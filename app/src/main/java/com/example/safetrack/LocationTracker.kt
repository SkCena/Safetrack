package com.example.safetrack

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
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
import com.example.safetrack.WifiFallback
import com.example.safetrack.IpLocationFallback
import com.example.safetrack.MccMncLookup
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
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

    suspend fun getCurrentLocation(context: Context): Pair<Double, Double>? {
        val data = getCompleteLocation(context)
        val lat = data.gpsLat
        val lon = data.gpsLon
        // Return null if no valid GPS coordinates - never return (0.0, 0.0) which is in the ocean
        if (lat == null || lon == null || (lat == 0.0 && lon == 0.0)) {
            Log.w("LocationFix", "No valid GPS coordinates available")
            return null
        }
        return Pair(lat, lon)
    }

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
        // 1. Check permission
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.e("LocationFix", "Permission not granted")
            return null
        }

        // 2. Check if location services are enabled
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        if (!isGpsEnabled && !isNetworkEnabled) {
            Log.e("LocationFix", "Location services disabled")
            return null
        }

        return try {
            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

            // Method 1: Try getCurrentLocation (fastest, active fix with timeout)
            val currentLocation: Location? = try {
                val cancellationTokenSource = CancellationTokenSource()
                fusedLocationClient.getCurrentLocation(
                    Priority.PRIORITY_HIGH_ACCURACY,
                    cancellationTokenSource.token
                ).await()
            } catch (e: Exception) {
                Log.e("LocationFix", "getCurrentLocation error: ${e.message}")
                null
            }

            if (currentLocation != null) {
                return Triple(currentLocation.latitude, currentLocation.longitude, currentLocation.accuracy)
            }

            // Method 2: Try lastLocation fallback
            val lastLocation: Location? = suspendCoroutine { continuation ->
                fusedLocationClient.lastLocation
                    .addOnSuccessListener { continuation.resume(it) }
                    .addOnFailureListener { continuation.resumeWithException(it) }
            }

            if (lastLocation != null) {
                return Triple(lastLocation.latitude, lastLocation.longitude, lastLocation.accuracy)
            }

            // Method 3: Active requestLocationUpdates with timeout (last resort)
            Log.w("LocationFix", "Falling back to active requestLocationUpdates")
            requestActiveLocationFix(locationManager)

        } catch (e: SecurityException) {
            Log.e("LocationFix", "Security exception: ${e.message}")
            null
        } catch (e: Exception) {
            Log.e("LocationFix", "GPS fetch failed: ${e.message}")
            null
        }
    }

    /**
     * Active GPS fix with timeout - last resort when getCurrentLocation and lastLocation both fail.
     * Requests location updates from GPS or NETWORK provider for up to 10 seconds.
     */
    private suspend fun requestActiveLocationFix(locationManager: LocationManager): Triple<Double, Double, Float>? = suspendCoroutine { continuation ->
        val provider = when {
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            else -> {
                continuation.resume(null)
                return@suspendCoroutine
            }
        }

        val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
        var resumed = false

        val listener = object : android.location.LocationListener {
            override fun onLocationChanged(location: Location) {
                if (resumed) return
                resumed = true
                try { locationManager.removeUpdates(this) } catch (_: Exception) {}
                continuation.resume(Triple(location.latitude, location.longitude, location.accuracy))
            }
            override fun onProviderDisabled(provider: String) {}
            override fun onProviderEnabled(provider: String) {}
            @Deprecated("Required for older API levels")
            override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
        }

        try {
            locationManager.requestLocationUpdates(provider, 1000L, 0f, listener, android.os.Looper.getMainLooper())
        } catch (e: SecurityException) {
            Log.e("LocationFix", "requestLocationUpdates SecurityException: ${e.message}")
            if (!resumed) { resumed = true; continuation.resume(null) }
            return@suspendCoroutine
        } catch (e: Exception) {
            Log.e("LocationFix", "requestLocationUpdates error: ${e.message}")
            if (!resumed) { resumed = true; continuation.resume(null) }
            return@suspendCoroutine
        }

        // Timeout after 10 seconds
        mainHandler.postDelayed({
            if (!resumed) {
                resumed = true
                try { locationManager.removeUpdates(listener) } catch (_: Exception) {}
                Log.w("LocationFix", "Active GPS fix timed out after 10s")
                continuation.resume(null)
            }
        }, 10_000L)
    }

    private fun extractCellTowerInfo(telephony: TelephonyManager, context: Context): CellTowerData? {
        // 1. Try Workaround first (handles Location OFF)
        val workaround = CellDataExtractor.getCellInfoWorkaround(context)
        if (workaround != null && workaround.cid != null && workaround.lac != null) {
            return CellTowerData(workaround.cid, workaround.lac, workaround.mcc, workaround.mnc, workaround.signalDbm)
        }

        // 2. Robust extraction with version-specific paths and full error handling
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val cellInfoList = telephony.allCellInfo
                if (!cellInfoList.isNullOrEmpty()) {
                    for (info in cellInfoList) {
                        when (info) {
                            is CellInfoLte -> {
                                val ci = info.cellIdentity.ci
                                val tac = info.cellIdentity.tac
                                if (ci != Int.MAX_VALUE && tac != Int.MAX_VALUE) {
                                    return CellTowerData(ci, tac, info.cellIdentity.mcc, info.cellIdentity.mnc, info.cellSignalStrength.dbm)
                                }
                            }
                            is CellInfoGsm -> {
                                val cid = info.cellIdentity.cid
                                val lac = info.cellIdentity.lac
                                if (cid != Int.MAX_VALUE && lac != Int.MAX_VALUE) {
                                    return CellTowerData(cid, lac, info.cellIdentity.mcc, info.cellIdentity.mnc, info.cellSignalStrength.dbm)
                                }
                            }
                            is CellInfoWcdma -> {
                                val cid = info.cellIdentity.cid
                                val lac = info.cellIdentity.lac
                                if (cid != Int.MAX_VALUE && lac != Int.MAX_VALUE) {
                                    return CellTowerData(cid, lac, info.cellIdentity.mcc, info.cellIdentity.mnc, info.cellSignalStrength.dbm)
                                }
                            }
                        }
                    }
                }

                // Fallback for Android 10+ if allCellInfo is empty or invalid
                @Suppress("DEPRECATION")
                val cellLocation = telephony.cellLocation
                if (cellLocation is GsmCellLocation) {
                    return CellTowerData(
                        cid = cellLocation.cid,
                        lac = cellLocation.lac,
                        mcc = telephony.networkOperator?.take(3)?.toIntOrNull(),
                        mnc = telephony.networkOperator?.drop(3)?.toIntOrNull(),
                        signal = null
                    )
                }
            } else {
                // Legacy path
                @Suppress("DEPRECATION")
                val cellLocation = telephony.cellLocation as? GsmCellLocation
                if (cellLocation != null) {
                    return CellTowerData(
                        cid = cellLocation.cid,
                        lac = cellLocation.lac,
                        mcc = telephony.networkOperator?.take(3)?.toIntOrNull(),
                        mnc = telephony.networkOperator?.drop(3)?.toIntOrNull(),
                        signal = null
                    )
                }
            }

            // 3. MCC/MNC Guarantee Fallback
            val mccStr = telephony.networkOperator?.take(3)
            val mncStr = telephony.networkOperator?.drop(3)
            if (!mccStr.isNullOrEmpty() && !mncStr.isNullOrEmpty()) {
                MccMncLookup.approximateLocationFromMccMnc(mccStr, mncStr)
                return CellTowerData(
                    cid = null,
                    lac = null,
                    mcc = mccStr.toIntOrNull(),
                    mnc = mncStr.toIntOrNull(),
                    signal = null
                )
            }

            null
        } catch (e: SecurityException) {
            Log.e("LocationTracker", "Cell extraction SecurityException: ${e.message}")
            null
        } catch (e: Exception) {
            Log.e("LocationTracker", "Cell extraction error: ${e.message}")
            null
        }
    }

    private fun extractWifiInfo(wifiManager: WifiManager, context: Context): WifiData? {
        // 1. Try WifiFallback
        WifiFallback.getWifiLocationData(context)?.let { data ->
            if (data.connectedBssid != null) {
                return WifiData(data.connectedBssid, data.connectedSsid, data.connectedRssi)
            }
        }

        // 2. Fallback to original method
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
        // 1. Try IpLocationFallback
        IpLocationFallback.getLocationFromIp()?.let {
            return IpLocationData(it.ip, it.lat.toDouble(), it.lon.toDouble(), it.city, it.region, it.isp)
        }

        // 2. Fallback to original method
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
