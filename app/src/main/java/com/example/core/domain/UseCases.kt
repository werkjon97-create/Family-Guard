package com.example.core.domain

import android.content.Context
import com.example.core.data.local.AppInfoEntity
import com.example.core.data.local.FamilyGuardDao
import com.example.core.data.local.LimitEntity
import com.example.core.data.local.LocalPreferencesRepository
import com.example.core.data.local.SyncEntity
import com.example.core.data.local.UsageEntity
import com.example.core.data.remote.FamilyGuardRepository
import com.example.core.data.remote.GeneratedPairingResult
import com.example.core.data.remote.PairingException
import com.example.core.utils.ConnectivityObserver
import com.example.core.utils.UsageAccessHelper
import com.example.core.utils.UsageStatsHelper
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.flow.first

class GeneratePairingCodeUseCase(
    private val repository: FamilyGuardRepository
) {
    suspend operator fun invoke(childLabel: String): AppResult<GeneratedPairingResult> {
        return try {
            AppResult.Success(repository.createPairingCode(childLabel))
        } catch (e: Exception) {
            AppResult.Error(e.localizedMessage ?: "Failed to generate pairing code")
        }
    }
}

class RedeemPairingCodeUseCase(
    private val repository: FamilyGuardRepository,
    private val prefs: LocalPreferencesRepository
) {
    suspend operator fun invoke(
        context: Context,
        rawCode: String,
        childName: String
    ): AppResult<ChildDevice> {
        if (!ConnectivityObserver.isOnline(context)) {
            return AppResult.Error("No internet connection.", AppErrorCode.NO_INTERNET)
        }
        return try {
            val deviceId = prefs.getOrCreateDeviceId()
            val usageGranted = UsageAccessHelper.hasUsageAccessPermission(context)
            val fcmToken = prefs.state.first().fcmToken
            val linked = repository.redeemPairingCode(
                rawCode = rawCode,
                deviceId = deviceId,
                fallbackChildName = childName,
                usageAccessGranted = usageGranted,
                fcmToken = fcmToken
            )
            prefs.saveChildLink(
                deviceId = linked.id,
                parentUid = linked.parentUid,
                childName = linked.childName
            )
            AppResult.Success(linked)
        } catch (e: PairingException) {
            AppResult.Error(e.message, e.errorCode)
        } catch (e: Exception) {
            AppResult.Error(e.localizedMessage ?: "Pairing failed.", AppErrorCode.UNKNOWN)
        }
    }
}

