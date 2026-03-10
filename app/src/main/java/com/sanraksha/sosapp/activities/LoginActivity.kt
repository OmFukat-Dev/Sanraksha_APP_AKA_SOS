package com.sanraksha.sosapp.activities

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
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
import com.sanraksha.sosapp.database.User
import com.sanraksha.sosapp.utils.PrefManager
import com.sanraksha.sosapp.utils.SMSHelper
import com.sanraksha.sosapp.utils.ThemeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

class LoginActivity : AppCompatActivity() {

    private lateinit var etUniqueId: EditText
    private lateinit var btnLogin: Button
    private lateinit var btnCreateAccount: Button
    private lateinit var btnForgotId: TextView

    private lateinit var database: AppDatabase
    private lateinit var prefManager: PrefManager
    private lateinit var smsHelper: SMSHelper

    private var loginAttempts = 0
    private var pendingRecoveryOtp: String? = null
    private var pendingRecoveryOtpTs: Long = 0L
    private var pendingPhoneForRecovery: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeUtils.applySavedTheme(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        database = AppDatabase.getDatabase(this)
        prefManager = PrefManager(this)
        smsHelper = SMSHelper(this)

        // Check if already logged in
        if (prefManager.isLoggedIn) {
            navigateToMain()
            return
        }

        initViews()
        setupListeners()
    }

    private fun initViews() {
        etUniqueId = findViewById(R.id.etUniqueId)
        btnLogin = findViewById(R.id.btnLogin)
        btnCreateAccount = findViewById(R.id.btnCreateAccount)
        btnForgotId = findViewById(R.id.btnForgotId)
    }

