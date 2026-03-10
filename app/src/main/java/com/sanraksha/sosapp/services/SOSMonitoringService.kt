package com.sanraksha.sosapp.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.sanraksha.sosapp.R
import com.sanraksha.sosapp.database.AppDatabase
import com.sanraksha.sosapp.utils.PrefManager
import com.sanraksha.sosapp.utils.SOSTriggerManager
import com.sanraksha.sosapp.utils.ShakeDetector
import com.sanraksha.sosapp.utils.SoundDetector
import com.sanraksha.sosapp.utils.VoiceDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class SOSMonitoringService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var prefManager: PrefManager
    private lateinit var database: AppDatabase
    private lateinit var sosTriggerManager: SOSTriggerManager

    private var shakeDetector: ShakeDetector? = null
    private var voiceDetector: VoiceDetector? = null
    private var soundDetector: SoundDetector? = null
    private var lastTriggerAtMs: Long = 0L

    override fun onCreate() {
        super.onCreate()
        prefManager = PrefManager(this)
        database = AppDatabase.getDatabase(this)
        sosTriggerManager = SOSTriggerManager(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            startForeground(NOTIFICATION_ID, createNotification())
            startMonitoring()
        } catch (e: Exception) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopMonitoring()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startMonitoring() {
        if (!prefManager.safetyMode) {
            stopSelf()
            return
        }

        val triggerCallback: () -> Unit = trigger@{
            val now = System.currentTimeMillis()
            if (now - lastTriggerAtMs < 15_000) return@trigger
            lastTriggerAtMs = now
            serviceScope.launch {
                val userId = prefManager.userId ?: return@launch
                val user = database.userDao().getUserById(userId) ?: return@launch
                sosTriggerManager.triggerSOS(userId, user.name)
            }
            Unit
        }

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

        if (prefManager.voiceEnabled && voiceDetector == null) {
            try {
                voiceDetector = VoiceDetector(this, triggerCallback).also { it.start() }
            } catch (e: Exception) {
                voiceDetector = null
            }
        }

        if (prefManager.soundEnabled && soundDetector == null) {
            try {
                soundDetector = SoundDetector(cacheDir, triggerCallback).also { it.start() }
            } catch (e: Exception) {
                soundDetector = null
            }
        }
    }

    private fun stopMonitoring() {
        shakeDetector?.stop()
        shakeDetector = null

        voiceDetector?.stop()
        voiceDetector = null

        soundDetector?.stop()
        soundDetector = null
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

    companion object {
        private const val CHANNEL_ID = "sos_monitoring_channel"
        private const val NOTIFICATION_ID = 1001
    }
}
