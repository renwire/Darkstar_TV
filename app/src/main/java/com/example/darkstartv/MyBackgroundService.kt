package com.example.darkstartv

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaSessionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.annotation.RequiresApi
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class MyBackgroundService : Service() {

    private val urlSet = "http://192.168.0.10:5279/nowplaying" //set to your endpoint for the request
    //private val urlSet = "https://example.com/nowplaying" //you can also use https
    private val secretKey = "Key_Goes_Here" //this can be removed in sendNowPlayingToApi look for the comment like this

    // MediaSession APIs
    private lateinit var mediaSessionManager: MediaSessionManager
    private lateinit var sessionListener: MediaSessionManager.OnActiveSessionsChangedListener
    private lateinit var listenerComponent: ComponentName

    // Polling loop (30 s)
    private val pollIntervalMs = 30_000L
    private val handler = Handler(Looper.getMainLooper())
    private val poller = object : Runnable {
        override fun run() {
            fetchNowPlayingData()
            handler.postDelayed(this, pollIntervalMs)
        }
    }


    // Remember last reported values
    private var lastTitle: String? = null
    private var lastArtist: String? = null
    private var lastSource: String? = null

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate() {
        super.onCreate()

        // ─── 1) Foreground service setup ─────────────────────────────────────
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())

        // ─── 2) Kick off polling loop ────────────────────────────────────────
        handler.post(poller)

        // ─── 3) Initialize MediaSessionManager & listener ───────────────────
        mediaSessionManager = getSystemService(Context.MEDIA_SESSION_SERVICE)
                as MediaSessionManager

        sessionListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
            controllers.orEmpty().forEach { ctrl ->
                ctrl.metadata?.let { meta ->
                    val title  = meta.getString(MediaMetadata.METADATA_KEY_TITLE)  ?: return@let
                    val artist = meta.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: return@let
                    Log.i("DarkstarService", "▶️ SessionListener: $title — $artist")
                }
            }
        }

        // ─── 4) Register listener with NotificationListenerService ───────────
        listenerComponent = ComponentName(this, MediaNotificationListener::class.java)
        try {
            mediaSessionManager.addOnActiveSessionsChangedListener(
                sessionListener,
                listenerComponent
            )
            // initial query
            mediaSessionManager.getActiveSessions(listenerComponent)
                ?.let(sessionListener::onActiveSessionsChanged)
        } catch (sec: SecurityException) {
            Log.w("DarkstarService",
                "Notification-access not granted; MediaSessions unavailable", sec)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i("DarkstarService", "▶️ Service started")
        return START_STICKY
    }

    override fun onDestroy() {
        // clean up
        try {
            mediaSessionManager.removeOnActiveSessionsChangedListener(sessionListener)
        } catch (_: Exception) { /* ignore */ }
        handler.removeCallbacks(poller)
        Log.i("DarkstarService", "🛑 Service stopped")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null // Return null if your service does not support binding
    }

    /**
     * Pulls the first active session’s metadata and
     * only alerts your API when something has changed.
     */
    private fun fetchNowPlayingData() {
        val controllers = try {
            mediaSessionManager.getActiveSessions(listenerComponent)
        } catch (_: SecurityException) {
            Log.w("DarkstarService", "Cannot fetch sessions: no Notification Access")
            return
        }

        if (controllers.isNullOrEmpty()) {
            Log.i("DarkstarService", "🔄 No active media sessions, falling back to notifications")

            // Check media notifications as a fallback
            MediaNotificationListener.currentMediaNotifications.forEach { (pkg, sbn) ->
                val notif = sbn.notification
                val title = notif.extras?.getString(android.app.Notification.EXTRA_TITLE)
                val text = notif.extras?.getString(android.app.Notification.EXTRA_TEXT)

                // Handle Plex-specific metadata
                val plexTitle = if (pkg.contains("plexapp")) notif.extras?.getString("android.title") else null
                val plexText = if (pkg.contains("plexapp")) notif.extras?.getString("android.text") else null

                val finalTitle = plexTitle ?: title
                val finalText = plexText ?: text

                if (!finalTitle.isNullOrBlank() || !finalText.isNullOrBlank()) {
                    Log.i("DarkstarService", "🎵 From Notifications! $finalTitle — $finalText (via $pkg)")

                    // Send to API
                    sendNowPlayingToApi(finalTitle ?: "Unknown", finalText ?: "Unknown", pkg)
                    return // Process one valid notification
                }
            }

            Log.i("DarkstarService", "🔄 No media notifications found")
            return
        }

        for (ctrl in controllers) {
            val pkg = ctrl.packageName
            val meta = ctrl.metadata ?: continue

            var title: String? = null
            var artist: String? = null

            // Extract standard metadata (Music Apps)
            title = meta.getString(MediaMetadata.METADATA_KEY_TITLE)
            artist = meta.getString(MediaMetadata.METADATA_KEY_ARTIST)

            // Handle Plex-specific metadata
            if (pkg == "com.plexapp.android") {
                title = meta.getString("android.title") ?: title
                artist = meta.getString("android.text") ?: artist
            }

            // Check if metadata was extracted
            if (title.isNullOrBlank()) {
                Log.i("DarkstarService", "🔄 $pkg: Sessions found but no valid metadata available")
                continue
            }

            // Only send updates if metadata or source changed
            if (title != lastTitle || artist != lastArtist || pkg != lastSource) {
                lastTitle = title
                lastArtist = artist
                lastSource = pkg

                Log.i(
                    "DarkstarService",
                    "🎵 Changed! $title — ${artist ?: "Unknown"} (via $pkg)"
                )

                sendNowPlayingToApi(title, artist, pkg)
            }

            return // Process one valid session
        }

        Log.i("DarkstarService", "🔄 Sessions found but no metadata available")
    }
    /**
     * Posts a simple JSON payload to your endpoint.
     * Replace API_URL with your real URL.
     */
    private fun sendNowPlayingToApi(title: String, artist: String?, source: String) {
        Thread {
            try {
                val apiUrl = URL(urlSet)
                val conn = (apiUrl.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json; utf-8")
                    setRequestProperty("X-TV-Key", secretKey) //Delete this line for non api key requests
                    doOutput = true
                }

                val payload = JSONObject().apply {
                    if (source == "com.plexapp.android") {
                        // Plex-specific handling
                        put("Title", title)
                        put("Year", artist ?: "Unknown") // "artist" is actually the year in Plex
                        put("Source", source)
                    } else {
                        // Standard handling
                        put("Title", title)
                        put("Artist", artist ?: "Unknown") // Safeguard against null artists
                        put("Source", source)
                    }
                }.toString()

                conn.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
                val code = conn.responseCode
                Log.i("DarkstarService", "API responded: $code")
                conn.disconnect()
            } catch (e: Exception) {
                Log.e("DarkstarService", "Failed to send now-playing", e)
            }
        }.start()
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_DEFAULT
                )
            )
        }
    }

    private fun buildNotification(): Notification =
        Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Service Running")
            .setContentText("Darkstar running")
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .build()

    companion object {
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID      = "default_channel_id"
        private const val CHANNEL_NAME    = "Default Channel"
    }
}
