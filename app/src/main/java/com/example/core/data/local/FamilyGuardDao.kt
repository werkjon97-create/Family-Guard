package com.example.core.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FamilyGuardDao {

    // --- UsageEntity ---
    @Query("SELECT * FROM usage WHERE date = :date ORDER BY usageTimeMs DESC")
    fun observeUsageForDate(date: String): Flow<List<UsageEntity>>

    @Query("DELETE FROM usage WHERE date = :date")
    suspend fun clearUsageForDate(date: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUsageEntries(entries: List<UsageEntity>)

    // --- AppInfoEntity ---
    @Query("SELECT * FROM app_info ORDER BY usageTimeMs DESC")
    fun observeAppInfo(): Flow<List<AppInfoEntity>>

    @Query("SELECT * FROM app_info ORDER BY usageTimeMs DESC")
    suspend fun getAppInfoSnapshot(): List<AppInfoEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAppInfo(apps: List<AppInfoEntity>)

    @Query("DELETE FROM app_info")
    suspend fun clearAppInfo()

    // --- ChildDeviceEntity ---
    @Query("SELECT * FROM child_devices ORDER BY lastSeenMs DESC")
    fun observeChildDevices(): Flow<List<ChildDeviceEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertChildDevices(devices: List<ChildDeviceEntity>)

    @Query("DELETE FROM child_devices WHERE deviceId = :deviceId")
    suspend fun deleteChildDevice(deviceId: String)

    @Query("DELETE FROM child_devices")
    suspend fun clearChildDevices()

    // --- SyncEntity ---
    @Query("SELECT * FROM sync_logs ORDER BY timestamp DESC LIMIT 20")
    fun observeRecentSyncLogs(): Flow<List<SyncEntity>>

    @Query("SELECT * FROM sync_logs ORDER BY timestamp DESC LIMIT 1")
    fun observeLatestSync(): Flow<SyncEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSyncLog(sync: SyncEntity)

    @Query("DELETE FROM sync_logs")
    suspend fun clearSyncLogs()

    // --- LimitEntity ---
    @Query("SELECT * FROM limits ORDER BY dailyLimitMinutes ASC")
    fun observeLimits(): Flow<List<LimitEntity>>

    @Query("SELECT * FROM limits WHERE isEnabled = 1")
    suspend fun getEnabledLimits(): List<LimitEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLimits(limits: List<LimitEntity>)

    @Query("DELETE FROM limits WHERE packageName = :packageName")
    suspend fun deleteLimit(packageName: String)

    @Query("DELETE FROM limits")
    suspend fun clearLimits()
}
