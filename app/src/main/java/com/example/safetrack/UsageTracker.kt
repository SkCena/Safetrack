package com.example.safetrack

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Process
import android.provider.Settings
import android.util.Log
import org.json.JSONObject

object UsageTracker {

    fun hasUsageStatsPermission(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun requestPermission(context: Context) {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    suspend fun saveUsageStatsToDb(context: Context) {
        if (!hasUsageStatsPermission(context)) {
            Log.e("UsageTracker", "Permission denied: Cannot access usage stats")
            return
        }

        try {
            val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val currentTime = System.currentTimeMillis()
            val startTime = currentTime - 24 * 60 * 60 * 1000 // 24 hours ago

            val stats = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                startTime,
                currentTime
            )

            val dao = AppDatabase.getDatabase(context).trackingDao()

            if (stats != null) {
                for (usageStats in stats) {
                    if (usageStats.totalTimeInForeground > 0) {
                        val trackingData = TrackingData(
                            timestamp = System.currentTimeMillis(),
                            packageName = usageStats.packageName,
                            foregroundTimeMs = usageStats.totalTimeInForeground,
                            lastTimeUsed = usageStats.lastTimeUsed,
                            category = "GENERAL",
                            usageType = "APP"
                        )
                        dao.insertLog(trackingData)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("UsageTracker", "Error saving usage stats to DB: ${e.message}", e)
        }
    }

    fun getUsageStatsJSON(context: Context): String {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val currentTime = System.currentTimeMillis()
        val startTime = currentTime - 30 * 60 * 1000 // 30 minutes ago

        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            startTime,
            currentTime
        )

        val jsonObject = JSONObject()

        if (stats != null) {
            for (usageStats in stats) {
                if (usageStats.totalTimeInForeground > 0) {
                    val packageName = usageStats.packageName
                    val timeInSeconds = usageStats.totalTimeInForeground / 1000
                    jsonObject.put(packageName, timeInSeconds)
                }
            }
        }

        val jsonString = jsonObject.toString()
        Log.d("UsageTracker", "CyberSkOD Free: Usage Stats: $jsonString")

        return jsonString
    }
}
