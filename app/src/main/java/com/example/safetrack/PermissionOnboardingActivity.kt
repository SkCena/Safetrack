package com.example.safetrack

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class PermissionOnboardingActivity : AppCompatActivity() {

    private val requiredPermissions = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.ACCESS_WIFI_STATE,
        Manifest.permission.RECEIVE_SMS
    )

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { updateUi() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_permission_onboarding)

        findViewById<android.widget.Button>(R.id.btnStartService).setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }

        updateUi()
    }

    override fun onResume() {
        super.onResume()
        updateUi()
    }

    private fun updateUi() {
        val container = findViewById<LinearLayout>(R.id.containerPermissions)
        container.removeAllViews()

        var allGranted = true

        // Permissions Check
        for (perm in requiredPermissions) {
            val granted = ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED
            addPermissionRow(container, prettyName(perm), granted) {
                ActivityCompat.requestPermissions(this, arrayOf(perm), 0)
            }
            if (!granted) allGranted = false
        }

        // Background Location (Android 10+) - shown only on Q+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val bgGranted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            addPermissionRow(container, "Background Location", bgGranted) {
                PermissionUtils.requestBackgroundLocation(this)
            }
            if (!bgGranted) allGranted = false
        }

        // Usage Stats Check
        val hasUsageStats = hasUsageStatsPermission()
        addPermissionRow(container, "App Usage Access", hasUsageStats) {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
        if (!hasUsageStats) allGranted = false

        // Battery Optimization Check
        val hasIgnoreBattery = hasIgnoreBatteryPermission()
        addPermissionRow(container, "Battery Optimization", hasIgnoreBattery) {
            startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
        }
        if (!hasIgnoreBattery) allGranted = false

        // Location Services Check
        val isLocationEnabled = isLocationEnabled()
        addPermissionRow(container, "Location Services", isLocationEnabled) {
            ForceLocationUtil.promptEnableLocation(this)
        }
        if (!isLocationEnabled) allGranted = false

        // Auto-Start (OEM-specific) - critical for MIUI/ColorOS/FuntouchOS/EMUI/OxygenOS
        if (AutoStartUtils.isRestrictiveManufacturer()) {
            addPermissionRow(container, "Auto-Start (${AutoStartUtils.getManufacturer()})", true) {
                AutoStartUtils.getAutoStartIntent(this)?.let { intent ->
                    try { startActivity(intent) } catch (_: Exception) {}
                } ?: run {
                    // Fallback to battery settings if OEM screen is not reachable
                    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:$packageName")
                    })
                }
            }
        }

        findViewById<android.widget.Button>(R.id.btnStartService).isEnabled = allGranted
    }

    private fun prettyName(perm: String): String = when (perm) {
        Manifest.permission.ACCESS_FINE_LOCATION -> "Precise Location"
        Manifest.permission.READ_PHONE_STATE -> "Phone State"
        Manifest.permission.ACCESS_WIFI_STATE -> "Wi-Fi State"
        Manifest.permission.RECEIVE_SMS -> "Receive SMS"
        else -> perm.substringAfterLast('.')
    }

    private fun isLocationEnabled(): Boolean {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
               locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    /**
     * Add a permission row as a colored card with status badge.
     * Visually distinguishes granted (green) vs pending (orange) vs unclickable (gray).
     */
    private fun addPermissionRow(container: LinearLayout, name: String, granted: Boolean, action: () -> Unit) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val pad = (resources.displayMetrics.density * 14).toInt()
            setPadding(pad, pad, pad, pad)
            val bg = GradientDrawable().apply {
                cornerRadius = resources.displayMetrics.density * 12
                setColor(if (granted) Color.parseColor("#1B3328") else Color.parseColor("#33241A"))
                setStroke((resources.displayMetrics.density * 1).toInt(),
                    if (granted) Color.parseColor("#00E676") else Color.parseColor("#FFA726"))
            }
            background = bg
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.bottomMargin = (resources.displayMetrics.density * 8).toInt()
            layoutParams = lp
        }

        val indicator = TextView(this).apply {
            text = if (granted) "✓" else "!"
            setTextColor(if (granted) Color.parseColor("#00E676") else Color.parseColor("#FFA726"))
            textSize = 18f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            val lp = LinearLayout.LayoutParams(
                (resources.displayMetrics.density * 28).toInt(),
                (resources.displayMetrics.density * 28).toInt()
            )
            lp.marginEnd = (resources.displayMetrics.density * 12).toInt()
            layoutParams = lp
            gravity = Gravity.CENTER
        }
        row.addView(indicator)

        val label = TextView(this).apply {
            text = name
            setTextColor(Color.parseColor("#F5F7FA"))
            textSize = 15f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        row.addView(label)

        val statusBadge = TextView(this).apply {
            text = if (granted) "Granted" else "Tap to Grant"
            setTextColor(if (granted) Color.parseColor("#00E676") else Color.parseColor("#FFA726"))
            textSize = 12f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        row.addView(statusBadge)

        row.setOnClickListener { action() }
        container.addView(row)
    }

    private fun hasUsageStatsPermission(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName)
        } else {
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun hasIgnoreBatteryPermission(): Boolean {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(packageName)
    }
}
