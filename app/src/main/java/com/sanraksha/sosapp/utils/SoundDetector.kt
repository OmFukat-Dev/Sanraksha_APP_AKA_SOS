package com.sanraksha.sosapp.utils

import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import java.io.File

class SoundDetector(
    private val cacheDir: File,
    private val onScreamDetected: () -> Unit
) {
    private var mediaRecorder: MediaRecorder? = null
    private val handler = Handler(Looper.getMainLooper())
    private val screamThreshold = 2000 // Amplitude threshold
    private var isMonitoring = false

    private val monitoringRunnable = object : Runnable {
        override fun run() {
            if (isMonitoring) {
                checkAmplitude()
                handler.postDelayed(this, 100) // Check every 100ms
            }
        }
    }

    fun start() {
        try {
            mediaRecorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                setOutputFile(File(cacheDir, "temp_audio.3gp").absolutePath)
                prepare()
                start()
            }
            isMonitoring = true
            handler.post(monitoringRunnable)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun checkAmplitude() {
        try {
            val amplitude = mediaRecorder?.maxAmplitude ?: 0
            if (amplitude > screamThreshold) {
                onScreamDetected()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun stop() {
        isMonitoring = false
        handler.removeCallbacks(monitoringRunnable)
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}