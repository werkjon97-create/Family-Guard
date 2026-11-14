package com.example.child

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.data.local.AppInfoEntity
import com.example.core.data.local.FamilyGuardDao
import com.example.core.data.local.LimitEntity
import com.example.core.data.local.LocalPreferencesRepository
import com.example.core.data.local.SyncEntity
import com.example.core.data.remote.FamilyGuardRepository
import com.example.core.data.sync.UsageSyncWorker
import com.example.core.domain.AppErrorCode
import com.example.core.domain.AppLimit
import com.example.core.domain.AppResult
import com.example.core.domain.AppRole
import com.example.core.domain.ChildDevice
import com.example.core.domain.RedeemPairingCodeUseCase
import com.example.core.domain.SyncChildUsageUseCase
import com.example.core.utils.UsageAccessHelper
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ChildUiState(
    val isLinked: Boolean = false,
    val deviceId: String = "",
    val childName: String = "",
    val usageAccessGranted: Boolean = false,
    val remoteDevice: ChildDevice? = null,
    val localApps: List<AppInfoEntity> = emptyList(),
    val localLimits: List<LimitEntity> = emptyList(),
    val latestSync: SyncEntity? = null,
    val isSyncing: Boolean = false,
    val syncError: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class ChildViewModel(
    private val repository: FamilyGuardRepository,
    private val dao: FamilyGuardDao,
    private val prefs: LocalPreferencesRepository,
    private val currentUserId: String
) : ViewModel() {

    private val _pairingResult = MutableStateFlow<AppResult<ChildDevice>?>(null)
    val pairingResult: StateFlow<AppResult<ChildDevice>?> = _pairingResult.asStateFlow()

    private val _usageAccessGranted = MutableStateFlow(false)
    private val _isSyncing = MutableStateFlow(false)
    private val _syncError = MutableStateFlow<String?>(null)

    private val remoteDeviceFlow = prefs.state.flatMapLatest { local ->
        if (local.childDeviceId.isBlank() || local.linkedParentUid.isBlank()) {
            flowOf(null)
        } else {
            repository.observeDevice(local.childDeviceId)
                .catch { error ->
                    val fsError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsError == null && error is CancellationException) throw error
                    Log.w(TAG, "Child device stream error", fsError ?: error)
                    emit(null)
                }
        }
    }

    private val remoteLimitsFlow = prefs.state.flatMapLatest { local ->
        if (local.childDeviceId.isBlank() || local.linkedParentUid.isBlank()) {
            flowOf(emptyList<AppLimit>())
        } else {
            repository.observeDeviceLimits(local.childDeviceId, currentUserId, isParent = false)
                .catch { error ->
                    val fsError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsError == null && error is CancellationException) throw error
                    Log.w(TAG, "Child limits stream error", fsError ?: error)
                    emit(emptyList())
                }
        }
    }

    val uiState: StateFlow<ChildUiState> = combine(
        prefs.state,
        _usageAccessGranted,
        remoteDeviceFlow,
        dao.observeAppInfo(),
        dao.observeLimits(),
        dao.observeLatestSync(),
        _isSyncing,
        _syncError,
        remoteLimitsFlow
    ) { values ->
        val local = values[0] as com.example.core.data.local.LocalPrefsState
        val access = values[1] as Boolean
        val device = values[2] as ChildDevice?
        @Suppress("UNCHECKED_CAST")
        val apps = values[3] as List<AppInfoEntity>
        @Suppress("UNCHECKED_CAST")
        val cachedLimits = values[4] as List<LimitEntity>
        val latestSync = values[5] as SyncEntity?
        val syncing = values[6] as Boolean
        val syncErr = values[7] as String?
        @Suppress("UNCHECKED_CAST")
        val liveLimits = values[8] as List<AppLimit>

        val effectiveLimits = if (liveLimits.isNotEmpty()) {
            liveLimits.map {
                LimitEntity(
                    packageName = it.packageName,
                    appName = it.appName,
                    dailyLimitMinutes = it.dailyLimitMinutes.toInt(),
                    isEnabled = it.isEnabled,
                    updatedAtMs = System.currentTimeMillis()
                )
            }
        } else {
            cachedLimits
        }

        ChildUiState(
            isLinked = local.linkedParentUid.isNotBlank() && local.childDeviceId.isNotBlank(),
            deviceId = local.childDeviceId,
            childName = local.childName,
            usageAccessGranted = access,
            remoteDevice = device,
            localApps = apps,
            localLimits = effectiveLimits,
            latestSync = latestSync,
            isSyncing = syncing,
            syncError = syncErr
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = ChildUiState()
    )

    init {
        viewModelScope.launch {
            runCatching { repository.ensureUserProfile(AppRole.CHILD) }
                .onFailure { Log.w(TAG, "Child profile ensure failed", it) }
        }
    }

    fun refreshUsageAccess(context: Context) {
        _usageAccessGranted.value = UsageAccessHelper.hasUsageAccessPermission(context)
    }

    fun connectWithPairingCode(context: Context, rawCode: String, childName: String) {
        viewModelScope.launch {
            _pairingResult.value = AppResult.Loading
            val res = RedeemPairingCodeUseCase(repository, prefs)(context, rawCode, childName)
            _pairingResult.value = res
            if (res is AppResult.Success) {
                UsageSyncWorker.schedulePeriodicSync(context)
                syncNow(context)
            }
        }
    }

    fun clearPairingResult() {
        _pairingResult.value = null
    }

    fun syncNow(context: Context) {
        viewModelScope.launch {
            _isSyncing.value = true
            _syncError.value = null
            refreshUsageAccess(context)
            val res = SyncChildUsageUseCase(repository, dao, prefs)(context)
            _isSyncing.value = false
            if (res is AppResult.Error && res.code != AppErrorCode.USAGE_ACCESS_DISABLED) {
                _syncError.value = res.message
            }
        }
    }

    fun resetLocalLink(context: Context) {
        viewModelScope.launch {
            UsageSyncWorker.cancelSync(context)
            prefs.clearChildLink()
            dao.clearAppInfo()
            dao.clearLimits()
            dao.clearSyncLogs()
            _pairingResult.value = null
        }
    }

    private companion object {
        const val TAG = "ChildVM"
    }
}
