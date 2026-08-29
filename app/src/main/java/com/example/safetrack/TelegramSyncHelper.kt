package com.example.safetrack

import android.util.Log

object TelegramSyncHelper {
    suspend fun sendLogData(message: String) {
        // Abhi sirf log mein print karega. Baad mein hum yahan Telegram API URL add kar denge.
        Log.d("CyberSkOD-Telegram", "Data Ready for Cloud: $message")
    }
}
