package com.sanraksha.sosapp.activities

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.sanraksha.sosapp.R
import com.sanraksha.sosapp.database.AppDatabase
import com.sanraksha.sosapp.utils.PrefManager
import com.sanraksha.sosapp.utils.ThemeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProfileActivity : AppCompatActivity() {

    private lateinit var etName: EditText
    private lateinit var etPhone: EditText
    private lateinit var etEmail: EditText
    private lateinit var etBloodGroup: EditText
    private lateinit var etAge: EditText
    private lateinit var btnSave: Button
    private lateinit var bottomNav: BottomNavigationView

    private lateinit var database: AppDatabase
    private lateinit var prefManager: PrefManager

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeUtils.applySavedTheme(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        database = AppDatabase.getDatabase(this)
        prefManager = PrefManager(this)

        initViews()
        setupListeners()
        loadProfile()
    }

    private fun initViews() {
        etName = findViewById(R.id.etName)
        etPhone = findViewById(R.id.etPhone)
        etEmail = findViewById(R.id.etEmail)
        etBloodGroup = findViewById(R.id.etBloodGroup)
        etAge = findViewById(R.id.etAge)
        btnSave = findViewById(R.id.btnSave)
        bottomNav = findViewById(R.id.bottomNavigation)

        bottomNav.selectedItemId = R.id.nav_profile
    }

    private fun setupListeners() {
        btnSave.setOnClickListener {
            saveProfile()
        }

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                    true
                }
                R.id.nav_contacts -> {
                    startActivity(Intent(this, ContactsActivity::class.java))
                    finish()
                    true
                }
                R.id.nav_profile -> true
                R.id.nav_settings -> {
                    startActivity(Intent(this, SettingsActivity::class.java))
                    finish()
                    true
                }
                else -> false
            }
        }
    }

    private fun loadProfile() {
        lifecycleScope.launch {
            try {
                val userId = prefManager.userId ?: return@launch
                val user = withContext(Dispatchers.IO) {
                    database.userDao().getUserById(userId)
                }

                user?.let {
                    etName.setText(it.name)
                    etPhone.setText(it.phone)
                    etEmail.setText(it.email ?: "")
                    etBloodGroup.setText(it.bloodGroup ?: "")
                    etAge.setText(it.age?.toString() ?: "")
                }
            } catch (e: Exception) {
                Toast.makeText(this@ProfileActivity, "Error loading profile", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun saveProfile() {
        lifecycleScope.launch {
            try {
                val userId = prefManager.userId ?: return@launch
                val user = withContext(Dispatchers.IO) {
                    database.userDao().getUserById(userId)
                } ?: return@launch

                val name = etName.text.toString().trim()
                val phone = etPhone.text.toString().trim()
                val email = etEmail.text.toString().trim()
                val bloodGroup = etBloodGroup.text.toString().trim()
                val ageText = etAge.text.toString().trim()

                if (name.isEmpty() || phone.isEmpty()) {
                    Toast.makeText(this@ProfileActivity, "Name and phone are required", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val age = if (ageText.isNotEmpty()) ageText.toIntOrNull() else null

                val updatedUser = user.copy(
                    name = name,
                    phone = phone,
                    email = email.ifEmpty { null },
                    bloodGroup = bloodGroup.ifEmpty { null },
                    age = age
                )

                withContext(Dispatchers.IO) {
                    database.userDao().update(updatedUser)
                }

                Toast.makeText(this@ProfileActivity, "Profile updated", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@ProfileActivity, "Error saving profile", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
