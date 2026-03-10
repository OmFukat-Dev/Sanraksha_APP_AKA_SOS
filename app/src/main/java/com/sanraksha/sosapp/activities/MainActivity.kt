package com.sanraksha.sosapp.activities

import android.content.pm.PackageManager
import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.switchmaterial.SwitchMaterial
import com.sanraksha.sosapp.R
import com.sanraksha.sosapp.database.AppDatabase
import com.sanraksha.sosapp.services.SOSMonitoringService
import com.sanraksha.sosapp.utils.LocationHelper
import com.sanraksha.sosapp.utils.PrefManager
import com.sanraksha.sosapp.utils.PermissionHelper
import com.sanraksha.sosapp.utils.SOSTriggerManager
import com.sanraksha.sosapp.utils.ThemeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var btnSOS: Button
    private lateinit var tvCountdown: TextView
    private lateinit var tvLocationHeader: TextView
    private lateinit var tvLocationMarker: TextView
    private lateinit var tvGuardianName: TextView
    private lateinit var tvGuardianMeta: TextView
    private lateinit var tvSosStateChip: TextView
    private lateinit var tvEtaPolice: TextView
    private lateinit var tvEtaAmbulance: TextView
    private lateinit var tvEtaFire: TextView
    private lateinit var ivDummyMap: ImageView
    private lateinit var switchSafetyMode: SwitchMaterial
    private lateinit var switchShake: SwitchMaterial
    private lateinit var switchVoice: SwitchMaterial
    private lateinit var switchSound: SwitchMaterial
    private lateinit var bottomNav: BottomNavigationView

    private lateinit var prefManager: PrefManager
    private lateinit var database: AppDatabase
    private lateinit var sosTriggerManager: SOSTriggerManager
    private lateinit var locationHelper: LocationHelper

    private var isSOSActive = false
    private var countdown = 30
    private val handler = Handler(Looper.getMainLooper())
    private var isInitializingSettings = false
    private var lastKnownLocation: Pair<Double, Double>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeUtils.applySavedTheme(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefManager = PrefManager(this)
        database = AppDatabase.getDatabase(this)
        sosTriggerManager = SOSTriggerManager(this)
        locationHelper = LocationHelper(this)

        initViews()
        setupMap()
        setupListeners()
        checkPermissions()
        loadSettings()
        loadGuardianUserInfo()
        updateSOSStateChip()
    }

    private fun initViews() {
        btnSOS = findViewById(R.id.btnSOS)
        tvCountdown = findViewById(R.id.tvCountdown)
        tvLocationHeader = findViewById(R.id.tvLocationHeader)
        tvLocationMarker = findViewById(R.id.tvLocationMarker)
        tvGuardianName = findViewById(R.id.tvGuardianName)
        tvGuardianMeta = findViewById(R.id.tvGuardianMeta)
        tvSosStateChip = findViewById(R.id.tvSosStateChip)
        tvEtaPolice = findViewById(R.id.tvEtaPolice)
        tvEtaAmbulance = findViewById(R.id.tvEtaAmbulance)
        tvEtaFire = findViewById(R.id.tvEtaFire)
        tvEtaPolice.visibility = View.GONE
        tvEtaAmbulance.visibility = View.GONE
        tvEtaFire.visibility = View.GONE
        ivDummyMap = findViewById(R.id.ivDummyMap)
        switchSafetyMode = findViewById(R.id.switchSafetyMode)
        switchShake = findViewById(R.id.switchShake)
        switchVoice = findViewById(R.id.switchVoice)
        switchSound = findViewById(R.id.switchSound)
        bottomNav = findViewById(R.id.bottomNavigation)

        bottomNav.selectedItemId = R.id.nav_home
    }

    private fun setupListeners() {
        btnSOS.setOnClickListener {
            if (!isSOSActive) {
                maybeStartSOS()
            } else {
                cancelSOS()
            }
        }

        switchSafetyMode.setOnCheckedChangeListener { _, isChecked ->
            prefManager.safetyMode = isChecked
            if (isInitializingSettings) return@setOnCheckedChangeListener
            if (isChecked) {
                startMonitoringService()
            } else {
                stopMonitoringService()
            }
        }

        switchShake.setOnCheckedChangeListener { _, isChecked ->
            prefManager.shakeEnabled = isChecked
        }

        switchVoice.setOnCheckedChangeListener { _, isChecked ->
            prefManager.voiceEnabled = isChecked
        }

        switchSound.setOnCheckedChangeListener { _, isChecked ->
            prefManager.soundEnabled = isChecked
        }

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> true
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
                R.id.nav_settings -> {
                    startActivity(Intent(this, SettingsActivity::class.java))
                    finish()
                    true
                }
                else -> false
            }
        }
    }

    private fun loadSettings() {
        isInitializingSettings = true
        switchSafetyMode.isChecked = prefManager.safetyMode
        switchShake.isChecked = prefManager.shakeEnabled
        switchVoice.isChecked = prefManager.voiceEnabled
        switchSound.isChecked = prefManager.soundEnabled
        isInitializingSettings = false

        // Auto-resume monitoring only when permissions are already granted.
        if (prefManager.safetyMode && PermissionHelper.checkPermissions(this)) {
            startMonitoringService()
        }
    }

    private fun setupMap() {
        ivDummyMap.setOnClickListener { openCurrentLocationInMaps() }
    }

    private fun loadGuardianUserInfo() {
        lifecycleScope.launch {
            val userId = prefManager.userId
            if (userId.isNullOrBlank()) {
                tvGuardianName.text = "GUARDIAN USER"
                tvGuardianMeta.text = "ID: unavailable"
                return@launch
            }

            val user = withContext(Dispatchers.IO) {
                database.userDao().getUserById(userId)
            }

            if (user != null) {
                tvGuardianName.text = user.name.uppercase(Locale.getDefault())
                tvGuardianMeta.text = if (prefManager.hideSensitiveInfo) {
                    "ID: ${maskUserId(user.uniqueId)}"
                } else {
                    "ID: ${user.uniqueId}"
                }
            } else {
                tvGuardianName.text = "GUARDIAN USER"
                tvGuardianMeta.text = if (prefManager.hideSensitiveInfo) {
                    "ID: ${maskUserId(userId)}"
                } else {
                    "ID: $userId"
                }
            }
        }
    }

    private fun checkPermissions() {
        if (!PermissionHelper.checkPermissions(this)) {
            PermissionHelper.requestPermissions(this)
        } else {
            loadCurrentLocation()
        }
    }

    private fun loadCurrentLocation() {
        lifecycleScope.launch {
            val location = withContext(Dispatchers.IO) {
                locationHelper.getCurrentLocation()
            }

            if (location != null) {
                lastKnownLocation = location
                val precision = if (prefManager.hideSensitiveInfo) 3 else 5
                val lat = String.format(Locale.US, "%.${precision}f", location.first)
                val lon = String.format(Locale.US, "%.${precision}f", location.second)
                tvLocationHeader.text = "GPS Location: $lat, $lon"
                tvLocationMarker.text = "MARKED: user pinned at $lat, $lon"
                updateMapLocation(location.first, location.second)
            } else {
                tvLocationHeader.text = "GPS Location: unavailable"
                tvLocationMarker.text = "MARKED: location permission needed"
            }
        }
    }

    private fun maybeStartSOS() {
        if (!prefManager.requireSosConfirmation) {
            startSOSCountdown()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Confirm SOS")
            .setMessage("Send emergency alert now?")
            .setPositiveButton("Send") { _, _ ->
                startSOSCountdown()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun maskUserId(userId: String): String {
        if (userId.length <= 4) return "****"
        val visible = userId.takeLast(4)
        return "****$visible"
    }

    private fun applyScreenshotSecurity() {
        if (prefManager.blockScreenshots) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    private fun updateMapLocation(latitude: Double, longitude: Double) {
        // Static image mode: no live map rendering.
    }

    private fun openCurrentLocationInMaps() {
        val location = lastKnownLocation
        if (location == null) {
            Toast.makeText(this, "Current location not available yet", Toast.LENGTH_SHORT).show()
            return
        }

        val (lat, lon) = location
        val pinUri = Uri.parse("geo:$lat,$lon?q=$lat,$lon(User Location)")
        val mapIntent = Intent(Intent.ACTION_VIEW, pinUri).apply {
            setPackage("com.google.android.apps.maps")
        }

        if (mapIntent.resolveActivity(packageManager) != null) {
            startActivity(mapIntent)
        } else {
            val webUri = Uri.parse(locationHelper.getLocationString(lat, lon))
            startActivity(Intent(Intent.ACTION_VIEW, webUri))
        }
    }

    private fun startSOSCountdown() {
        isSOSActive = true
        countdown = 30
        btnSOS.text = "Cancel SOS"
        btnSOS.backgroundTintList = ColorStateList.valueOf(getColor(R.color.warning))
        updateSOSStateChip()

        val countdownRunnable = object : Runnable {
            override fun run() {
                if (countdown > 0 && isSOSActive) {
                    tvCountdown.text = "SOS in $countdown seconds..."
                    countdown--
                    handler.postDelayed(this, 1000)
                } else if (isSOSActive) {
                    triggerSOS()
                }
            }
        }
        handler.post(countdownRunnable)
    }

    private fun cancelSOS() {
        isSOSActive = false
        handler.removeCallbacksAndMessages(null)
        btnSOS.text = getString(R.string.sos_button)
        btnSOS.setBackgroundResource(R.drawable.bg_sos_neon_button)
        btnSOS.backgroundTintList = null
        tvCountdown.text = ""
        sosTriggerManager.stopSiren()
        updateSOSStateChip()
    }

    private fun triggerSOS() {
        lifecycleScope.launch {
            try {
                val userId = prefManager.userId ?: return@launch
                val user = withContext(Dispatchers.IO) {
                    database.userDao().getUserById(userId)
                }

                if (user != null) {
                    sosTriggerManager.triggerSOS(userId, user.name)
                    Toast.makeText(this@MainActivity, "SOS Alert Sent!", Toast.LENGTH_LONG).show()
                }

                // Reset UI after 30 seconds
                handler.postDelayed({
                    cancelSOS()
                }, 30000)

            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startMonitoringService() {
        if (!PermissionHelper.checkPermissions(this)) {
            PermissionHelper.requestPermissions(this)
            return
        }
        try {
            val intent = Intent(this, SOSMonitoringService::class.java)
            startForegroundService(intent)
        } catch (e: Exception) {
            prefManager.safetyMode = false
            switchSafetyMode.isChecked = false
            Toast.makeText(
                this,
                "Unable to start safety monitoring on this device",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun updateSOSStateChip() {
        if (isSOSActive) {
            tvSosStateChip.text = "SOS ACTIVE"
            tvSosStateChip.backgroundTintList = ColorStateList.valueOf(getColor(R.color.error))
            tvSosStateChip.setTextColor(getColor(android.R.color.white))
        } else {
            tvSosStateChip.text = "SOS INACTIVE"
            tvSosStateChip.backgroundTintList = ColorStateList.valueOf(getColor(R.color.success))
            tvSosStateChip.setTextColor(getColor(android.R.color.white))
        }
    }

    private fun stopMonitoringService() {
        val intent = Intent(this, SOSMonitoringService::class.java)
        stopService(intent)
    }

    override fun onResume() {
        super.onResume()
        applyScreenshotSecurity()
        loadGuardianUserInfo()
        loadCurrentLocation()
        bottomNav.selectedItemId = R.id.nav_home
    }

    override fun onStart() {
        super.onStart()
    }

    override fun onPause() {
        super.onPause()
    }

    override fun onStop() {
        super.onStop()
    }

    override fun onLowMemory() {
        super.onLowMemory()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PermissionHelper.PERMISSION_REQUEST_CODE) {
            val allGranted = grantResults.isNotEmpty() &&
                    grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            if (allGranted && switchSafetyMode.isChecked) {
                startMonitoringService()
                loadCurrentLocation()
            } else if (allGranted) {
                loadCurrentLocation()
            } else if (!allGranted && switchSafetyMode.isChecked) {
                prefManager.safetyMode = false
                switchSafetyMode.isChecked = false
                Toast.makeText(
                    this,
                    "Required permissions were denied. Safety mode disabled.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
    }
}
