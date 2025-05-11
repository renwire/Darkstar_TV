package com.example.darkstartv


import android.app.Application
import android.util.Log
import androidx.work.*
import java.util.concurrent.TimeUnit

class DarkstarApplication  : Application() {
    override fun onCreate() {
        super.onCreate()
        Log.i("DarkstarApp", "🌑 DarkstarApplication.onCreate()")
        schedulePeriodicSync()
    }

    private fun schedulePeriodicSync() {
        val work = PeriodicWorkRequestBuilder<SyncWorker>(6, TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()

        WorkManager.getInstance(this)
            .enqueueUniquePeriodicWork(
                "DarkstarSync",
                ExistingPeriodicWorkPolicy.KEEP,
                work
            )
    }
}