package com.example.core.data.sync

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.R
import com.example.core.data.local.LocalPreferencesRepository
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Official FCM service receiving non-sensitive wake-up signals only
 * (e.g. "New parental-control settings available"), then fetching latest
 * rules securely from Firestore via UsageSyncWorker.
 */
class FamilyGuardMessagingService : FirebaseMessagingService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        val prefs = LocalPreferencesRepository(applicationContext)
        serviceScope.launch {
            prefs.setFcmToken(token)
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        // Trigger secure Firestore settings & usage refresh without embedding sensitive data in payload
        UsageSyncWorker.triggerImmediateSync(applicationContext)

        val eventType = message.data["type"].orEmpty()
        val displayTitle = getString(R.string.app_name)
        val displayBody = when (eventType) {
            "limits_updated" -> getString(R.string.fcm_limits_updated)
            "sync_failed" -> getString(R.string.fcm_sync_attention)
            else -> getString(R.string.fcm_settings_updated)
        }
        postNonSensitiveNotification(displayTitle, displayBody)
    }

    private fun postNonSensitiveNotification(title: String, body: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_UPDATES,
                getString(R.string.notification_channel_updates),
                NotificationManager.IMPORTANCE_DEFAULT
            )
            manager.createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(this, CHANNEL_UPDATES)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        manager.notify(UPDATE_NOTIFICATION_ID, notification)
    }

    companion object {
        private const val CHANNEL_UPDATES = "family_guard_updates"
        private const val UPDATE_NOTIFICATION_ID = 4103
    }
}
