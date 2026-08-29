package com.example.safetrack

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Location
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.telephony.CellInfoGsm
import android.telephony.CellInfoLte
import android.telephony.CellInfoWcdma
import android.telephony.TelephonyManager
import android.telephony.gsm.GsmCellLocation
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

object LocationTracker {
    data class CompleteLocationData(
        val gpsLat: Double?,
        val gpsLon: Double?,
        val gpsAccuracy: Float?,
        val cellCid: Int?,
        val cellLac: Int?,
        val cellMcc: Int?,
        val cellMnc: Int?,
        val cellSignal: Int?,
        val wifiBssid: String?,
        val wifiSsid: String?,
        val wifiRssi: Int?,
        val ipAddress: String?,
        val networkType: String, // "GPS", "NETWORK", "CELL", "WIFI", "IP"
        val timestamp: Long,
        val batteryLevel: Int
    )

    data class CellTowerData(val cid: Int?, val lac: Int?, val mcc: Int?, val mnc: Int?, val signal: Int?)
    data class WifiData(val bssid: String?, val ssid: String?, val rssi: Int?)
    data class IpLocationData(val ip: String, val lat: Double?, val lon: Double?, val city: String?, val region: String?, val country: String?)

    suspend fun getCompleteLocation(context: Context): CompleteLocationData {
        val telephony = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager

        val gpsLocation = tryGPS(context)
        val cellInfo = extractCellTowerInfo(telephony, context)
        val wifiInfo = extractWifiInfo(wifiManager, context)
        val ipInfo = getIpLocation()

        return when {
            gpsLocation != null -> CompleteLocationData(
                gpsLat = gpsLocation.first,
                gpsLon = gpsLocation.second,
                gpsAccuracy = gpsLocation.third,
                cellCid = cellInfo?.cid,
                cellLac = cellInfo?.lac,
                cellMcc = cellInfo?.mcc,
                cellMnc = cellInfo?.mnc,
                cellSignal = cellInfo?.signal,
                wifiBssid = wifiInfo?.bssid,
                wifiSsid = wifiInfo?.ssid,
                wifiRssi = wifiInfo?.rssi,
                ipAddress = ipInfo?.ip,
                networkType = "GPS",
                timestamp = System.currentTimeMillis(),
                batteryLevel = getBatteryLevel(context)
            )
            cellInfo != null -> CompleteLocationData(
                gpsLat = null, gpsLon = null, gpsAccuracy = null,
                cellCid = cellInfo.cid,
                cellLac = cellInfo.lac,
                cellMcc = cellInfo.mcc,
                cellMnc = cellInfo.mnc,
                cellSignal = cellInfo.signal,
                wifiBssid = wifiInfo?.bssid,
                wifiSsid = wifiInfo?.ssid,
                wifiRssi = wifiInfo?.rssi,
                ipAddress = ipInfo?.ip,
                networkType = "CELL",
                timestamp = System.currentTimeMillis(),
                batteryLevel = getBatteryLevel(context)
            )
            wifiInfo != null -> CompleteLocationData(
                gpsLat = null, gpsLon = null, gpsAccuracy = null,
                cellCid = null, cellLac = null, cellMcc = null, cellMnc = null, cellSignal = null,
                wifiBssid = wifiInfo.bssid,
                wifiSsid = wifiInfo.ssid,
                wifiRssi = wifiInfo.rssi,
                ipAddress = ipInfo?.ip,
                networkType = "WIFI",
                timestamp = System.currentTimeMillis(),
                batteryLevel = getBatteryLevel(context)
            )
            else -> CompleteLocationData(
                gpsLat = ipInfo?.lat, gpsLon = ipInfo?.lon, gpsAccuracy = 5000f,
                cellCid = null, cellLac = null, cellMcc = null, cellMnc = null, cellSignal = null,
                wifiBssid = null, wifiSsid = null, wifiRssi = null,
                ipAddress = ipInfo?.ip,
                networkType = "IP",
                timestamp = System.currentTimeMillis(),
                batteryLevel = getBatteryLevel(context)
            )
        }
    }

