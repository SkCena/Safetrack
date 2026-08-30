package com.example.safetrack

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

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
                if (pkg != null && !isIgnoredPackage(pkg)) {

                    // Close previous session when a NEW app comes to foreground
                    if (lastPkg.isNotEmpty()) {
                        appendSession(context, timeline, lastPkg, lastStartTime, event.timeStamp)
                    }

                    lastPkg = pkg
                    lastStartTime = event.timeStamp
                }
            } else if (event.eventType == UsageEvents.Event.MOVE_TO_BACKGROUND) {
                // Close the current session when app goes to background
                if (lastPkg.isNotEmpty() && event.packageName == lastPkg) {
                    appendSession(context, timeline, lastPkg, lastStartTime, event.timeStamp)
                    lastPkg = ""
                    lastStartTime = 0L
                }
            }
        }

        // BUGFIX: Close the final active session - the currently foregrounded app
        // never receives a MOVE_TO_BACKGROUND event inside the query window, so
        // its duration was previously lost (showed as 0).
        if (lastPkg.isNotEmpty()) {
            appendSession(context, timeline, lastPkg, lastStartTime, endTime)
        }

        return if (timeline.isEmpty()) {
            "📊 No app usage detected in the last $hours hour(s). Make sure Usage Stats permission is granted."
        } else {
            timeline.take(20).joinToString("\n")
        }
    }

    private fun appendSession(
        context: Context,
        timeline: MutableList<String>,
        pkg: String,
        startMs: Long,
        endMs: Long
    ) {
        val durationMs = max(0L, endMs - startMs)
        val totalSeconds = durationMs / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        // Format duration - show seconds too when < 1 min so "0 min" never appears
        // for short sessions that were previously invisible.
        val durationStr = when {
            minutes == 0L -> "${seconds}s"
            seconds == 0L -> "${minutes} min"
            else -> "${minutes} min ${seconds}s"
        }
        val timeStr = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(startMs))
        val appName = try {
            context.packageManager.getApplicationLabel(
                context.packageManager.getApplicationInfo(pkg, 0)
            ).toString()
        } catch (_: Exception) { pkg }
        timeline.add(0, "⏱ $timeStr - $appName (Duration: $durationStr)")
    }

    private fun isIgnoredPackage(pkg: String): Boolean {
        return pkg.contains("launcher") ||
                pkg == "com.miui.home" ||
                pkg == "com.google.android.googlequicksearchbox" ||
                pkg.contains("systemui") ||
                pkg == context_safe_self()
    }

    private fun context_safe_self(): String = "com.example.safetrack"
}

