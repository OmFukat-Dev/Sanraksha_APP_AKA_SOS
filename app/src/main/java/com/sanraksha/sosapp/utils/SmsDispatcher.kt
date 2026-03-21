package com.sanraksha.sosapp.utils

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SmsDispatcher(context: Context) {
    private val appContext = context.applicationContext
    private val smsHelper = SMSHelper(appContext)
    private val outbox = SmsOutbox(appContext)

    fun sendBulk(phoneNumbers: List<String>, message: String): Int {
        var success = 0
        phoneNumbers.forEach { phone ->
            if (smsHelper.sendSMS(phone, message)) {
                success++
            } else {
                outbox.enqueue(phone, message)
            }
        }
        return success
    }

    suspend fun flushOutbox(maxPerRun: Int = 10): Int {
        return withContext(Dispatchers.IO) {
            outbox.flush(smsHelper, maxPerRun)
        }
    }
}