    private fun setupListeners() {
        btnLogin.setOnClickListener {
            val uniqueId = etUniqueId.text.toString().trim()
            if (uniqueId.isEmpty()) {
                Toast.makeText(this, "Please enter your Unique ID", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            performLogin(uniqueId)
        }

        btnCreateAccount.setOnClickListener {
            showCreateAccountDialog()
        }

        btnForgotId.setOnClickListener {
            startActivity(Intent(this, ForgotIdActivity::class.java))
        }
    }

    private fun performLogin(uniqueId: String) {
        lifecycleScope.launch {
            try {
                val user = withContext(Dispatchers.IO) {
                    database.userDao().getUserById(uniqueId)
                }

                if (user != null) {
                    prefManager.isLoggedIn = true
                    prefManager.userId = user.uniqueId
                    navigateToMain()
                } else {
                    loginAttempts++
                    if (loginAttempts >= 3) {
                        showOTPDialog()
                    } else {
                        Toast.makeText(
                            this@LoginActivity,
                            "Invalid ID. Attempt $loginAttempts/3",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(this@LoginActivity, "Login error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showCreateAccountDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_create_user, null)
        val etName = dialogView.findViewById<EditText>(R.id.etDialogName)
        val etPhone = dialogView.findViewById<EditText>(R.id.etDialogPhone)

        AlertDialog.Builder(this)
            .setTitle("Create New Account")
            .setView(dialogView)
            .setPositiveButton("Create") { _, _ ->
                val name = etName.text.toString().trim()
                val phone = etPhone.text.toString().trim()

                if (name.isEmpty() || phone.isEmpty()) {
                    Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                if (phone.length != 10) {
                    Toast.makeText(this, "Phone must be 10 digits", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                createNewUser(name, phone)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun createNewUser(name: String, phone: String) {
        lifecycleScope.launch {
            try {
                // Check if phone already exists
                val existingUser = withContext(Dispatchers.IO) {
                    database.userDao().getUserByPhone(phone)
                }

                if (existingUser != null) {
                    Toast.makeText(this@LoginActivity, "Phone number already registered", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                // Generate unique ID
                val uniqueId = generateUniqueId()

                val newUser = User(
                    uniqueId = uniqueId,
                    name = name,
                    phone = phone
                )

                withContext(Dispatchers.IO) {
                    database.userDao().insert(newUser)
                }

                // Show unique ID to user
                showUniqueIdDialog(uniqueId)

                // Auto login
                prefManager.isLoggedIn = true
                prefManager.userId = uniqueId

            } catch (e: Exception) {
                Toast.makeText(this@LoginActivity, "Error creating account: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun generateUniqueId(): String {
        val randomNumber = Random.nextInt(100000, 999999)
        return "SRK$randomNumber"
    }

    private fun showUniqueIdDialog(uniqueId: String) {
        AlertDialog.Builder(this)
            .setTitle("Your Unique ID")
            .setMessage("Please save this ID for future login:\n\n$uniqueId")
            .setPositiveButton("OK") { _, _ ->
                navigateToMain()
            }
            .setCancelable(false)
            .show()
    }

    private fun showForgotIdDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_forgot_id, null)
        val etPhone = dialogView.findViewById<EditText>(R.id.etDialogForgotPhone)

        AlertDialog.Builder(this)
            .setTitle("Forgot Unique ID")
            .setView(dialogView)
            .setPositiveButton("Retrieve") { _, _ ->
                val phone = etPhone.text.toString().trim()

                if (phone.length != 10) {
                    Toast.makeText(this, "Please enter valid 10-digit phone", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                retrieveUniqueId(phone)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun retrieveUniqueId(phone: String) {
        if (!hasSmsPermission()) {
            pendingPhoneForRecovery = phone
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.SEND_SMS),
                REQUEST_SEND_SMS
            )
            Toast.makeText(this, "Please allow SMS permission to continue", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                val user = withContext(Dispatchers.IO) {
                    database.userDao().getUserByPhone(phone)
                }

                if (user != null) {
                    val otp = Random.nextInt(100000, 999999).toString()
                    val otpMessage = "Sanraksha OTP for ID recovery: $otp. Valid for 5 minutes."
                    val otpSent = smsHelper.sendSMS(phone, otpMessage)

                    if (otpSent) {
                        pendingRecoveryOtp = otp
                        pendingRecoveryOtpTs = System.currentTimeMillis()
                        Toast.makeText(
                            this@LoginActivity,
                            "OTP sent to your registered number",
                            Toast.LENGTH_LONG
                        ).show()
                        showOtpVerificationDialog(phone, user.uniqueId)
                    } else {
                        sendUniqueIdDirectly(phone, user.uniqueId)
                    }
                } else {
                    AlertDialog.Builder(this@LoginActivity)
                        .setTitle("Phone Not Found")
                        .setMessage("Would you like to create a new account?")
                        .setPositiveButton("Yes") { _, _ ->
                            showCreateAccountDialog()
                        }
                        .setNegativeButton("No", null)
                        .show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@LoginActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showOtpVerificationDialog(phone: String, uniqueId: String) {
        val otpInput = EditText(this).apply {
            hint = "Enter 6-digit OTP"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setPadding(48, 32, 48, 32)
        }

        AlertDialog.Builder(this)
            .setTitle("Verify OTP")
            .setMessage("Enter the OTP sent to $phone")
            .setView(otpInput)
            .setCancelable(false)
            .setPositiveButton("Verify") { _, _ ->
                val enteredOtp = otpInput.text.toString().trim()
                val otpValid =
                    pendingRecoveryOtp != null &&
                            enteredOtp == pendingRecoveryOtp &&
                            (System.currentTimeMillis() - pendingRecoveryOtpTs) <= 5 * 60 * 1000

                if (otpValid) {
                    sendUniqueIdDirectly(phone, uniqueId)
                } else {
                    Toast.makeText(this, "Invalid or expired OTP", Toast.LENGTH_LONG).show()
                }
                clearRecoveryOtp()
            }
            .setNegativeButton("Cancel") { _, _ ->
                clearRecoveryOtp()
            }
            .show()
    }

    private fun sendUniqueIdDirectly(phone: String, uniqueId: String) {
        val idMessage = "Your Sanraksha Alert Unique ID is: $uniqueId"
        val idSent = smsHelper.sendSMS(phone, idMessage)
        if (idSent) {
            Toast.makeText(this, "Unique ID sent via SMS", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(
                this,
                "Could not send SMS. Please grant SMS permission and try again.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun clearRecoveryOtp() {
        pendingRecoveryOtp = null
        pendingRecoveryOtpTs = 0L
    }

    private fun hasSmsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_SEND_SMS) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                pendingPhoneForRecovery?.let { retrieveUniqueId(it) }
            } else {
                Toast.makeText(this, "SMS permission denied", Toast.LENGTH_LONG).show()
            }
            pendingPhoneForRecovery = null
        }
    }

    private fun showOTPDialog() {
        // For demo purposes, show a test OTP
        val testOTP = Random.nextInt(1000, 9999)
        Toast.makeText(this, "Test OTP: $testOTP (Demo only)", Toast.LENGTH_LONG).show()
        loginAttempts = 0
    }

    private fun navigateToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    companion object {
        private const val REQUEST_SEND_SMS = 201
    }
}
