package com.example.safetrack

import android.Manifest
import android.app.AppOpsManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Button
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

        findViewById<Button>(R.id.btnStartService).setOnClickListener {
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
            addPermissionRow(container, perm, granted) {
                ActivityCompat.requestPermissions(this, arrayOf(perm), 0)
            }
            if (!granted) allGranted = false
        }

        // Usage Stats Check
        val hasUsageStats = hasUsageStatsPermission()
        addPermissionRow(container, "Usage Stats", hasUsageStats) {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
        if (!hasUsageStats) allGranted = false

        // Battery Optimization Check
        val hasIgnoreBattery = hasIgnoreBatteryPermission()
        addPermissionRow(container, "Ignore Battery Opt", hasIgnoreBattery) {
            startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, android.net.Uri.parse("package:$packageName")))
        }
        if (!hasIgnoreBattery) allGranted = false

        findViewById<Button>(R.id.btnStartService).isEnabled = allGranted
    }

    private fun addPermissionRow(container: LinearLayout, name: String, granted: Boolean, action: () -> Unit) {
        val tv = TextView(this)
        tv.text = "$name: ${if (granted) "✅" else "❌ Click to Grant"}"
        tv.setPadding(0, 16, 0, 16)
        if (!granted) tv.setOnClickListener { action() }
        container.addView(tv)
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
