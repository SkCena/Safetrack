package com.example.safetrack

import android.content.Context
import android.net.wifi.WifiManager
import android.telephony.TelephonyManager
import android.telephony.CellInfoGsm
import android.telephony.CellInfoWcdma
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.os.Build

object NetworkLocationUtility {
    fun getNetworkLocationInfo(context: Context): String {
        val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager

        var cellInfo = "Cell: Unknown"
        try {
            val cellList = telephonyManager.allCellInfo
            if (!cellList.isNullOrEmpty()) {
                val cell = cellList[0]
                cellInfo = when (cell) {
                    is CellInfoGsm -> "GSM: CID=${cell.cellIdentity.cid}, LAC=${cell.cellIdentity.lac}"
                    is CellInfoWcdma -> "WCDMA: CID=${cell.cellIdentity.cid}, LAC=${cell.cellIdentity.lac}"
                    is CellInfoLte -> "LTE: CI=${cell.cellIdentity.ci}, TAC=${cell.cellIdentity.tac}"
                    is CellInfoNr -> "NR: CI=${cell.cellIdentity.nci}"
                    else -> "Unknown"
                }
            }
        } catch (e: SecurityException) {
            cellInfo = "Cell: No Permission"
        }

        val wifiInfo = try {
            val scanResults = wifiManager.scanResults
            "WiFi: " + scanResults.take(3).joinToString { it.BSSID }
        } catch (e: SecurityException) {
            "WiFi: No Permission"
        }

        return "$cellInfo | $wifiInfo"
    }
}