class SyncChildUsageUseCase(
    private val repository: FamilyGuardRepository,
    private val dao: FamilyGuardDao,
    private val prefs: LocalPreferencesRepository
) {
    suspend operator fun invoke(context: Context): AppResult<Unit> {
        val currentPrefs = prefs.state.first()
        val deviceId = currentPrefs.childDeviceId
        val parentUid = currentPrefs.linkedParentUid
        val childUid = Firebase.auth.currentUser?.uid
        if (deviceId.isBlank() || parentUid.isBlank() || childUid == null) {
            return AppResult.Error("Device is not linked.", AppErrorCode.DEVICE_UNLINKED)
        }

        val usageGranted = UsageAccessHelper.hasUsageAccessPermission(context)
        val summary = UsageStatsHelper.collectTodayUsage(context)
        val nowMs = System.currentTimeMillis()

        // 1. Always update local Room cache first
        dao.clearUsageForDate(summary.date)
        dao.insertUsageEntries(
            summary.apps.map {
                UsageEntity(
                    packageName = it.packageName,
                    appName = it.appName,
                    usageTimeMs = it.usageTimeMs,
                    launchCount = it.launchCount,
                    date = summary.date,
                    timestamp = nowMs
                )
            }
        )
        dao.clearAppInfo()
        dao.upsertAppInfo(
            summary.apps.map {
                AppInfoEntity(
                    packageName = it.packageName,
                    appName = it.appName,
                    usageTimeMs = it.usageTimeMs,
                    launchCount = it.launchCount,
                    lastUsedMs = it.lastUsedMs,
                    date = summary.date
                )
            }
        )

        if (!usageGranted) {
            dao.insertSyncLog(
                SyncEntity(
                    deviceId = deviceId,
                    status = "failed",
                    syncedAppCount = 0,
                    totalScreenTimeMs = 0L,
                    message = "Usage access permission required",
                    timestamp = nowMs
                )
            )
            if (ConnectivityObserver.isOnline(context)) {
                runCatching {
                    repository.reportAlert(
                        deviceId = deviceId,
                        parentUid = parentUid,
                        childUid = childUid,
                        type = AlertType.USAGE_ACCESS_REVOKED,
                        title = "Usage Access Disabled",
                        message = "${currentPrefs.childName.ifBlank { "Child device" }} needs Usage Access enabled.",
                        alertId = "usage_access_${summary.date}"
                    )
                }
            }
            return AppResult.Error("Usage Access required", AppErrorCode.USAGE_ACCESS_DISABLED)
        }

        if (!ConnectivityObserver.isOnline(context)) {
            dao.insertSyncLog(
                SyncEntity(
                    deviceId = deviceId,
                    status = "pending",
                    syncedAppCount = summary.apps.size,
                    totalScreenTimeMs = summary.totalScreenTimeMs,
                    message = "Offline - saved locally in Room",
                    timestamp = nowMs
                )
            )
            return AppResult.Error("No internet connection.", AppErrorCode.NO_INTERNET)
        }

        return try {
            repository.syncChildUsageToFirestore(
                deviceId = deviceId,
                parentUid = parentUid,
                usageAccessGranted = true,
                isOnline = true,
                summary = summary,
                fcmToken = currentPrefs.fcmToken
            )

            // Fetch latest parent-configured limits and sync to Room
            val remoteLimits = repository.observeDeviceLimits(deviceId, childUid, isParent = false).first()
            dao.clearLimits()
            dao.upsertLimits(
                remoteLimits.map {
                    LimitEntity(
                        packageName = it.packageName,
                        appName = it.appName,
                        dailyLimitMinutes = it.dailyLimitMinutes.toInt(),
                        isEnabled = it.isEnabled,
                        updatedAtMs = nowMs
                    )
                }
            )

            // Evaluate daily limits (transparent warning + alert, never force-close apps)
            val exceededApps = EvaluateAppLimitsUseCase().findExceededLimits(
                usageApps = summary.apps.map { it.packageName to it.usageTimeMs },
                limits = remoteLimits
            )
            for (limit in exceededApps) {
                val safePkgKey = limit.packageName.replace(".", "_")
                repository.reportAlert(
                    deviceId = deviceId,
                    parentUid = parentUid,
                    childUid = childUid,
                    type = AlertType.LIMIT_EXCEEDED,
                    title = "${limit.appName} limit reached",
                    message = "Daily ${limit.dailyLimitMinutes} min limit exceeded on ${currentPrefs.childName.ifBlank { "Child Device" }}.",
                    alertId = "lim_${summary.date}_$safePkgKey".take(120)
                )
            }

            prefs.setLastSyncMs(nowMs)
            dao.insertSyncLog(
                SyncEntity(
                    deviceId = deviceId,
                    status = "synced",
                    syncedAppCount = summary.apps.size,
                    totalScreenTimeMs = summary.totalScreenTimeMs,
                    message = "Synced ${summary.apps.size} apps to Firestore",
                    timestamp = nowMs
                )
            )
            AppResult.Success(Unit)
        } catch (e: Exception) {
            dao.insertSyncLog(
                SyncEntity(
                    deviceId = deviceId,
                    status = "failed",
                    syncedAppCount = summary.apps.size,
                    totalScreenTimeMs = summary.totalScreenTimeMs,
                    message = e.localizedMessage ?: "Sync error",
                    timestamp = nowMs
                )
            )
            AppResult.Error(e.localizedMessage ?: "Sync failed", AppErrorCode.FIREBASE_UNAVAILABLE)
        }
    }
}

class EvaluateAppLimitsUseCase {
    fun findExceededLimits(
        usageApps: List<Pair<String, Long>>,
        limits: List<AppLimit>
    ): List<AppLimit> {
        val usageMap = usageApps.toMap()
        return limits.filter { limit ->
            if (!limit.isEnabled) return@filter false
            val usedMs = usageMap[limit.packageName] ?: 0L
            usedMs >= limit.dailyLimitMinutes * 60_000L
        }
    }
}
