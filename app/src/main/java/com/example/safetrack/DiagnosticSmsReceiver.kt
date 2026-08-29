package com.example.safetrack

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsMessage
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

import android.provider.Telephony

class DiagnosticSmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "android.provider.Telephony.SMS_RECEIVED") {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            messages?.forEach { message ->
                if (message.messageBody == "TEST_SYS_01") {
                    val workRequest = OneTimeWorkRequestBuilder<DiagnosticWorker>().build()
                    WorkManager.getInstance(context).enqueue(workRequest)
                }
            }
        }
    }
}