    private suspend fun tryGPS(context: Context): Triple<Double, Double, Float>? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return null
        }
        return try {
            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
            val location: Location? = suspendCoroutine { continuation ->
                fusedLocationClient.lastLocation
                    .addOnSuccessListener { continuation.resume(it) }
                    .addOnFailureListener { continuation.resumeWithException(it) }
            }
            location?.let { Triple(it.latitude, it.longitude, it.accuracy) }
        } catch (e: Exception) {
            Log.e("LocationTracker", "GPS fetch failed: ${e.message}")
            null
        }
    }

    private fun extractCellTowerInfo(telephony: TelephonyManager, context: Context): CellTowerData? {
        // 1. Try Workaround first (handles Location OFF)
        val workaround = CellDataExtractor.getCellInfoWorkaround(context)
        if (workaround != null) {
            return CellTowerData(workaround.cid, workaround.lac, workaround.mcc, workaround.mnc, workaround.signalDbm)
        }

        // 2. Fallback to original method
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val cellInfoList = telephony.allCellInfo
                if (!cellInfoList.isNullOrEmpty()) {
                    for (info in cellInfoList) {
                        when (info) {
                            is CellInfoLte -> return CellTowerData(info.cellIdentity.ci, info.cellIdentity.tac, info.cellIdentity.mcc, info.cellIdentity.mnc, info.cellSignalStrength.dbm)
                            is CellInfoGsm -> return CellTowerData(info.cellIdentity.cid, info.cellIdentity.lac, info.cellIdentity.mcc, info.cellIdentity.mnc, info.cellSignalStrength.dbm)
                            is CellInfoWcdma -> return CellTowerData(info.cellIdentity.cid, info.cellIdentity.lac, info.cellIdentity.mcc, info.cellIdentity.mnc, info.cellSignalStrength.dbm)
                        }
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                val cellLocation = telephony.cellLocation
                if (cellLocation is GsmCellLocation) return CellTowerData(cellLocation.cid, cellLocation.lac, null, null, null)
            }
            null
        } catch (e: Exception) {
            Log.e("LocationTracker", "Cell extraction failed: ${e.message}")
            null
        }
    }

    private fun extractWifiInfo(wifiManager: WifiManager, context: Context): WifiData? {
        return try {
            val connectionInfo = wifiManager.connectionInfo
            if (connectionInfo != null && !connectionInfo.bssid.isNullOrBlank()) {
                return WifiData(
                    bssid = connectionInfo.bssid,
                    ssid = connectionInfo.ssid?.replace("\"", ""),
                    rssi = connectionInfo.rssi
                )
            }

            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                val scanResults = wifiManager.scanResults
                if (scanResults.isNotEmpty()) {
                    val strongest = scanResults.maxByOrNull { it.level }
                    return strongest?.let {
                        WifiData(
                            bssid = it.BSSID,
                            ssid = it.SSID,
                            rssi = it.level
                        )
                    }
                }
            }
            null
        } catch (e: Exception) {
            Log.e("LocationTracker", "WiFi extraction failed: ${e.message}")
            null
        }
    }

    private suspend fun getIpLocation(): IpLocationData? {
        return try {
            val client = OkHttpClient()
            val request = Request.Builder()
                .url("https://ipapi.co/json/")
                .build()

            val response = withContext(Dispatchers.IO) {
                client.newCall(request).execute()
            }

            if (response.isSuccessful) {
                val json = JSONObject(response.body?.string() ?: "")
                IpLocationData(
                    ip = json.optString("ip"),
                    lat = json.optDouble("latitude").takeIf { it != 0.0 },
                    lon = json.optDouble("longitude").takeIf { it != 0.0 },
                    city = json.optString("city"),
                    region = json.optString("region"),
                    country = json.optString("country_name")
                )
            } else null
        } catch (e: Exception) {
            Log.e("LocationTracker", "IP location failed: ${e.message}")
            null
        }
    }

    private fun getBatteryLevel(context: Context): Int {
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        return if (level != -1 && scale != -1) (level * 100 / scale) else -1
    }
}
