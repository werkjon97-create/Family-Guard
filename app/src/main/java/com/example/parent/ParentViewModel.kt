package com.example.parent

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.data.local.LocalPreferencesRepository
import com.example.core.data.remote.FamilyGuardRepository
import com.example.core.data.remote.GeneratedPairingResult
import com.example.core.domain.AppLimit
import com.example.core.domain.AppResult
import com.example.core.domain.AppRole
import com.example.core.domain.AppUsageEntry
import com.example.core.domain.ChildDevice
import com.example.core.domain.DailyUsage
import com.example.core.domain.DeviceAlert
import com.example.core.domain.GeneratePairingCodeUseCase
import com.example.core.domain.UserProfile
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ParentDashboardUiState(
    val profile: UserProfile? = null,
    val devices: List<ChildDevice> = emptyList(),
    val selectedDevice: ChildDevice? = null,
    val appUsage: List<AppUsageEntry> = emptyList(),
    val dailyUsage: List<DailyUsage> = emptyList(),
    val alerts: List<DeviceAlert> = emptyList(),
    val limits: List<AppLimit> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val privacyMode: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
class ParentViewModel(
    private val repository: FamilyGuardRepository,
    private val prefs: LocalPreferencesRepository,
    private val currentUserId: String
) : ViewModel() {

    private val _selectedDeviceId = MutableStateFlow<String?>(null)
    private val _pairingState = MutableStateFlow<AppResult<GeneratedPairingResult>?>(null)
    val pairingState: StateFlow<AppResult<GeneratedPairingResult>?> = _pairingState.asStateFlow()

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    private val profileFlow: StateFlow<UserProfile?> = repository.observeUserProfile(currentUserId)
        .catch { error ->
            val fsError = unwrapFirestoreError(error)
            if (fsError == null && error is CancellationException) throw error
            Log.w(TAG, "Profile stream error", fsError ?: error)
            emit(null)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    private val devicesStateFlow: StateFlow<AppResult<List<ChildDevice>>> = repository.observeParentDevices(currentUserId)
        .map<List<ChildDevice>, AppResult<List<ChildDevice>>> { list ->
            AppResult.Success(list)
        }
        .catch { error ->
            val fsError = unwrapFirestoreError(error)
            if (fsError == null && error is CancellationException) throw error
            val root = fsError ?: error
            Log.w(TAG, "Devices stream error", root)
            emit(AppResult.Error(root.localizedMessage ?: "Failed to load child devices"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), AppResult.Loading)

    private val activeDeviceIdFlow: StateFlow<String?> = combine(devicesStateFlow, _selectedDeviceId) { res, chosen ->
        val list = (res as? AppResult.Success)?.data.orEmpty()
        when {
            chosen != null && list.any { it.id == chosen } -> chosen
            else -> list.firstOrNull()?.id
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    private val appsFlow = activeDeviceIdFlow.flatMapLatest { deviceId ->
        if (deviceId == null) flowOf(emptyList())
        else repository.observeDeviceApps(deviceId, currentUserId, isParent = true)
            .catch { error ->
                val fsError = unwrapFirestoreError(error)
                if (fsError == null && error is CancellationException) throw error
                Log.w(TAG, "Apps stream error", fsError ?: error)
                emit(emptyList())
            }
    }

    private val dailyFlow = activeDeviceIdFlow.flatMapLatest { deviceId ->
        if (deviceId == null) flowOf(emptyList())
        else repository.observeDailyUsage(deviceId, currentUserId, isParent = true)
            .catch { error ->
                val fsError = unwrapFirestoreError(error)
                if (fsError == null && error is CancellationException) throw error
                Log.w(TAG, "Daily usage stream error", fsError ?: error)
                emit(emptyList())
            }
    }

    private val alertsFlow = activeDeviceIdFlow.flatMapLatest { deviceId ->
        if (deviceId == null) flowOf(emptyList())
        else repository.observeDeviceAlerts(deviceId, currentUserId, isParent = true)
            .catch { error ->
                val fsError = unwrapFirestoreError(error)
                if (fsError == null && error is CancellationException) throw error
                Log.w(TAG, "Alerts stream error", fsError ?: error)
                emit(emptyList())
            }
    }

    private val limitsFlow = activeDeviceIdFlow.flatMapLatest { deviceId ->
        if (deviceId == null) flowOf(emptyList())
        else repository.observeDeviceLimits(deviceId, currentUserId, isParent = true)
            .catch { error ->
                val fsError = unwrapFirestoreError(error)
                if (fsError == null && error is CancellationException) throw error
                Log.w(TAG, "Limits stream error", fsError ?: error)
                emit(emptyList())
            }
    }

    val uiState: StateFlow<ParentDashboardUiState> = combine(
        profileFlow,
        devicesStateFlow,
        activeDeviceIdFlow,
        appsFlow,
        dailyFlow,
        alertsFlow,
        limitsFlow,
        prefs.state
    ) { values ->
        val profile = values[0] as UserProfile?
        @Suppress("UNCHECKED_CAST")
        val devResult = values[1] as AppResult<List<ChildDevice>>
        val activeId = values[2] as String?
        @Suppress("UNCHECKED_CAST")
        val apps = values[3] as List<AppUsageEntry>
        @Suppress("UNCHECKED_CAST")
        val daily = values[4] as List<DailyUsage>
        @Suppress("UNCHECKED_CAST")
        val alerts = values[5] as List<DeviceAlert>
        @Suppress("UNCHECKED_CAST")
        val limits = values[6] as List<AppLimit>
        val localPrefs = values[7] as com.example.core.data.local.LocalPrefsState

        val devices = (devResult as? AppResult.Success)?.data.orEmpty()
        ParentDashboardUiState(
            profile = profile,
            devices = devices,
            selectedDevice = devices.firstOrNull { it.id == activeId },
            appUsage = apps,
            dailyUsage = daily,
            alerts = alerts,
            limits = limits,
            isLoading = devResult is AppResult.Loading,
            errorMessage = (devResult as? AppResult.Error)?.message,
            privacyMode = profile?.privacyMode ?: localPrefs.privacyMode
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = ParentDashboardUiState()
    )

    init {
        viewModelScope.launch {
            runCatching { repository.ensureUserProfile(AppRole.PARENT) }
                .onFailure { Log.w(TAG, "Profile ensure failed", it) }
        }
    }

    fun selectDevice(deviceId: String) {
        _selectedDeviceId.value = deviceId
    }

    fun generatePairingCode(childLabel: String) {
        viewModelScope.launch {
            _pairingState.value = AppResult.Loading
            _pairingState.value = GeneratePairingCodeUseCase(repository)(childLabel)
        }
    }

    fun clearPairingState() {
        _pairingState.value = null
    }

    fun toggleMonitoring(deviceId: String, active: Boolean) {
        viewModelScope.launch {
            runCatching { repository.setDeviceMonitoringActive(deviceId, active) }
                .onFailure { _actionMessage.value = it.localizedMessage }
        }
    }

    fun upsertLimit(
        deviceId: String,
        childUid: String,
        packageName: String,
        appName: String,
        minutes: Long,
        enabled: Boolean
    ) {
        viewModelScope.launch {
            runCatching {
                repository.upsertAppLimit(
                    deviceId = deviceId,
                    childUid = childUid,
                    packageName = packageName,
                    appName = appName,
                    dailyLimitMinutes = minutes,
                    isEnabled = enabled
                )
            }.onFailure { _actionMessage.value = it.localizedMessage }
        }
    }

    fun deleteLimit(deviceId: String, packageName: String) {
        viewModelScope.launch {
            runCatching { repository.deleteAppLimit(deviceId, packageName) }
                .onFailure { _actionMessage.value = it.localizedMessage }
        }
    }

    fun markAlertRead(deviceId: String, alertId: String) {
        viewModelScope.launch {
            runCatching { repository.markAlertRead(deviceId, alertId) }
                .onFailure { _actionMessage.value = it.localizedMessage }
        }
    }

    fun setPrivacyMode(enabled: Boolean) {
        viewModelScope.launch {
            prefs.setPrivacyMode(enabled)
            runCatching { repository.updatePrivacyMode(enabled) }
                .onFailure { _actionMessage.value = it.localizedMessage }
        }
    }

    fun deleteChildDeviceData(deviceId: String) {
        viewModelScope.launch {
            runCatching { repository.deleteChildDeviceData(deviceId) }
                .onFailure { _actionMessage.value = it.localizedMessage }
        }
    }

    fun deleteAccountAndData(onComplete: () -> Unit) {
        viewModelScope.launch {
            runCatching { repository.deleteAccountData() }
                .onSuccess { onComplete() }
                .onFailure { _actionMessage.value = it.localizedMessage }
        }
    }

    fun clearActionMessage() {
        _actionMessage.value = null
    }

    private fun unwrapFirestoreError(error: Throwable): FirebaseFirestoreException? {
        return generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
    }

    private companion object {
        const val TAG = "ParentVM"
    }
}
