package com.example.darkstartv

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

class PermissionActivity : AppCompatActivity() {
    private val REQUEST_POST_NOTIF = 1

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Request notification permission on Android 13+

        //ensureNotificationsEnabled()
        Intent(this, MyBackgroundService::class.java).also { svc ->
            ContextCompat.startForegroundService(this, svc)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                REQUEST_POST_NOTIF
            )
        } else {
            finish() // No prompt needed on older versions
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        // Optionally check grantResults[0] == PackageManager.PERMISSION_GRANTED
        finish()
    }

    /**
     * Checks if notifications are enabled for this app.
     * If not, deep-links the user into the TV Settings → App → Additional permissions screen.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    fun Context.ensureNotificationsEnabled() {
        if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) {
            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        }
    }
}
