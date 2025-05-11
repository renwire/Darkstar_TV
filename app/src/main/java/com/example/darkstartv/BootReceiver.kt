package com.example.darkstartv


import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.*
import java.util.concurrent.TimeUnit

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Reschedule your WorkManager tasks after reboot
            val work = PeriodicWorkRequestBuilder<SyncWorker>(
                6, TimeUnit.HOURS
            )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()

            WorkManager
                .getInstance(context)
                .enqueueUniquePeriodicWork(
                    "DarkstarSync",
                    ExistingPeriodicWorkPolicy.REPLACE,
                    work
                )
        }
    }
}