package com.example.safetrack

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import java.text.SimpleDateFormat
import java.util.Locale

class TrackingWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Log.d("TrackingWorker", "Background Tracking Started")
        val context = applicationContext

        // 1. Fetch complete location data with all fallbacks
        val locationData = LocationTracker.getCompleteLocation(context)

        // 2. Fetch precise app usage events
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val endTime = System.currentTimeMillis()
        val startTime = endTime - 15 * 60 * 1000 // 15 minutes interval

        val usageEvents = usageStatsManager.queryEvents(startTime, endTime)
        val event = UsageEvents.Event()
        var actualForegroundApp = ""

        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                val pkg = event.packageName
                if (pkg != "com.google.android.googlequicksearchbox" &&
                    pkg != "com.miui.home" &&
                    !pkg.contains("launcher") &&
                    !pkg.contains("systemui")) {
                    actualForegroundApp = pkg
                }
            }
        }

        if (actualForegroundApp.isEmpty()) {
            actualForegroundApp = "None/Home Screen"
        }

        // 3. Insert into Room Database
        val database = AppDatabase.getDatabase(context)
        val dao = database.trackingDao()
        val log = TrackingData(
            timestamp = System.currentTimeMillis(),
            latitude = locationData.gpsLat ?: 0.0,
            longitude = locationData.gpsLon ?: 0.0,
            packageName = actualForegroundApp,
            foregroundTimeMs = 0,
            lastTimeUsed = System.currentTimeMillis()
        )
        dao.insertLog(log)

        // 4. Send complete data to Telegram via server API
        TelegramSyncHelper.sendToTelegram(context, locationData)

        return Result.success()
    }
}
