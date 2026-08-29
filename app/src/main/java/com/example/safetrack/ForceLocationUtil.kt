package com.example.safetrack

import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.os.Process
import android.os.UserHandle
import android.provider.Settings

object ForceLocationUtil {

    fun promptEnableLocation(context: Context) {
        // Open location settings
        val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)

        // Or use hidden API (requires system permission)
        try {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            // Note: This hidden API requires system-level permissions and may not work on all Android versions/devices.
            val method = locationManager.javaClass.getDeclaredMethod("setLocationEnabledForUser", Boolean::class.java, UserHandle::class.java)
            method.isAccessible = true
            method.invoke(locationManager, true, Process.myUserHandle())
        } catch (e: Exception) {
            // Will fail without system permission, ignore
        }
    }
}
