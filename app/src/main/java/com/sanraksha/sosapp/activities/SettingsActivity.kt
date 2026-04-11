package com.sanraksha.sosapp.activities

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.switchmaterial.SwitchMaterial
import com.sanraksha.sosapp.R
import com.sanraksha.sosapp.utils.PrefManager
import com.sanraksha.sosapp.utils.ThemeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class SettingsActivity : AppCompatActivity() {

    private lateinit var seekBarSensitivity: SeekBar
    private lateinit var tvSensitivity: TextView
    private lateinit var switchDarkMode: SwitchMaterial
    private lateinit var switchBlockScreenshots: SwitchMaterial
    private lateinit var switchHideSensitiveInfo: SwitchMaterial
    private lateinit var switchRequireSosConfirmation: SwitchMaterial
    private lateinit var btnLogout: Button
    private lateinit var btnShare: Button
    private lateinit var btnRate: Button
    private lateinit var btnPrivacy: Button
    private lateinit var bottomNav: BottomNavigationView

    private lateinit var prefManager: PrefManager
    private var isInitializing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeUtils.applySavedTheme(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        prefManager = PrefManager(this)

        initViews()
        setupListeners()
        loadSettings()
    }

    private fun initViews() {
        seekBarSensitivity = findViewById(R.id.seekBarSensitivity)
        tvSensitivity = findViewById(R.id.tvSensitivity)
        switchDarkMode = findViewById(R.id.switchDarkMode)
        switchBlockScreenshots = findViewById(R.id.switchBlockScreenshots)
        switchHideSensitiveInfo = findViewById(R.id.switchHideSensitiveInfo)
        switchRequireSosConfirmation = findViewById(R.id.switchRequireSosConfirmation)
        btnLogout = findViewById(R.id.btnLogout)
        btnShare = findViewById(R.id.btnShare)
        btnRate = findViewById(R.id.btnRate)
        btnPrivacy = findViewById(R.id.btnPrivacy)
        bottomNav = findViewById(R.id.bottomNavigation)

        bottomNav.selectedItemId = R.id.nav_settings
    }

    private fun setupListeners() {
        seekBarSensitivity.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val sensitivity = when (progress) {
                    0 -> "Low"
                    1 -> "Medium"
                    2 -> "High"
                    else -> "Medium"
                }
                tvSensitivity.text = sensitivity
                prefManager.shakeSensitivity = progress
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        switchDarkMode.setOnCheckedChangeListener { _, isChecked ->
            if (isInitializing) return@setOnCheckedChangeListener
            prefManager.darkMode = isChecked
            applyTheme(isChecked)
        }

        switchBlockScreenshots.setOnCheckedChangeListener { _, isChecked ->
            prefManager.blockScreenshots = isChecked
            applyScreenshotSecurity(isChecked)
        }

        switchHideSensitiveInfo.setOnCheckedChangeListener { _, isChecked ->
            prefManager.hideSensitiveInfo = isChecked
        }

        switchRequireSosConfirmation.setOnCheckedChangeListener { _, isChecked ->
            prefManager.requireSosConfirmation = isChecked
        }

        btnLogout.setOnClickListener {
            showLogoutDialog()
        }

        btnShare.setOnClickListener {
            shareApp()
        }

        btnRate.setOnClickListener {
            rateApp()
        }

        btnPrivacy.setOnClickListener {
            openPrivacyPolicy()
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
                R.id.nav_profile -> {
                    startActivity(Intent(this, ProfileActivity::class.java))
                    finish()
                    true
                }
                R.id.nav_settings -> true
                else -> false
            }
        }
    }

    private fun loadSettings() {
        isInitializing = true
        seekBarSensitivity.progress = prefManager.shakeSensitivity
        val sensitivity = when (prefManager.shakeSensitivity) {
            0 -> "Low"
            1 -> "Medium"
            2 -> "High"
            else -> "Medium"
        }
        tvSensitivity.text = sensitivity

        switchDarkMode.isChecked = prefManager.darkMode
        switchBlockScreenshots.isChecked = prefManager.blockScreenshots
        switchHideSensitiveInfo.isChecked = prefManager.hideSensitiveInfo
        switchRequireSosConfirmation.isChecked = prefManager.requireSosConfirmation
        applyScreenshotSecurity(prefManager.blockScreenshots)
        isInitializing = false
    }

    private fun applyScreenshotSecurity(blockScreenshots: Boolean) {
        if (blockScreenshots) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    private fun applyTheme(isDark: Boolean) {
        val targetMode = if (isDark) {
            AppCompatDelegate.MODE_NIGHT_YES
        } else {
            AppCompatDelegate.MODE_NIGHT_NO
        }
        if (AppCompatDelegate.getDefaultNightMode() != targetMode) {
            AppCompatDelegate.setDefaultNightMode(targetMode)
            delegate.applyDayNight()
            recreate()
        }
    }

    private fun showLogoutDialog() {
        AlertDialog.Builder(this)
            .setTitle("Logout")
            .setMessage("Are you sure you want to logout?")
            .setPositiveButton("Yes") { _, _ ->
                logout()
            }
            .setNegativeButton("No", null)
            .show()
    }

    private fun logout() {
        prefManager.logout()
        startActivity(Intent(this, LoginActivity::class.java))
        finishAffinity()
    }

    private fun shareApp() {
        lifecycleScope.launch {
            val apkUri = withContext(Dispatchers.IO) { exportShareableApk() }
            if (apkUri == null) {
                Toast.makeText(
                    this@SettingsActivity,
                    "Unable to prepare APK for sharing on this device.",
                    Toast.LENGTH_LONG
                ).show()
                return@launch
            }

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/vnd.android.package-archive"
                putExtra(Intent.EXTRA_SUBJECT, "Sanraksha Alert APK")
                putExtra(Intent.EXTRA_TEXT, "Sharing the Sanraksha Alert app APK.")
                putExtra(Intent.EXTRA_STREAM, apkUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            launchExternalIntent(
                Intent.createChooser(shareIntent, "Share app APK"),
                "No app available to share the APK."
            )
        }
    }

    private fun rateApp() {
        try {
            val uri = Uri.parse("market://details?id=${packageName}")
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (_: ActivityNotFoundException) {
            val uri = Uri.parse("https://play.google.com/store/apps/details?id=${packageName}")
            launchExternalIntent(
                Intent(Intent.ACTION_VIEW, uri),
                "No browser available to open the Play Store page."
            )
        }
    }

    private fun openPrivacyPolicy() {
        val uri = Uri.parse("https://sanrakshaalert.com/privacy")
        launchExternalIntent(
            Intent(Intent.ACTION_VIEW, uri),
            "No browser available to open the privacy policy."
        )
    }

    private fun launchExternalIntent(intent: Intent, errorMessage: String) {
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show()
        }
    }

    private fun exportShareableApk(): Uri? {
        return runCatching {
            val sourcePath = applicationInfo.publicSourceDir?.takeIf { it.isNotBlank() }
                ?: applicationInfo.sourceDir
            val sourceFile = File(sourcePath)
            if (!sourceFile.exists()) {
                return null
            }

            val shareDir = File(cacheDir, "shared_apk").apply { mkdirs() }
            val apkFile = File(shareDir, "Sanraksha-Alert.apk")
            sourceFile.inputStream().use { input ->
                apkFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                apkFile
            )
        }.getOrNull()
    }
}
