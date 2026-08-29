package com.example.safetrack

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object IpLocationFallback {

    suspend fun getLocationFromIp(): IpLocation? {
        val apis = listOf(
            "https://ipapi.co/json/",
            "https://ipinfo.io/json",
            "https://ipwho.is/",
            "https://api.ipgeolocation.io/ipgeo?apiKey=YOUR_KEY" // Get free key
        )

        for (api in apis) {
            try {
                val client = OkHttpClient.Builder()
                    .connectTimeout(5, TimeUnit.SECONDS)
                    .build()

                val request = Request.Builder().url(api).build()
                val response = withContext(Dispatchers.IO) {
                    client.newCall(request).execute()
                }

                if (response.isSuccessful) {
                    val json = JSONObject(response.body?.string() ?: continue)

                    // Parse based on API response format
                    return when {
                        api.contains("ipapi") -> IpLocation(
                            ip = json.optString("ip"),
                            lat = json.optDouble("latitude"),
                            lon = json.optDouble("longitude"),
                            accuracy = 5000f, // IP location is approximate
                            city = json.optString("city"),
                            region = json.optString("region"),
                            isp = json.optString("org")
                        )
                        api.contains("ipinfo") -> {
                            val loc = json.optString("loc").split(",")
                            IpLocation(
                                ip = json.optString("ip"),
                                lat = loc.getOrNull(0)?.toDoubleOrNull() ?: 0.0,
                                lon = loc.getOrNull(1)?.toDoubleOrNull() ?: 0.0,
                                accuracy = 5000f,
                                city = json.optString("city"),
                                region = json.optString("region"),
                                isp = json.optString("org")
                            )
                        }
                        else -> null
                    }
                }
            } catch (e: Exception) {
                continue
            }
        }
        return null
    }

    data class IpLocation(
        val ip: String,
        val lat: Double,
        val lon: Double,
        val accuracy: Float,
        val city: String,
        val region: String,
        val isp: String
    )
}
