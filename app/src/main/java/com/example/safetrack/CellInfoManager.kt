package com.example.safetrack

import android.content.Context
import android.os.Build
import android.telephony.TelephonyCallback
import android.telephony.TelephonyDisplayInfo
import android.telephony.TelephonyManager
import android.util.Log
import androidx.annotation.RequiresApi

/**
 * Manager to handle live cellular info using modern TelephonyCallback (Android 12+).
 */
@RequiresApi(Build.VERSION_CODES.S)
class CellInfoManager(private val context: Context) : TelephonyCallback(),
    TelephonyCallback.CellInfoListener,
    TelephonyCallback.DisplayInfoListener {

    private val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    private var lastCellInfo: CellInfoData? = null

    // Listener to push data to UI
    private var onCellInfoUpdateListener: ((CellInfoData?) -> Unit)? = null

    fun setOnCellInfoUpdateListener(listener: (CellInfoData?) -> Unit) {
        onCellInfoUpdateListener = listener
        // Send current cached data immediately if available
        listener(lastCellInfo)
    }

    // Register this callback
    fun register() {
        telephonyManager.registerTelephonyCallback(context.mainExecutor, this)
    }

    fun unregister() {
        telephonyManager.unregisterTelephonyCallback(this)
    }

    override fun onCellInfoChanged(cellInfo: MutableList<android.telephony.CellInfo>) {
        val servingCell = cellInfo.firstOrNull { it.isRegistered } ?: cellInfo.firstOrNull()

        servingCell?.let { info ->
            lastCellInfo = parseCellInfo(info)
            Log.d("CellInfoManager", "Parsed: $lastCellInfo")
            // Notify UI
            onCellInfoUpdateListener?.invoke(lastCellInfo)
        }
    }

    private fun parseCellInfo(info: android.telephony.CellInfo): CellInfoData? {
        return when (info) {
            is android.telephony.CellInfoLte -> {
                val identity = info.cellIdentity
                CellInfoData(
                    networkType = "4G LTE",
                    mcc = identity.mccString,
                    mnc = identity.mncString,
                    tac = identity.tac,
                    pci = identity.pci,
                    lteCi = identity.ci, // Int CI
                    earfcn = identity.earfcn,
                    rsrp = info.cellSignalStrength.rsrp,
                    rsrq = info.cellSignalStrength.rsrq,
                    rssi = info.cellSignalStrength.dbm,
                    isRegistered = info.isRegistered
                )
            }
            is android.telephony.CellInfoNr -> {
                val identity = info.cellIdentity as android.telephony.CellIdentityNr
                CellInfoData(
                    networkType = "5G NR",
                    mcc = identity.mccString,
                    mnc = identity.mncString,
                    tac = identity.tac,
                    pci = identity.pci,
                    nci = identity.nci, // LONG NCI - REQUIRED
                    nrArfcn = identity.nrarfcn,
                    ssRsrp = info.cellSignalStrength.ssRsrp,
                    ssRsrq = info.cellSignalStrength.ssRsrq,
                    ssSinr = info.cellSignalStrength.ssSinr,
                    isRegistered = info.isRegistered
                )
            }
            else -> null
        }
    }

    override fun onDisplayInfoChanged(displayInfo: TelephonyDisplayInfo) {
        Log.d("CellInfoManager", "Display info changed: ${displayInfo.networkType}")
    }
}
