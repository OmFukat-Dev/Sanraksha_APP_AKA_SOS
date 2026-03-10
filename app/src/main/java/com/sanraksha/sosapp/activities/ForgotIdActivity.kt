package com.sanraksha.sosapp.activities

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.sanraksha.sosapp.R
import com.sanraksha.sosapp.database.AppDatabase
import com.sanraksha.sosapp.utils.SMSHelper
import com.sanraksha.sosapp.utils.ThemeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

class ForgotIdActivity : AppCompatActivity() {
    private lateinit var etPhone: EditText
    private lateinit var etOtp: EditText
    private lateinit var btnSendOtp: Button
    private lateinit var btnVerify: Button
    private lateinit var tvStatus: TextView

    private lateinit var database: AppDatabase
    private lateinit var smsHelper: SMSHelper

    private var pendingPhone: String? = null
    private var pendingUniqueId: String? = null
    private var pendingOtp: String? = null
    private var pendingOtpTs: Long = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeUtils.applySavedTheme(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_forgot_id)

        database = AppDatabase.getDatabase(this)
        smsHelper = SMSHelper(this)

        etPhone = findViewById(R.id.etRecoveryPhone)
        etOtp = findViewById(R.id.etRecoveryOtp)
        btnSendOtp = findViewById(R.id.btnSendOtp)
        btnVerify = findViewById(R.id.btnVerifyOtp)
        tvStatus = findViewById(R.id.tvRecoveryStatus)

        btnSendOtp.setOnClickListener { sendOtp() }
        btnVerify.setOnClickListener { verifyOtp() }
    }

    private fun sendOtp() {
        val phone = etPhone.text.toString().trim()
        if (phone.length != 10) {
            Toast.makeText(this, "Please enter a valid 10-digit phone", Toast.LENGTH_SHORT).show()
            return
        }

        if (!hasSmsPermission()) {
            pendingPhone = phone
            requestSmsPermission()
            return
        }

        lifecycleScope.launch {
            val user = withContext(Dispatchers.IO) {
                database.userDao().getUserByPhone(phone)
            }
            if (user == null) {
                tvStatus.text = "Phone number not found"
                return@launch
            }

            pendingPhone = phone
            pendingUniqueId = user.uniqueId
            pendingOtp = Random.nextInt(100000, 999999).toString()
            pendingOtpTs = System.currentTimeMillis()

            val otpSms = "Sanraksha OTP for ID recovery: $pendingOtp (valid for 5 minutes)."
            val otpSent = smsHelper.sendSMS(phone, otpSms)

            if (otpSent) {
                tvStatus.text = "OTP sent to +91-$phone"
                etOtp.isEnabled = true
                btnVerify.isEnabled = true
            } else {
                // Fallback requested by you: send unique id directly if OTP SMS fails.
                sendUniqueIdFallback(phone, user.uniqueId)
            }
        }
    }

    private fun verifyOtp() {
        val otpInput = etOtp.text.toString().trim()
        val otpValid = pendingOtp != null &&
                otpInput == pendingOtp &&
                (System.currentTimeMillis() - pendingOtpTs) <= 5 * 60 * 1000

        if (!otpValid) {
            Toast.makeText(this, "Invalid or expired OTP", Toast.LENGTH_LONG).show()
            return
        }

        val phone = pendingPhone ?: return
        val uniqueId = pendingUniqueId ?: return
        val idMessage = "Your Sanraksha Alert Unique ID is: $uniqueId"
        val sent = smsHelper.sendSMS(phone, idMessage)
        if (sent) {
            tvStatus.text = "Unique ID sent to your phone via SMS."
            Toast.makeText(this, "Unique ID sent successfully", Toast.LENGTH_LONG).show()
            clearRecoveryState()
        } else {
            Toast.makeText(
                this,
                "Could not send ID SMS. Please grant SMS permission and retry.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun sendUniqueIdFallback(phone: String, uniqueId: String) {
        val idSms = "Your Sanraksha Alert Unique ID is: $uniqueId"
        val idSent = smsHelper.sendSMS(phone, idSms)
        if (idSent) {
            tvStatus.text = "OTP unavailable, Unique ID sent directly via SMS."
        } else {
            tvStatus.text = "SMS could not be sent. Please enable SMS permission."
        }
    }

    private fun clearRecoveryState() {
        pendingOtp = null
        pendingOtpTs = 0L
        pendingUniqueId = null
    }

    private fun hasSmsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestSmsPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.SEND_SMS),
            REQUEST_SMS
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_SMS) {
            val granted = grantResults.isNotEmpty() &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED
            if (granted) {
                pendingPhone?.let {
                    etPhone.setText(it)
                    sendOtp()
                }
            } else {
                Toast.makeText(this, "SMS permission denied", Toast.LENGTH_LONG).show()
            }
        }
    }

    companion object {
        private const val REQUEST_SMS = 301
    }
}
