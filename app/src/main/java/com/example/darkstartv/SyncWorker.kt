package com.example.darkstartv

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class SyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        // … perform your background logic …
        Log.i("DarkstarSync", "SyncWorker.doWork() fired at ${System.currentTimeMillis()}")
        return Result.success()
    }
}
