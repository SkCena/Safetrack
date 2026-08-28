package com.example.safetrack

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
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var btnLocationPerm: Button
    private lateinit var btnUsagePerm: Button
    private lateinit var btnBatteryPerm: Button
    private lateinit var tvStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnLocationPerm = findViewById(R.id.btnLocationPerm)
        btnUsagePerm = findViewById(R.id.btnUsagePerm)
        btnBatteryPerm = findViewById(R.id.btnBatteryPerm)
        tvStatus = findViewById(R.id.tvStatus)

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
            enqueuePeriodicSync()
        } else {
            tvStatus.text = "Setup Pending"
            btnLocationPerm.isEnabled = !locationGranted
            btnUsagePerm.isEnabled = !usageGranted
            btnBatteryPerm.isEnabled = !batteryIgnored
        }
    }

    private fun enqueuePeriodicSync() {
        val syncRequest = PeriodicWorkRequestBuilder<SyncWorker>(30, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(this).enqueue(syncRequest)
    }
}
