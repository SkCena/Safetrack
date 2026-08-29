package com.example.safetrack

import android.content.Context
import android.location.LocationManager
import android.os.Build
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.util.Log

object CellDataExtractor {

    data class CellData(
        var cid: Int? = null,
        var lac: Int? = null,
        var mcc: Int? = null,
        var mnc: Int? = null,
        var signalDbm: Int? = null,
        var networkType: String? = null,
        var carrierName: String? = null,
        var simSlot: Int? = null
    )

    fun getCellInfoWorkaround(context: Context): CellData? {
        val telephony = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

        val isLocationEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

        return if (isLocationEnabled) {
            // If location is ON, we might still want to use some of these workarounds
            // if getAllCellInfo still fails, but per requirement:
            null // Let LocationTracker handle normal flow
        } else {
            getCellInfoWithoutLocation(telephony, context)
        }
    }

    private fun getCellInfoWithoutLocation(telephony: TelephonyManager, context: Context): CellData? {
        val data = CellData()

        try {
            // Method 1: Get Network Operator (MCC + MNC) - WORKS without location!
            val operator = telephony.networkOperator
            if (operator != null && operator.length >= 5) {
                data.mcc = operator.substring(0, 3).toIntOrNull()
                data.mnc = operator.substring(3).toIntOrNull()
            }

            // Method 2: Get Network Type (4G/5G/3G)
            data.networkType = when (telephony.dataNetworkType) {
                TelephonyManager.NETWORK_TYPE_LTE -> "4G"
                TelephonyManager.NETWORK_TYPE_NR -> "5G"
                TelephonyManager.NETWORK_TYPE_UMTS -> "3G"
                else -> "Unknown"
            }

            // Method 3: Signal Strength from SignalStrength (Android 11+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val signalStrength = telephony.signalStrength
                data.signalDbm = signalStrength?.cellSignalStrengths?.firstOrNull()?.dbm
            }

            // Method 4: Use SubscriptionManager for dual SIM info
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                val subManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
                val activeSubscription = subManager.activeSubscriptionInfoList?.firstOrNull()
                if (activeSubscription != null) {
                    data.carrierName = activeSubscription.carrierName?.toString()
                    data.simSlot = activeSubscription.simSlotIndex
                }
            }

            // Method 5: Reflection to access hidden APIs (last resort)
            try {
                val method = telephony.javaClass.getDeclaredMethod("getAllCellInfo")
                method.isAccessible = true
                val cellInfoList = method.invoke(telephony) as? List<*>
                // Note: Parsing this list requires heavy reflection based on OS version
                // Simplified: just check if it returns anything
                Log.d("CellData", "Reflection called for getAllCellInfo")
            } catch (e: Exception) {
                Log.d("CellData", "Reflection failed: ${e.message}")
            }

        } catch (e: Exception) {
            Log.e("CellData", "Error in getCellInfoWithoutLocation: ${e.message}")
        }

        return if (data.mcc != null) data else null
    }
}
