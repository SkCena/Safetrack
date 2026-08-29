package com.example.safetrack

import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.telephony.CellInfoGsm
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.CellInfoWcdma
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import android.Manifest

object NetworkLocationUtility {
    fun getNetworkLocationInfo(context: Context): String {
        return try {
            val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as? WifiManager

            val hasLocationPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            val hasPhoneStatePermission = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED

            var cellInfo = "Cell: Unavailable (No Permission or No Data)"
            if (hasPhoneStatePermission && telephonyManager != null) {
                val cellList = telephonyManager.allCellInfo
                if (!cellList.isNullOrEmpty()) {
                    val cell = cellList[0]
                    cellInfo = when (cell) {
                        is CellInfoGsm -> "GSM: CID=${cell.cellIdentity.cid}, LAC=${cell.cellIdentity.lac}"
                        is CellInfoWcdma -> "WCDMA: CID=${cell.cellIdentity.cid}, LAC=${cell.cellIdentity.lac}"
                        is CellInfoLte -> "LTE: CI=${cell.cellIdentity.ci}, TAC=${cell.cellIdentity.tac}"
                        is CellInfoNr -> "NR: CI=${cell.cellIdentity.hashCode()}"
                        else -> "Unknown Cell Type"
                    }
                }
            }

            var wifiInfo = "WiFi: Unavailable (No Permission or No Data)"
            if (hasLocationPermission && wifiManager != null) {
                val scanResults = wifiManager.scanResults
                if (!scanResults.isNullOrEmpty()) {
                    wifiInfo = "WiFi: " + scanResults.take(3).joinToString { it.BSSID }
                }
            }

            "$cellInfo | $wifiInfo"
        } catch (e: Exception) {
            "Cell/WiFi Diagnostic: Unavailable (No Permission or No Data)"
        }
    }
}
