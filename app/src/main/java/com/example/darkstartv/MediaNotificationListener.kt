package com.example.darkstartv

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class MediaNotificationListener : NotificationListenerService() {
    companion object {
        private const val TAG = "MediaNotifyListener"
        private val currentMediaNotifications = mutableMapOf<String, StatusBarNotification>()
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
        if (MEDIA_APP_KEYWORDS.any { pkg.contains(it) }) {
            val notif = sbn.notification
            val title = notif.extras?.getString(android.app.Notification.EXTRA_TITLE)
            val text  = notif.extras?.getString(android.app.Notification.EXTRA_TEXT)
            if (!title.isNullOrBlank() || !text.isNullOrBlank()) {
                currentMediaNotifications[pkg] = sbn
                Log.d(TAG, "🎵 [$pkg] $title — $text")
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
