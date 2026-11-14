package com.example.core.data.sync

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.R
import com.example.core.data.local.FamilyGuardDatabase
import com.example.core.data.local.LocalPreferencesRepository
import com.example.core.data.remote.FamilyGuardRepository
import com.example.core.domain.AppErrorCode
import com.example.core.domain.AppResult
import com.example.core.domain.EvaluateAppLimitsUseCase
import com.example.core.domain.SyncChildUsageUseCase
import com.example.core.utils.UsageStatsHelper
import java.util.concurrent.TimeUnit

class UsageSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val db = FamilyGuardDatabase.getInstance(applicationContext)
        val dao = db.familyGuardDao()
        val prefs = LocalPreferencesRepository(applicationContext)
        val repository = FamilyGuardRepository(applicationContext)

        val result = SyncChildUsageUseCase(repository, dao, prefs)(applicationContext)

        // Post transparent local warning if an app limit was exceeded (never force-closes apps)
        if (result is AppResult.Success) {
            val summary = UsageStatsHelper.collectTodayUsage(applicationContext)
            val limits = dao.getEnabledLimits().map {
                com.example.core.domain.AppLimit(
                    packageName = it.packageName,
                    appName = it.appName,
                    dailyLimitMinutes = it.dailyLimitMinutes.toLong(),
                    isEnabled = it.isEnabled
                )
            }
            val exceeded = EvaluateAppLimitsUseCase().findExceededLimits(
                usageApps = summary.apps.map { it.packageName to it.usageTimeMs },
                limits = limits
            )
            if (exceeded.isNotEmpty()) {
                postLimitWarningNotification(
                    applicationContext,
                    exceeded.joinToString(", ") { it.appName }
                )
            }
        }

        return when (result) {
            is AppResult.Success -> Result.success()
            is AppResult.Error -> when (result.code) {
                AppErrorCode.NO_INTERNET,
                AppErrorCode.FIREBASE_UNAVAILABLE -> Result.retry()
                else -> Result.success()
            }
            else -> Result.success()
        }
    }

    private fun postLimitWarningNotification(context: Context, appNames: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ALERTS,
                context.getString(R.string.notification_channel_alerts),
                NotificationManager.IMPORTANCE_DEFAULT
            )
            manager.createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(context.getString(R.string.limit_warning_title))
            .setContentText(context.getString(R.string.limit_warning_body, appNames))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        manager.notify(LIMIT_NOTIFICATION_ID, notification)
    }

    companion object {
        private const val PERIODIC_WORK_NAME = "family_guard_usage_sync"
        private const val ONE_TIME_WORK_NAME = "family_guard_usage_sync_now"
        private const val CHANNEL_ALERTS = "family_guard_alerts"
        private const val LIMIT_NOTIFICATION_ID = 4102

        fun schedulePeriodicSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = PeriodicWorkRequestBuilder<UsageSyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun triggerImmediateSync(context: Context) {
            val request = OneTimeWorkRequestBuilder<UsageSyncWorker>().build()
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                ONE_TIME_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }

        fun cancelSync(context: Context) {
            WorkManager.getInstance(context.applicationContext).cancelUniqueWork(PERIODIC_WORK_NAME)
        }
    }
}
