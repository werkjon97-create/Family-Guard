package com.example.core.data.remote

import android.content.Context
import com.example.R
import com.example.core.domain.AlertType
import com.example.core.domain.AppErrorCode
import com.example.core.domain.AppLimit
import com.example.core.domain.AppRole
import com.example.core.domain.AppUsageEntry
import com.example.core.domain.ChildDevice
import com.example.core.domain.DailyUsage
import com.example.core.domain.DeviceAlert
import com.example.core.domain.PairingCodeRecord
import com.example.core.domain.UserProfile
import com.example.core.security.OperationType
import com.example.core.security.PairingSecurity
import com.example.core.security.handleFirestoreError
import com.example.core.utils.CollectedUsageSummary
import com.example.core.utils.Formatters
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class PairingException(
    val errorCode: AppErrorCode,
    override val message: String
) : Exception(message)

data class GeneratedPairingResult(
    val rawCode: String,
    val codeHash: String,
    val expiresAtMs: Long
)

class FamilyGuardRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        db = FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        auth = Firebase.auth
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    // --- User Profile ---

    suspend fun ensureUserProfile(role: AppRole): UserProfile {
        val user = auth.currentUser
            ?: throw IllegalStateException("User must be signed in before accessing profile.")
        val docRef = db.collection("users").document(user.uid)
        try {
            val snap = docRef.get().await()
            if (snap.exists()) {
                val existing = snap.toObject(
                    UserProfile::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
                if (existing != null) return existing
            }
            val displayName = user.displayName?.take(80)?.ifBlank { null }
                ?: user.email?.substringBefore('@')?.take(80)
                ?: "Parent"
            val payload = buildMap<String, Any> {
                put("userId", user.uid)
                put("displayName", displayName)
                user.email?.takeIf { it.isNotBlank() }?.let { put("email", it.take(160)) }
                put("privacyMode", false)
                put("role", role.value)
                put("createdAt", FieldValue.serverTimestamp())
                put("updatedAt", FieldValue.serverTimestamp())
            }
            docRef.set(payload).await()
            return UserProfile(
                id = user.uid,
                userId = user.uid,
                displayName = displayName,
                email = user.email.orEmpty(),
                privacyMode = false,
                role = role.value
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    fun observeUserProfile(userId: String): Flow<UserProfile?> = flow {
        val docRef = db.collection("users").document(userId)
        emitAll(
            docRef.snapshots()
                .map { snap ->
                    if (snap.exists()) {
                        snap.toObject(
                            UserProfile::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    } else {
                        null
                    }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == userId) {
                        handleFirestoreError(firestoreError ?: error, OperationType.GET, docRef.path)
                    }
                    throw error
                }
        )
    }

    suspend fun updatePrivacyMode(enabled: Boolean) {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
        try {
            docRef.update(
                mapOf(
                    "privacyMode" to enabled,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun updateProfileRole(role: AppRole) {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
        try {
            val snap = docRef.get().await()
            if (!snap.exists()) {
                ensureUserProfile(role)
            } else {
                docRef.update(
                    mapOf(
                        "role" to role.value,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    // --- Secure Pairing System ---

    suspend fun createPairingCode(childLabel: String): GeneratedPairingResult {
        val uid = requireUserId()
        val rawCode = PairingSecurity.generateRawCode()
        val codeHash = PairingSecurity.hashCode(rawCode)
        val expiresAtMs = PairingSecurity.computeExpirationMs()
        val docRef = db.collection("pairing_codes").document(codeHash)
        val sanitizedLabel = childLabel.trim().take(60)
        val payload = buildMap<String, Any> {
            put("parentUid", uid)
            if (sanitizedLabel.isNotBlank()) {
                put("childLabel", sanitizedLabel)
            }
            put("used", false)
            put("expiresAtMs", expiresAtMs)
            put("createdAt", FieldValue.serverTimestamp())
            put("updatedAt", FieldValue.serverTimestamp())
        }
        try {
            docRef.set(payload).await()
            return GeneratedPairingResult(
                rawCode = rawCode,
                codeHash = codeHash,
                expiresAtMs = expiresAtMs
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun redeemPairingCode(
        rawCode: String,
        deviceId: String,
        fallbackChildName: String,
        usageAccessGranted: Boolean,
        fcmToken: String = ""
    ): ChildDevice {
        val childUid = requireUserId()
        if (!PairingSecurity.isValidFormat(rawCode)) {
            throw PairingException(AppErrorCode.INVALID_PAIRING_CODE, "Invalid pairing code format.")
        }
        val codeHash = PairingSecurity.hashCode(rawCode)
        val codeRef = db.collection("pairing_codes").document(codeHash)

        try {
            val snap = try {
                codeRef.get().await()
            } catch (e: FirebaseFirestoreException) {
                if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                    throw PairingException(AppErrorCode.INVALID_PAIRING_CODE, "Pairing code already used or invalid.")
                }
                throw e
            }
            if (!snap.exists()) {
                throw PairingException(AppErrorCode.INVALID_PAIRING_CODE, "Pairing code not found.")
            }
            val record = snap.toObject(
                PairingCodeRecord::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ) ?: throw PairingException(AppErrorCode.INVALID_PAIRING_CODE, "Invalid pairing record.")

            if (record.used) {
                throw PairingException(AppErrorCode.INVALID_PAIRING_CODE, "Pairing code already used.")
            }
            if (record.expiresAtMs <= System.currentTimeMillis()) {
                throw PairingException(AppErrorCode.EXPIRED_PAIRING_CODE, "Pairing code expired.")
            }

            val resolvedChildName = record.childLabel.ifBlank { fallbackChildName.ifBlank { "Child Device" } }.take(60)
            val deviceName = Formatters.deviceDisplayName().take(80).ifBlank { "Android Device" }
            val androidVersion = Formatters.androidVersionLabel().take(32)
            val nowMs = System.currentTimeMillis()
            val deviceRef = db.collection("devices").document(deviceId)

            val devicePayload = buildMap<String, Any> {
                put("parentUid", record.parentUid)
                put("childUid", childUid)
                put("childName", resolvedChildName)
                put("deviceName", deviceName)
                put("androidVersion", androidVersion)
                put("isOnline", true)
                put("monitoringActive", true)
                put("usageAccessGranted", usageAccessGranted)
                put("screenTimeMs", 0L)
                put("appCount", 0L)
                put("lastSeenMs", nowMs)
                put("syncStatus", "synced")
                if (fcmToken.isNotBlank()) {
                    put("fcmToken", fcmToken.take(512))
                }
                put("createdAt", FieldValue.serverTimestamp())
                put("updatedAt", FieldValue.serverTimestamp())
            }

            val batch = db.batch()
            batch.update(
                codeRef,
                mapOf(
                    "used" to true,
                    "usedByDeviceId" to deviceId,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.set(deviceRef, devicePayload)
            batch.commit().await()

            return ChildDevice(
                id = deviceId,
                parentUid = record.parentUid,
                childUid = childUid,
                childName = resolvedChildName,
                deviceName = deviceName,
                androidVersion = androidVersion,
                isOnline = true,
                monitoringActive = true,
                usageAccessGranted = usageAccessGranted,
                screenTimeMs = 0L,
                appCount = 0L,
                lastSeenMs = nowMs,
                syncStatus = "synced",
                fcmToken = fcmToken
            )
        } catch (e: PairingException) {
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, codeRef.path)
            throw e
        }
    }

    // --- Parent Devices ---

    fun observeParentDevices(parentUid: String): Flow<List<ChildDevice>> = flow {
        val query = db.collection("devices").whereEqualTo("parentUid", parentUid)
        emitAll(
            query.snapshots()
                .map { snap ->
                    snap.toObjects(
                        ChildDevice::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.lastSeenMs }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == parentUid) {
                        handleFirestoreError(firestoreError ?: error, OperationType.LIST, "devices")
                    }
                    throw error
                }
        )
    }

    fun observeDevice(deviceId: String): Flow<ChildDevice?> = flow {
        val docRef = db.collection("devices").document(deviceId)
        emitAll(
            docRef.snapshots()
                .map { snap ->
                    if (snap.exists()) {
                        snap.toObject(
                            ChildDevice::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    } else {
                        null
                    }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    if (auth.currentUser != null) {
                        handleFirestoreError(firestoreError ?: error, OperationType.GET, docRef.path)
                    }
                    throw error
                }
        )
    }

    suspend fun setDeviceMonitoringActive(deviceId: String, active: Boolean) {
        requireUserId()
        val docRef = db.collection("devices").document(deviceId)
        try {
            docRef.update(
                mapOf(
                    "monitoringActive" to active,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun updateDeviceChildName(deviceId: String, childName: String) {
        requireUserId()
        val docRef = db.collection("devices").document(deviceId)
        try {
            docRef.update(
                mapOf(
                    "childName" to childName.trim().take(60),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    // --- Usage & Apps ---

    suspend fun syncChildUsageToFirestore(
        deviceId: String,
        parentUid: String,
        usageAccessGranted: Boolean,
        isOnline: Boolean,
        summary: CollectedUsageSummary,
        fcmToken: String = ""
    ) {
        val childUid = requireUserId()
        val nowMs = System.currentTimeMillis()
        val deviceRef = db.collection("devices").document(deviceId)
        val dailyRef = deviceRef.collection("usage").document(summary.date)
        val topAppName = summary.apps.firstOrNull()?.appName.orEmpty().take(80)

        try {
            val deviceUpdates = buildMap<String, Any> {
                put("deviceName", Formatters.deviceDisplayName().take(80))
                put("androidVersion", Formatters.androidVersionLabel().take(32))
                put("isOnline", isOnline)
                put("usageAccessGranted", usageAccessGranted)
                put("screenTimeMs", summary.totalScreenTimeMs)
                put("appCount", summary.apps.size.toLong())
                put("lastSeenMs", nowMs)
                put("syncStatus", "synced")
                if (fcmToken.isNotBlank()) {
                    put("fcmToken", fcmToken.take(512))
                }
                put("updatedAt", FieldValue.serverTimestamp())
            }
            deviceRef.update(deviceUpdates).await()

            val existingDaily = dailyRef.get().await()
            if (existingDaily.exists()) {
                val dailyUpdates = buildMap<String, Any> {
                    put("totalScreenTimeMs", summary.totalScreenTimeMs)
                    put("appCount", summary.apps.size.toLong())
                    if (topAppName.isNotBlank()) put("topAppName", topAppName)
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                dailyRef.update(dailyUpdates).await()
            } else {
                val dailyCreate = buildMap<String, Any> {
                    put("parentUid", parentUid)
                    put("childUid", childUid)
                    put("date", summary.date)
                    put("totalScreenTimeMs", summary.totalScreenTimeMs)
                    put("appCount", summary.apps.size.toLong())
                    if (topAppName.isNotBlank()) put("topAppName", topAppName)
                    put("createdAt", FieldValue.serverTimestamp())
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                dailyRef.set(dailyCreate).await()
            }

            // Upsert top 30 used apps cleanly
            for (app in summary.apps.take(30)) {
                val pkgId = app.packageName.take(128)
                val appRef = deviceRef.collection("apps").document(pkgId)
                val appSnap = appRef.get().await()
                if (appSnap.exists()) {
                    appRef.update(
                        mapOf(
                            "appName" to app.appName.take(80),
                            "usageTimeMs" to app.usageTimeMs,
                            "launchCount" to app.launchCount.toLong(),
                            "lastUsedMs" to app.lastUsedMs,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                } else {
                    appRef.set(
                        mapOf(
                            "parentUid" to parentUid,
                            "childUid" to childUid,
                            "packageName" to pkgId,
                            "appName" to app.appName.take(80),
                            "usageTimeMs" to app.usageTimeMs,
                            "launchCount" to app.launchCount.toLong(),
                            "lastUsedMs" to app.lastUsedMs,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, deviceRef.path)
            throw e
        }
    }

    fun observeDeviceApps(deviceId: String, uid: String, isParent: Boolean): Flow<List<AppUsageEntry>> = flow {
        val field = if (isParent) "parentUid" else "childUid"
        val query = db.collection("devices").document(deviceId).collection("apps")
            .whereEqualTo(field, uid)
        emitAll(
            query.snapshots()
                .map { snap ->
                    snap.toObjects(
                        AppUsageEntry::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.usageTimeMs }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(firestoreError ?: error, OperationType.LIST, "devices/$deviceId/apps")
                    }
                    throw error
                }
        )
    }

    fun observeDailyUsage(deviceId: String, uid: String, isParent: Boolean): Flow<List<DailyUsage>> = flow {
        val field = if (isParent) "parentUid" else "childUid"
        val query = db.collection("devices").document(deviceId).collection("usage")
            .whereEqualTo(field, uid)
        emitAll(
            query.snapshots()
                .map { snap ->
                    snap.toObjects(
                        DailyUsage::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.date }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(firestoreError ?: error, OperationType.LIST, "devices/$deviceId/usage")
                    }
                    throw error
                }
        )
    }

    // --- Alerts ---

    fun observeDeviceAlerts(deviceId: String, uid: String, isParent: Boolean): Flow<List<DeviceAlert>> = flow {
        val field = if (isParent) "parentUid" else "childUid"
        val query = db.collection("devices").document(deviceId).collection("alerts")
            .whereEqualTo(field, uid)
        emitAll(
            query.snapshots()
                .map { snap ->
                    snap.toObjects(
                        DeviceAlert::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(firestoreError ?: error, OperationType.LIST, "devices/$deviceId/alerts")
                    }
                    throw error
                }
        )
    }

    suspend fun reportAlert(
        deviceId: String,
        parentUid: String,
        childUid: String,
        type: AlertType,
        title: String,
        message: String,
        alertId: String = "alrt_" + UUID.randomUUID().toString().replace("-", "").take(16)
    ) {
        requireUserId()
        val alertRef = db.collection("devices").document(deviceId).collection("alerts").document(alertId)
        try {
            val snap = alertRef.get().await()
            if (snap.exists()) return
            alertRef.set(
                mapOf(
                    "parentUid" to parentUid,
                    "childUid" to childUid,
                    "deviceId" to deviceId,
                    "type" to type.value,
                    "title" to title.take(120),
                    "message" to message.take(300),
                    "isRead" to false,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, alertRef.path)
            throw e
        }
    }

    suspend fun markAlertRead(deviceId: String, alertId: String) {
        requireUserId()
        val alertRef = db.collection("devices").document(deviceId).collection("alerts").document(alertId)
        try {
            alertRef.update(
                mapOf(
                    "isRead" to true,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, alertRef.path)
            throw e
        }
    }

    // --- App Limits ---

    fun observeDeviceLimits(deviceId: String, uid: String, isParent: Boolean): Flow<List<AppLimit>> = flow {
        val field = if (isParent) "parentUid" else "childUid"
        val query = db.collection("devices").document(deviceId).collection("limits")
            .whereEqualTo(field, uid)
        emitAll(
            query.snapshots()
                .map { snap ->
                    snap.toObjects(
                        AppLimit::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedBy { it.appName.lowercase() }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(firestoreError ?: error, OperationType.LIST, "devices/$deviceId/limits")
                    }
                    throw error
                }
        )
    }

    suspend fun upsertAppLimit(
        deviceId: String,
        childUid: String,
        packageName: String,
        appName: String,
        dailyLimitMinutes: Long,
        isEnabled: Boolean
    ) {
        val parentUid = requireUserId()
        val pkgId = packageName.take(128)
        val limitRef = db.collection("devices").document(deviceId).collection("limits").document(pkgId)
        try {
            val snap = limitRef.get().await()
            if (snap.exists()) {
                limitRef.update(
                    mapOf(
                        "appName" to appName.take(80),
                        "dailyLimitMinutes" to dailyLimitMinutes.coerceIn(1L, 1440L),
                        "isEnabled" to isEnabled,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                limitRef.set(
                    mapOf(
                        "parentUid" to parentUid,
                        "childUid" to childUid,
                        "packageName" to pkgId,
                        "appName" to appName.take(80),
                        "dailyLimitMinutes" to dailyLimitMinutes.coerceIn(1L, 1440L),
                        "isEnabled" to isEnabled,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, limitRef.path)
            throw e
        }
    }

    suspend fun deleteAppLimit(deviceId: String, packageName: String) {
        requireUserId()
        val limitRef = db.collection("devices").document(deviceId).collection("limits").document(packageName)
        try {
            limitRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, limitRef.path)
            throw e
        }
    }

    // --- Privacy & Data Deletion ---

    suspend fun deleteChildDeviceData(deviceId: String) {
        val parentUid = requireUserId()
        val deviceRef = db.collection("devices").document(deviceId)
        try {
            val subcollections = listOf("usage", "apps", "alerts", "limits")
            for (sub in subcollections) {
                val snaps = deviceRef.collection(sub).whereEqualTo("parentUid", parentUid).get().await()
                for (doc in snaps.documents) {
                    doc.reference.delete().await()
                }
            }
            deviceRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, deviceRef.path)
            throw e
        }
    }

    suspend fun deleteAccountData() {
        val uid = requireUserId()
        try {
            val devices = db.collection("devices").whereEqualTo("parentUid", uid).get().await()
            for (deviceDoc in devices.documents) {
                deleteChildDeviceData(deviceDoc.id)
            }
            db.collection("users").document(uid).delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "users/$uid")
            throw e
        }
    }
}
