package com.example.safetrack

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ActivityTimelineUtility {
    fun generateActivityTimeline(context: Context, hours: Int): String {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val endTime = System.currentTimeMillis()
        val startTime = endTime - hours * 60 * 60 * 1000
        val usageEvents = usageStatsManager.queryEvents(startTime, endTime)
        val event = UsageEvents.Event()
        val timeline = mutableListOf<String>()
        var lastPkg = ""
        var lastStartTime = 0L

        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                val pkg = event.packageName
                if (pkg != null && !pkg.contains("launcher") && pkg != "com.miui.home" && pkg != "com.google.android.googlequicksearchbox" && !pkg.contains("systemui")) {

                    // Close previous session
                    if (lastPkg.isNotEmpty()) {
                        val duration = (event.timeStamp - lastStartTime) / 60000
                        val timeStr = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(lastStartTime))
                        val appName = try { context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(lastPkg, 0)).toString() } catch(e:Exception) { lastPkg }
                        timeline.add(0, "⏱ $timeStr - $appName (Duration: $duration mins)")
                    }

                    lastPkg = pkg
                    lastStartTime = event.timeStamp
                }
            }
        }
        return timeline.take(20).joinToString("\n")
    }
}
