package com.example.safetrack

/**
 * Modern data model to handle both 5G NR (Long NCI) and 4G LTE (Int CI) correctly.
 */
data class CellInfoData(
    val networkType: String, // "5G NR" or "4G LTE" or "Unknown"
    val mcc: String?,
    val mnc: String?,
    val tac: Int?,
    val pci: Int?,

    // NR specific
    val nci: Long? = null,
    val nrArfcn: Int? = null,
    val ssRsrp: Int? = null,
    val csiRsrp: Int? = null,
    val csiSinr: Int? = null,

    // LTE specific
    val lteCi: Int? = null,
    val earfcn: Int? = null,
    val rsrp: Int? = null,
    val rsrq: Int? = null,
    val rssi: Int? = null,

    val isRegistered: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)
