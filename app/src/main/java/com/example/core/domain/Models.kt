package com.example.core.domain

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class AppRole(val value: String) {
    PARENT("parent"),
    CHILD("child");

    companion object {
        fun fromValue(value: String?): AppRole =
            entries.firstOrNull { it.value == value } ?: PARENT
    }
}

enum class AlertType(val value: String) {
    LIMIT_EXCEEDED("limit_exceeded"),
    USAGE_ACCESS_REVOKED("usage_access_revoked"),
    SYNC_FAILED("sync_failed"),
    OFFLINE_TOO_LONG("offline_too_long"),
    SETTINGS_CHANGED("settings_changed");

    companion object {
        fun fromValue(value: String?): AlertType =
            entries.firstOrNull { it.value == value } ?: SETTINGS_CHANGED
    }
}

@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val email: String = "",
    val privacyMode: Boolean = false,
    val role: String = AppRole.PARENT.value,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class PairingCodeRecord(
    @DocumentId val id: String = "",
    val parentUid: String = "",
    val childLabel: String = "",
    val used: Boolean = false,
    val usedByDeviceId: String = "",
    val expiresAtMs: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class ChildDevice(
    @DocumentId val id: String = "",
    val parentUid: String = "",
    val childUid: String = "",
    val childName: String = "",
    val deviceName: String = "",
    val androidVersion: String = "",
    val isOnline: Boolean = false,
    val monitoringActive: Boolean = true,
    val usageAccessGranted: Boolean = false,
    val screenTimeMs: Long = 0L,
    val appCount: Long = 0L,
    val lastSeenMs: Long = 0L,
    val syncStatus: String = "pending",
    val fcmToken: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class DailyUsage(
    @DocumentId val id: String = "",
    val parentUid: String = "",
    val childUid: String = "",
    val date: String = "",
    val totalScreenTimeMs: Long = 0L,
    val appCount: Long = 0L,
    val topAppName: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class AppUsageEntry(
    @DocumentId val id: String = "",
    val parentUid: String = "",
    val childUid: String = "",
    val packageName: String = "",
    val appName: String = "",
    val usageTimeMs: Long = 0L,
    val launchCount: Long = 0L,
    val lastUsedMs: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class DeviceAlert(
    @DocumentId val id: String = "",
    val parentUid: String = "",
    val childUid: String = "",
    val deviceId: String = "",
    val type: String = AlertType.SETTINGS_CHANGED.value,
    val title: String = "",
    val message: String = "",
    val isRead: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class AppLimit(
    @DocumentId val id: String = "",
    val parentUid: String = "",
    val childUid: String = "",
    val packageName: String = "",
    val appName: String = "",
    val dailyLimitMinutes: Long = 30L,
    val isEnabled: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)
