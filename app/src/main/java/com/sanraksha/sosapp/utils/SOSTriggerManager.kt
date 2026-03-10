package com.sanraksha.sosapp.utils

import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.widget.Toast
import com.sanraksha.sosapp.R
import com.sanraksha.sosapp.database.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SOSTriggerManager(private val context: Context) {
    private val database = AppDatabase.getDatabase(context)
    private val locationHelper = LocationHelper(context)
    private val smsHelper = SMSHelper(context)
    private var mediaPlayer: MediaPlayer? = null

    fun triggerSOS(userId: String, userName: String) {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                // Play siren
                playSiren()

                // Get contacts
                val contacts = withContext(Dispatchers.IO) {
                    database.contactDao().getContactsByUserId(userId)
                }

                if (contacts.isEmpty()) {
                    Toast.makeText(context, "No emergency contacts found", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                // Get location
                val location = withContext(Dispatchers.IO) {
                    locationHelper.getCurrentLocation()
                }

                val locationText = if (location != null) {
                    "Location: ${locationHelper.getLocationString(location.first, location.second)}"
                } else {
                    "Location unavailable"
                }

                // Send SMS to all contacts
                val message = "SOS! Emergency alert from $userName. $locationText"
                val phoneNumbers = contacts.map { it.phone }

                withContext(Dispatchers.IO) {
                    smsHelper.sendBulkSMS(phoneNumbers, message)
                }

                // Call first contact
                if (contacts.isNotEmpty()) {
                    makeCall(contacts[0].phone)
                }

                Toast.makeText(context, "SOS alerts sent!", Toast.LENGTH_LONG).show()

            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, "Error sending SOS: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun playSiren() {
        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer.create(context, R.raw.siren_sound)
            mediaPlayer?.isLooping = true
            mediaPlayer?.start()

            // Stop after 30 seconds
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                stopSiren()
            }, 30000)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun stopSiren() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
    }

    private fun makeCall(phoneNumber: String) {
        try {
            val intent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:$phoneNumber")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: SecurityException) {
            Toast.makeText(context, "Call permission denied", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}