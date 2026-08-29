package com.example.safetrack

object MccMncLookup {

    data class CarrierLocation(
        val country: String,
        val carrier: String,
        val accuracy: String,
        val note: String
    )

    fun approximateLocationFromMccMnc(mcc: String, mnc: String): CarrierLocation {
        val mccToCountry = mapOf(
            "404" to "India",
            "405" to "India",
            "310" to "USA",
            "311" to "USA"
        )

        val carrierInfo = mapOf(
            "404" to mapOf(
                "01" to "Airtel",
                "10" to "Airtel",
                "02" to "AirCell",
                "03" to "Airtel",
                "04" to "IDEA",
                "05" to "Vodafone",
                "06" to "Airtel",
                "07" to "IDEA",
                "08" to "Spice",
                "09" to "Reliance",
                "11" to "Vodafone",
                "12" to "IDEA",
                "13" to "Vodafone",
                "14" to "IDEA",
                "15" to "Vodafone",
                "16" to "Airtel",
                "19" to "Airtel",
                "20" to "Vodafone",
                "21" to "Loop",
                "22" to "IDEA",
                "24" to "IDEA",
                "25" to "Airtel",
                "27" to "Vodafone",
                "28" to "Airtel",
                "29" to "Airtel",
                "30" to "Vodafone",
                "31" to "Airtel",
                "34" to "CellOne",
                "36" to "Reliance",
                "37" to "Airtel",
                "38" to "BSNL",
                "40" to "Airtel",
                "41" to "RPG",
                "42" to "Airtel",
                "43" to "IDEA",
                "44" to "Spice",
                "45" to "Airtel",
                "46" to "Vodafone",
                "48" to "Dishnet",
                "49" to "Airtel",
                "50" to "Reliance",
                "51" to "CellOne",
                "52" to "Reliance",
                "53" to "AirCell",
                "54" to "AirCell",
                "55" to "CellOne",
                "56" to "IDEA",
                "57" to "CellOne",
                "58" to "CellOne",
                "59" to "CellOne",
                "60" to "Vodafone",
                "62" to "CellOne",
                "64" to "CellOne",
                "66" to "CellOne",
                "67" to "Reliance",
                "68" to "DOLPHIN",
                "69" to "DOLPHIN",
                "70" to "Airtel",
                "71" to "CellOne",
                "72" to "CellOne",
                "73" to "CellOne",
                "74" to "CellOne",
                "75" to "CellOne",
                "76" to "CellOne",
                "77" to "Vodafone",
                "78" to "IDEA",
                "79" to "CellOne",
                "80" to "BSNL",
                "81" to "CellOne",
                "82" to "IDEA",
                "83" to "Reliance",
                "84" to "Vodafone",
                "85" to "Reliance",
                "86" to "Vodafone",
                "87" to "IDEA",
                "88" to "Vodafone",
                "89" to "IDEA",
                "90" to "Airtel",
                "91" to "Airtel",
                "92" to "Airtel",
                "93" to "Airtel",
                "94" to "Airtel",
                "95" to "Airtel",
                "96" to "Airtel",
                "97" to "Airtel",
                "98" to "Airtel"
            )
        )

        val country = mccToCountry[mcc] ?: "Unknown"
        val carrier = carrierInfo[mcc]?.get(mnc) ?: "Unknown"

        return CarrierLocation(
            country = country,
            carrier = carrier,
            accuracy = "city-level",
            note = "Location services were OFF, using network identification only"
        )
    }
}
