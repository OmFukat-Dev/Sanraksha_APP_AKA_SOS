package com.sanraksha.sosapp.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.SmsManager
import androidx.core.content.ContextCompat

class SMSHelper(private val context: Context) {

    fun sendSMS(phoneNumber: String, message: String): Boolean {
        return try {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.SEND_SMS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return false
            }

            val smsManager = context.getSystemService(SmsManager::class.java)
            smsManager.sendTextMessage(phoneNumber, null, message, null, null)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun sendBulkSMS(phoneNumbers: List<String>, message: String): Int {
        var successCount = 0
        phoneNumbers.forEach { phone ->
            if (sendSMS(phone, message)) {
                successCount++
            }
        }
        return successCount
    }
}