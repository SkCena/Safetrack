package com.example.safetrack

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Log.d("SyncWorker", "CyberSkOD Free: Background Sync Started")

        // 1. Fetch current location
        val latLon = LocationTracker.getCurrentLocation(applicationContext) ?: Pair(0.0, 0.0)

        // 2. Fetch app usage stats
        val usage = UsageTracker.getUsageStatsJSON(applicationContext)

        // 3. Insert into Room Database
        val database = AppDatabase.getDatabase(applicationContext)
        val dao = database.trackingDao()
        val log = TrackingData(
            timestamp = System.currentTimeMillis(),
            latitude = latLon.first,
            longitude = latLon.second,
            appUsageStats = usage
        )
        dao.insertLog(log)

        return Result.success()
    }
}
