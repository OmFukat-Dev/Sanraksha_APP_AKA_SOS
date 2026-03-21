package com.sanraksha.sosapp.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ProcessLifecycleOwner
import com.sanraksha.sosapp.R
import com.sanraksha.sosapp.database.AppDatabase
import com.sanraksha.sosapp.utils.PrefManager
import com.sanraksha.sosapp.utils.SmsDispatcher
import com.sanraksha.sosapp.utils.SOSTriggerManager
import com.sanraksha.sosapp.utils.ShakeDetector
import com.sanraksha.sosapp.utils.SoundDetector
import com.sanraksha.sosapp.utils.VoiceDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SOSMonitoringService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var prefManager: PrefManager
    private lateinit var database: AppDatabase
    private lateinit var sosTriggerManager: SOSTriggerManager
    private lateinit var smsDispatcher: SmsDispatcher

    private var shakeDetector: ShakeDetector? = null
    private var voiceDetector: VoiceDetector? = null
    private var soundDetector: SoundDetector? = null
    private var lastTriggerAtMs: Long = 0L
    private var kidnappingTrackingStarted = false
    private var micAllowed = false
    private var outboxJob: kotlinx.coroutines.Job? = null

    override fun onCreate() {
        super.onCreate()
        prefManager = PrefManager(this)
        database = AppDatabase.getDatabase(this)
        sosTriggerManager = SOSTriggerManager(this)
        smsDispatcher = SmsDispatcher(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            micAllowed = prefManager.safetyMode &&
                    (prefManager.voiceEnabled || prefManager.soundEnabled) &&
                    hasRecordAudioPermission() &&
                    isAppInForeground()

            startForegroundCompat(micAllowed)
            startMonitoring()
        } catch (e: Exception) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopMonitoring()
        sosTriggerManager.shutdown()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startMonitoring() {
        if (!prefManager.safetyMode && !prefManager.kidnappingMode) {
            stopSelf()
            return
        }

        if (prefManager.kidnappingMode && !kidnappingTrackingStarted) {
            kidnappingTrackingStarted = true
            serviceScope.launch {
                val userId = prefManager.userId ?: return@launch
                val user = withContext(Dispatchers.IO) {
                    database.userDao().getUserById(userId)
                } ?: return@launch
                sosTriggerManager.startKidnappingTracking(userId, user.name)
            }
        }

        val triggerCallback: () -> Unit = trigger@{
            val now = System.currentTimeMillis()
            if (now - lastTriggerAtMs < 15_000) return@trigger
            lastTriggerAtMs = now
            serviceScope.launch {
                val userId = prefManager.userId ?: return@launch
                val user = withContext(Dispatchers.IO) {
                    database.userDao().getUserById(userId)
                } ?: return@launch
                sosTriggerManager.triggerSOS(userId, user.name, prefManager.kidnappingMode)
            }
            Unit
        }

        if (prefManager.safetyMode) {
            if (prefManager.shakeEnabled && shakeDetector == null) {
                try {
                    shakeDetector = ShakeDetector(this, triggerCallback).also {
                        it.setSensitivity(prefManager.shakeSensitivity)
                        it.start()
                    }
                } catch (e: Exception) {
                    shakeDetector = null
                }
            }

            if (micAllowed && prefManager.voiceEnabled && voiceDetector == null) {
                try {
                    voiceDetector = VoiceDetector(this, triggerCallback).also { it.start() }
                } catch (e: Exception) {
                    voiceDetector = null
                }
            }

            if (micAllowed && prefManager.soundEnabled && soundDetector == null) {
                try {
                soundDetector = SoundDetector(this, triggerCallback).also { it.start() }
                } catch (e: Exception) {
                    soundDetector = null
                }
            }
        }

        startOutboxFlushLoop()
    }

    private fun stopMonitoring() {
        shakeDetector?.stop()
        shakeDetector = null

        voiceDetector?.stop()
        voiceDetector = null

        soundDetector?.stop()
        soundDetector = null

        sosTriggerManager.stopKidnappingTracking()
        kidnappingTrackingStarted = false

        outboxJob?.cancel()
        outboxJob = null
    }

    private fun startOutboxFlushLoop() {
        outboxJob?.cancel()
        outboxJob = serviceScope.launch {
            while (true) {
                smsDispatcher.flushOutbox(10)
                delay(30_000L)
            }
        }
    }

    private fun createNotification(): Notification {
        createNotificationChannel()
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.sos_monitoring_active))
            .setContentText(getString(R.string.monitoring_for_emergencies))
            .setSmallIcon(R.drawable.ic_sos)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            "SOS Monitoring",
            NotificationManager.IMPORTANCE_LOW
        )
        manager.createNotificationChannel(channel)
    }

    private fun startForegroundCompat(needsMic: Boolean) {
        val notification = createNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val type = if (needsMic) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            } else {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            }
            startForeground(NOTIFICATION_ID, notification, type)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun hasRecordAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.RECORD_AUDIO
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    private fun isAppInForeground(): Boolean {
        return try {
            androidx.lifecycle.ProcessLifecycleOwner.get()
                .lifecycle
                .currentState
                .isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)
        } catch (_: Exception) {
            false
        }
    }

    companion object {
        private const val CHANNEL_ID = "sos_monitoring_channel"
        private const val NOTIFICATION_ID = 1001
    }
}
