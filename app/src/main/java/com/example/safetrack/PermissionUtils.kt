package com.example.safetrack

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * Centralized runtime permission checks and requests.
 */
object PermissionUtils {

    /** Permissions required for SafeTrack core features */
    val REQUIRED_PERMISSIONS: Array<String> = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.CAMERA,
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.RECEIVE_SMS
    )

    /** Permissions that require Android 10+ separate request flow */
    val BACKGROUND_LOCATION: Array<String> = arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION)

    const val REQUEST_BG_LOCATION = 1001

    /**
     * Check all required permissions at runtime (not just Manifest declaration).
     * Returns true only if every permission is currently granted.
     */
    fun checkAllPermissionsGranted(context: Context): Boolean {
        return REQUIRED_PERMISSIONS.all { perm ->
            ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
        } && isBackgroundLocationGranted(context)
    }

    /**
     * Background location is a separate runtime grant on Android 10+.
     * On older versions ACCESS_BACKGROUND_LOCATION is implicitly granted with FINE/COARSE.
     */
    fun isBackgroundLocationGranted(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            // On pre-Q, ACCESS_FINE_LOCATION already grants background capability
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Request background location permission separately (Android 10+).
     * MUST be called AFTER ACCESS_FINE_LOCATION is already granted - Google Play policy
     * requires foreground location first, then ask for background in a separate prompt.
     */
    fun requestBackgroundLocation(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Only ask if we already have foreground location
            val foregroundGranted = ContextCompat.checkSelfPermission(
                activity, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            if (foregroundGranted && !isBackgroundLocationGranted(activity)) {
                ActivityCompat.requestPermissions(
                    activity,
                    BACKGROUND_LOCATION,
                    REQUEST_BG_LOCATION
                )
            }
        }
    }
}
