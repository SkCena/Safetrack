package com.example.safetrack

import android.app.usage.UsageStatsManager
import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class TrackingWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Log.d("TrackingWorker", "Background Tracking Started")

        // 1. Fetch current location
        val latLon = LocationTracker.getCurrentLocation(applicationContext) ?: Pair(0.0, 0.0)

        // 2. Fetch precise app usage stats for the last 15 minutes
        val usageStatsManager = applicationContext.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val endTime = System.currentTimeMillis()
        val startTime = endTime - 15 * 60 * 1000 // 15 minutes interval

        // Query usage stats
        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            startTime,
            endTime
        )

        // Find the app with the most foreground time in this interval
        var mostUsedPackage = "unknown"
        var maxForegroundTime: Long = 0
        var lastTimeUsed: Long = 0

        stats?.forEach { usageStats ->
            if (usageStats.totalTimeInForeground > maxForegroundTime) {
                maxForegroundTime = usageStats.totalTimeInForeground
                mostUsedPackage = usageStats.packageName
                lastTimeUsed = usageStats.lastTimeUsed
            }
        }

        // 3. Insert into Room Database
        val database = AppDatabase.getDatabase(applicationContext)
        val dao = database.trackingDao()
        val log = TrackingData(
            timestamp = System.currentTimeMillis(),
            latitude = latLon.first,
            longitude = latLon.second,
            packageName = mostUsedPackage,
            foregroundTimeMs = maxForegroundTime,
            lastTimeUsed = lastTimeUsed
        )
        dao.insertLog(log)

        // 4. Send to Telegram
        TelegramSyncHelper.sendToTelegram(
            lat = latLon.first.toString(),
            lng = latLon.second.toString(),
            usageApp = mostUsedPackage
        )

        return Result.success()
    }
}
