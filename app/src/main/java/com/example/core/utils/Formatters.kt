package com.example.core.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Formatters {

    fun formatDurationHms(ms: Long): String {
        val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
    }

    fun formatMinutesCompact(minutes: Long): String {
        val hrs = minutes / 60
        val mins = minutes % 60
        return if (hrs > 0) "${hrs}h ${mins}m" else "${mins}m"
    }

    fun formatTimeAgo(timestampMs: Long, nowMs: Long = System.currentTimeMillis()): String {
        if (timestampMs <= 0L) return "—"
        val diffSec = ((nowMs - timestampMs) / 1000L).coerceAtLeast(0L)
        return when {
            diffSec < 60L -> "${diffSec}s"
            diffSec < 3600L -> "${diffSec / 60L}m"
            diffSec < 86400L -> "${diffSec / 3600L}h"
            else -> SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()).format(Date(timestampMs))
        }
    }

    fun deviceDisplayName(): String {
        val manufacturer = Build.MANUFACTURER.orEmpty().replaceFirstChar { it.uppercase() }
        val model = Build.MODEL.orEmpty()
        return if (model.startsWith(manufacturer, ignoreCase = true)) model else "$manufacturer $model".trim()
    }

    fun androidVersionLabel(): String = "Android ${Build.VERSION.RELEASE ?: Build.VERSION.SDK_INT}"
}

object ConnectivityObserver {
    fun isOnline(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun observeOnline(context: Context): Flow<Boolean> = callbackFlow {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        if (cm == null) {
            trySend(false)
            close()
            return@callbackFlow
        }
        trySend(isOnline(context))

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(true)
            }

            override fun onLost(network: Network) {
                trySend(isOnline(context))
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                trySend(networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        cm.registerNetworkCallback(request, callback)
        awaitClose { cm.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()
}
