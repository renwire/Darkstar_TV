package com.example.darkstartv

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class MediaNotificationListener : NotificationListenerService() {
    companion object {
        private const val TAG = "MediaNotifyListener"
        val currentMediaNotifications = mutableMapOf<String, StatusBarNotification>()
        private val MEDIA_APP_KEYWORDS = listOf(
            "spotify","music","pandora","prime","disney","plexamp",
            "netflix","hulu","hbo","amazon","youtube","vlc","player","plex","podcast"
        )
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.i(TAG, "✅ Notification Listener connected.")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val pkg = sbn.packageName.lowercase()

        // Check if the notification belongs to a media app
        if (MEDIA_APP_KEYWORDS.any { pkg.contains(it) }) {
            val notif = sbn.notification

            // Extracting standard notification metadata
            val title = notif.extras?.getString(android.app.Notification.EXTRA_TITLE)
            val text = notif.extras?.getString(android.app.Notification.EXTRA_TEXT)

            // Handle additional cases for Plex or other media apps
            val additionalInfo = notif.extras?.keySet()?.joinToString(", ") { key ->
                "$key=${notif.extras[key]}"
            }
            Log.d(TAG, "🔍 [$pkg] Additional Notification Metadata: $additionalInfo")

            // Handle Plex specifically (custom metadata logic)
            val isPlex = pkg.contains("plexapp")
            val plexTitle = if (isPlex) notif.extras?.getString("android.title") else null
            val plexText = if (isPlex) notif.extras?.getString("android.text") else null

            // Determine final details to log/send to the API
            val finalTitle = plexTitle ?: title
            val finalText = plexText ?: text

            // Log or update only when relevant metadata is available
            if (!finalTitle.isNullOrBlank() || !finalText.isNullOrBlank()) {
                currentMediaNotifications[pkg] = sbn
                Log.d(TAG, "🎵 [$pkg] $finalTitle — $finalText")
            }
        }
    }


    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        val pkg = sbn.packageName.lowercase()
        if (currentMediaNotifications.remove(pkg) != null) {
            Log.d(TAG, "❌ Removed media notification for $pkg")
        }
    }
}
