package com.example.core.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "usage")
data class UsageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val packageName: String,
    val appName: String,
    val usageTimeMs: Long,
    val launchCount: Int = 0,
    val date: String,
    val timestamp: Long
)

@Entity(tableName = "child_devices")
data class ChildDeviceEntity(
    @PrimaryKey
    val deviceId: String,
    val parentUid: String,
    val childUid: String,
    val childName: String,
    val deviceName: String,
    val androidVersion: String,
    val isOnline: Boolean,
    val monitoringActive: Boolean,
    val usageAccessGranted: Boolean,
    val screenTimeMs: Long,
    val appCount: Int,
    val lastSeenMs: Long,
    val syncStatus: String
)

@Entity(tableName = "sync_logs")
data class SyncEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val deviceId: String,
    val status: String, // "synced", "pending", "failed"
    val syncedAppCount: Int,
    val totalScreenTimeMs: Long,
    val message: String,
    val timestamp: Long
)

@Entity(tableName = "app_info")
data class AppInfoEntity(
    @PrimaryKey
    val packageName: String,
    val appName: String,
    val usageTimeMs: Long,
    val launchCount: Int,
    val lastUsedMs: Long,
    val date: String
)

@Entity(tableName = "limits")
data class LimitEntity(
    @PrimaryKey
    val packageName: String,
    val appName: String,
    val dailyLimitMinutes: Int,
    val isEnabled: Boolean,
    val updatedAtMs: Long
)
