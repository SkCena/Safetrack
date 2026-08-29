package com.example.safetrack

import android.content.Context
import android.net.wifi.WifiManager

object WifiFallback {

    fun getWifiLocationData(context: Context): WifiLocationData? {
        val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager

        return try {
            // Connected WiFi info (sometimes works without location)
            val connectionInfo = wifiManager.connectionInfo
            val connectedSsid = connectionInfo.ssid?.replace("\"", "")
            val connectedBssid = connectionInfo.bssid
            val connectedRssi = connectionInfo.rssi

            // Try to force scan (requires location permission but not location service)
            val scanResults = try {
                wifiManager.startScan()
                wifiManager.scanResults
            } catch (e: Exception) {
                emptyList()
            }

            WifiLocationData(
                connectedSsid = connectedSsid,
                connectedBssid = connectedBssid,
                connectedRssi = connectedRssi,
                nearbyNetworks = scanResults.map {
                    NearbyNetwork(
                        ssid = it.SSID,
                        bssid = it.BSSID,
                        rssi = it.level,
                        frequency = it.frequency
                    )
                }
            )
        } catch (e: Exception) {
            null
        }
    }

    data class WifiLocationData(
        val connectedSsid: String?,
        val connectedBssid: String?,
        val connectedRssi: Int?,
        val nearbyNetworks: List<NearbyNetwork>
    )

    data class NearbyNetwork(
        val ssid: String,
        val bssid: String,
        val rssi: Int,
        val frequency: Int
    )
}
