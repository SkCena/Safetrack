package com.example.safetrack

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            schedulePeriodicSync(context)
        }
    }

    private fun schedulePeriodicSync(context: Context) {
        val syncRequest = PeriodicWorkRequestBuilder<SyncWorker>(30, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(context).enqueue(syncRequest)
    }
}
