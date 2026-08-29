package com.example.safetrack

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Log.d("SyncWorker", "CyberSkOD Free: Background Sync Started")

        val latLon = LocationTracker.getCurrentLocation(applicationContext) ?: Pair(0.0, 0.0)
        val usage = try { UsageTracker.getUsageStatsJSON(applicationContext) } catch (e: Exception) { "No data" }

        val database = AppDatabase.getDatabase(applicationContext)
        val dao = database.trackingDao()
        
        // Satisfying the 5 parameters Claude added to the Database
        val log = TrackingData(
            timestamp = System.currentTimeMillis(),
            latitude = latLon.first,
            longitude = latLon.second,
            packageName = usage.take(1000), // Saving JSON data safely here
            foregroundTimeMs = 0L,
            lastTimeUsed = 0L
        )
        dao.insertLog(log)

        return Result.success()
    }
}
