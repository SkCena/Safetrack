package com.example.safetrack

import android.content.Context
import android.net.wifi.WifiManager
import android.telephony.TelephonyManager
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object NetworkIntelligenceLocator {

    data class NetworkLocation(
        val latitude: Double,
        val longitude: Double,
        val accuracy: Float, // in meters
        val confidence: String, // "high", "medium", "low"
        val source: String, // "wifi_db", "ip_geo", "cell_triangulation", "combined"
        val area: String, // "Downtown", "Suburb", etc.
        val nearbyLandmarks: List<String>,
        val ispInfo: IspInfo
    )

    data class IspInfo(
        val ispName: String,
        val connectionType: String, // "Fiber", "4G", "5G"
        val city: String,
        val region: String,
        val country: String
    )

    suspend fun getNetworkLocation(context: Context): NetworkLocation? {
        val coroutineScope = CoroutineScope(Dispatchers.IO)

        // Parallel fetch from all sources
        val deferredResults = listOf(
            coroutineScope.async { getWifiDatabaseLocation(context) },
            coroutineScope.async { getIpGeolocation() },
            coroutineScope.async { getCellTowerApproximation(context) }
        )

        val results = deferredResults.awaitAll().filterNotNull()

        return if (results.isNotEmpty()) {
            // Combine all results for best accuracy
            combineLocationResults(results)
        } else null
    }

    // METHOD 1: WiFi BSSID Database Lookup
    private suspend fun getWifiDatabaseLocation(context: Context): NetworkLocation? {
        val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager

        try {
            val connectionInfo = wifiManager.connectionInfo
            val bssid = connectionInfo.bssid ?: return null

            // Query multiple WiFi databases
            val databases = listOf(
                "https://api.mylnikov.org/geolocation/wifi?v=1.2&bssid=$bssid",
                "https://api.opengps.ru/location/wifi?v=1.1&bssid=$bssid",
                "https://wifidb.org/api/v1/location/$bssid"
            )

            for (url in databases) {
                try {
                    val response = fetchUrl(url)
                    val json = JSONObject(response)

                    if (json.has("lat") && json.has("lon")) {
                        return NetworkLocation(
                            latitude = json.getDouble("lat"),
                            longitude = json.getDouble("lon"),
                            accuracy = json.optFloat("accuracy", 50f),
                            confidence = "high",
                            source = "wifi_db",
                            area = json.optString("area", "Unknown"),
                            nearbyLandmarks = json.optJSONArray("landmarks")?.let {
                                (0 until it.length()).map { i -> it.getString(i) }
                            } ?: emptyList(),
                            ispInfo = getIspInfo(json)
                        )
                    }
                } catch (e: Exception) { continue }
            }
        } catch (e: Exception) { }
        return null
    }

    // METHOD 2: Advanced IP Geolocation
    private suspend fun getIpGeolocation(): NetworkLocation? {
        val apis = listOf(
            Triple("ipapi", "https://ipapi.co/json/", ::parseIpApi),
            Triple("ipinfo", "https://ipinfo.io/json", ::parseIpInfo),
            Triple("ipwho", "https://ipwho.is/", ::parseIpWho),
            Triple("ipgeolocation", "https://api.ipgeolocation.io/ipgeo?apiKey=YOUR_KEY", ::parseIpGeo)
        )

        for ((name, url, parser) in apis) {
            try {
                val response = fetchUrl(url)
                val location = parser(response)
                if (location != null) return location.copy(source = "ip_geo_$name")
            } catch (e: Exception) { continue }
        }
        return null
    }

    // --- Helper Parsers for IP Geolocation ---
    private fun parseIpApi(response: String): NetworkLocation? {
        val json = JSONObject(response)
        return NetworkLocation(
            latitude = json.optDouble("latitude"),
            longitude = json.optDouble("longitude"),
            // Update accuracy based on table: IP is 2000-5000m
            accuracy = 3500f,
            confidence = "low",
            source = "ip_api",
            area = "${json.optString("city")}, ${json.optString("region")}",
            nearbyLandmarks = emptyList(),
            ispInfo = IspInfo(
                ispName = json.optString("org"),
                connectionType = json.optString("connection_type", "Unknown"),
                city = json.optString("city"),
                region = json.optString("region"),
                country = json.optString("country_name")
            )
        )
    }

    // Stub parsers for other IP APIs - implementation would be similar to parseIpApi
    private fun parseIpInfo(response: String): NetworkLocation? = null // TODO: Implement
    private fun parseIpWho(response: String): NetworkLocation? = null // TODO: Implement
    private fun parseIpGeo(response: String): NetworkLocation? = null // TODO: Implement

    // METHOD 3: Cell Tower + Network Type Analysis
    private suspend fun getCellTowerApproximation(context: Context): NetworkLocation? {
        val telephony = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

        try {
            val operator = telephony.networkOperator
            if (operator == null || operator.length < 5) return null

            val mcc = operator.substring(0, 3)
            val mnc = operator.substring(3)

            // Get network generation
            val networkType = when (telephony.dataNetworkType) {
                TelephonyManager.NETWORK_TYPE_NR -> "5G"
                TelephonyManager.NETWORK_TYPE_LTE -> "4G"
                else -> "3G"
            }

            // Query cell tower database
            val cellApis = listOf(
                "https://opencellid.org/cell/getInArea?key=YOUR_KEY&mcc=$mcc&mnc=$mnc&format=json",
                "https://api.mylnikov.org/geolocation/cell?v=1.1&mcc=$mcc&mnc=$mnc"
            )

            for (url in cellApis) {
                try {
                    val response = fetchUrl(url)
                    val json = JSONObject(response)

                    if (json.has("lat") && json.has("lon")) {
                        return NetworkLocation(
                            latitude = json.getDouble("lat"),
                            longitude = json.getDouble("lon"),
                            accuracy = json.optFloat("range", 1000f),
                            confidence = "medium",
                            source = "cell_tower",
                            area = json.optString("address", "Unknown Area"),
                            nearbyLandmarks = extractLandmarks(json),
                            ispInfo = IspInfo(
                                ispName = getCarrierName(mcc, mnc),
                                connectionType = networkType,
                                city = json.optString("city", ""),
                                region = json.optString("region", ""),
                                country = json.optString("country", "")
                            )
                        )
                    }
                } catch (e: Exception) { continue }
            }

            // If no specific tower, use MCC+MNC for region approximation
            return getRegionFromMccMnc(mcc, mnc)

        } catch (e: Exception) { }
        return null
    }

    // Combine multiple sources for better accuracy
    private fun combineLocationResults(results: List<NetworkLocation>): NetworkLocation {
        // Weight by accuracy - lower accuracy = higher weight
        var totalWeight = 0.0
        var weightedLat = 0.0
        var weightedLon = 0.0

        for (result in results) {
            val weight = 1.0 / result.accuracy.toDouble()
            totalWeight += weight
            weightedLat += result.latitude * weight
            weightedLon += result.longitude * weight
        }

        val bestResult = results.minByOrNull { it.accuracy } ?: results.first()

        return NetworkLocation(
            latitude = weightedLat / totalWeight,
            longitude = weightedLon / totalWeight,
            accuracy = results.minOf { it.accuracy },
            confidence = if (results.size > 1) "high" else bestResult.confidence,
            source = "combined_${results.size}_sources",
            area = bestResult.area,
            nearbyLandmarks = results.flatMap { it.nearbyLandmarks }.distinct(),
            ispInfo = bestResult.ispInfo
        )
    }

    // Get nearby landmarks using reverse geocoding
    suspend fun getNearbyPlaces(lat: Double, lon: Double): List<String> {
        return try {
            val url = "https://nominatim.openstreetmap.org/reverse?format=json&lat=$lat&lon=$lon&zoom=18&addressdetails=1"
            val response = fetchUrl(url)
            val json = JSONObject(response)

            val address = json.optJSONObject("address")
            val landmarks = mutableListOf<String>()

            address?.let {
                it.optString("road").takeIf { it.isNotEmpty() }?.let { landmarks.add("Road: $it") }
                it.optString("neighbourhood").takeIf { it.isNotEmpty() }?.let { landmarks.add("Area: $it") }
                it.optString("suburb").takeIf { it.isNotEmpty() }?.let { landmarks.add("Suburb: $it") }
                it.optString("amenity").takeIf { it.isNotEmpty() }?.let { landmarks.add("Nearby: $it") }
            }

            landmarks
        } catch (e: Exception) {
            emptyList()
        }
    }

    private suspend fun fetchUrl(urlString: String): String {
        return withContext(Dispatchers.IO) {
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.inputStream.bufferedReader().use { it.readText() }
        }
    }

    // --- Helper Stubs ---
    private fun getIspInfo(json: JSONObject): IspInfo = IspInfo("Unknown", "Unknown", "Unknown", "Unknown", "Unknown")
    private fun extractLandmarks(json: JSONObject): List<String> = emptyList()
    private fun getCarrierName(mcc: String, mnc: String): String = "Unknown"
    private fun getRegionFromMccMnc(mcc: String, mnc: String): NetworkLocation? = null
}
