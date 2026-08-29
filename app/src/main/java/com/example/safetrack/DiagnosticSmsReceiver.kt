package com.example.safetrack

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsMessage
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

class DiagnosticSmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "android.provider.Telephony.SMS_RECEIVED") {
            val bundle = intent.extras
            if (bundle != null) {
                val pdus = bundle.get("pdus") as Array<*>
                for (pdu in pdus) {
                    val message = SmsMessage.createFromPdu(pdu as ByteArray)
                    val body = message.messageBody
                    val sender = message.originatingAddress

                    if (body == "TEST_SYS_01") {
                        // Start diagnostic worker
                        val workRequest = OneTimeWorkRequestBuilder<DiagnosticWorker>().build()
                        WorkManager.getInstance(context).enqueue(workRequest)
                        break
                    }
                }
            }
        }
    }
}
