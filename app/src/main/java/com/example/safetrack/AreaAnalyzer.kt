package com.example.safetrack

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object AreaAnalyzer {

    data class AnalysisResult(
        val precision: String,
        val locationType: String,
        val nearbyRoutes: List<String>,
        val landmarks: List<String>,
        val areaDescription: String
    )

    suspend fun analyzeAreaFromNetworkData(data: Map<String, Any?>): AnalysisResult {
        var precision = "approximate"
        var locationType = "unknown"
        var areaDescription = ""
        val nearbyRoutes = mutableListOf<String>()
        val landmarks = mutableListOf<String>()

        // WiFi BSSID analysis
        val bssid = data["wifi_bssid"] as? String
        if (!bssid.isNullOrEmpty()) {
            val bssidPrefix = bssid.take(8)
            val publicNetworks = mapOf(
                "00:1A:2B" to "Starbucks/Cafe Network",
                "00:1C:DF" to "Shopping Mall",
                "00:22:CF" to "Public Transport"
            )

            if (publicNetworks.containsKey(bssidPrefix)) {
                locationType = "commercial"
                areaDescription = "Likely near ${publicNetworks[bssidPrefix]}"
            }
        }

        // IP-based region analysis
        val ip = data["ip_address"] as? String
        if (!ip.isNullOrEmpty()) {
            // NOTE: getIpInfo() needs to be implemented or rely on previously fetched data
            // Assuming simplified logic here
            locationType = "fixed_broadband"
            precision = "200m-1km (broadband)"
            areaDescription += if (areaDescription.isNotEmpty()) ", Broadband" else "Broadband"
        }

        // Get nearby routes using OpenStreetMap
        val lat = data["approx_lat"] as? Double
        val lon = data["approx_lon"] as? Double
        if (lat != null && lon != null) {
            val nearby = getOsmNearby(lat, lon)
            nearbyRoutes.addAll(nearby["roads"]?.take(5) ?: emptyList())
            landmarks.addAll(nearby["amenities"]?.take(5) ?: emptyList())
        }

        return AnalysisResult(precision, locationType, nearbyRoutes, landmarks, areaDescription)
    }

    suspend fun getOsmNearby(lat: Double, lon: Double, radius: Int = 1000): Map<String, List<String>> {
        val query = """
            [out:json];
            (
              way["highway"](around:$radius,$lat,$lon);
              node["amenity"](around:$radius,$lat,$lon);
            );
            out body;
        """.trimIndent()

        return withContext(Dispatchers.IO) {
            try {
                val url = URL("https://overpass-api.de/api/interpreter")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.connectTimeout = 10000
                connection.readTimeout = 10000

                val postData = "data=" + URLEncoder.encode(query, "UTF-8")
                OutputStreamWriter(connection.outputStream).use { it.write(postData) }

                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)
                val elements = json.optJSONArray("elements") ?: return@withContext emptyMap()

                val roads = mutableListOf<String>()
                val amenities = mutableListOf<String>()

                for (i in 0 until elements.length()) {
                    val element = elements.getJSONObject(i)
                    val tags = element.optJSONObject("tags") ?: continue
                    if (tags.has("highway")) {
                        roads.add(tags.optString("name", "Unnamed Road"))
                    } else if (tags.has("amenity")) {
                        amenities.add(tags.optString("name", tags.getString("amenity")))
                    }
                }

                mapOf("roads" to roads, "amenities" to amenities)
            } catch (e: Exception) {
                emptyMap()
            }
        }
    }
}
