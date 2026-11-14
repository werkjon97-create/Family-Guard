package com.example.core.utils

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class CollectedAppUsage(
    val packageName: String,
    val appName: String,
    val usageTimeMs: Long,
    val launchCount: Int,
    val lastUsedMs: Long
)

data class CollectedUsageSummary(
    val date: String,
    val totalScreenTimeMs: Long,
    val apps: List<CollectedAppUsage>
)

object UsageStatsHelper {

    fun todayDateKey(nowMs: Long = System.currentTimeMillis()): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return sdf.format(Date(nowMs))
    }

    fun collectTodayUsage(context: Context): CollectedUsageSummary {
        val now = System.currentTimeMillis()
        val dateKey = todayDateKey(now)
        if (!UsageAccessHelper.hasUsageAccessPermission(context)) {
            return CollectedUsageSummary(date = dateKey, totalScreenTimeMs = 0L, apps = emptyList())
        }

        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return CollectedUsageSummary(date = dateKey, totalScreenTimeMs = 0L, apps = emptyList())

        val startOfDay = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            startOfDay,
            now
        ).orEmpty()

        // Count foreground launch events via official queryEvents API
        val launchCounts = mutableMapOf<String, Int>()
        try {
            val events = usageStatsManager.queryEvents(startOfDay, now)
            val event = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                @Suppress("DEPRECATION")
                if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED ||
                    event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND
                ) {
                    val pkg = event.packageName ?: continue
                    launchCounts[pkg] = (launchCounts[pkg] ?: 0) + 1
                }
            }
        } catch (_: Exception) {
            // Gracefully ignore if queryEvents is unavailable on a specific OEM
        }

        val aggregated = mutableMapOf<String, Pair<Long, Long>>() // pkg -> (totalMs, lastUsedMs)
        for (item in stats) {
            val fgMs = item.totalTimeInForeground
            if (fgMs <= 0L) continue
            val pkg = item.packageName ?: continue
            if (pkg == context.packageName) continue
            val existing = aggregated[pkg]
            val mergedTime = (existing?.first ?: 0L) + fgMs
            val mergedLastUsed = maxOf(existing?.second ?: 0L, item.lastTimeUsed)
            aggregated[pkg] = mergedTime to mergedLastUsed
        }

        val pm = context.packageManager
        val collectedApps = aggregated.entries
            .map { (pkg, pair) ->
                val label = resolveAppLabel(pm, pkg)
                CollectedAppUsage(
                    packageName = pkg,
                    appName = label,
                    usageTimeMs = pair.first,
                    launchCount = launchCounts[pkg] ?: 0,
                    lastUsedMs = pair.second
                )
            }
            .sortedByDescending { it.usageTimeMs }

        val totalMs = collectedApps.sumOf { it.usageTimeMs }
        return CollectedUsageSummary(
            date = dateKey,
            totalScreenTimeMs = totalMs,
            apps = collectedApps
        )
    }

    private fun resolveAppLabel(pm: PackageManager, packageName: String): String {
        return try {
            val appInfo = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(appInfo).toString().ifBlank { fallbackLabel(packageName) }
        } catch (_: Exception) {
            fallbackLabel(packageName)
        }
    }

    private fun fallbackLabel(packageName: String): String {
        return packageName.substringAfterLast('.')
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
    }
}
