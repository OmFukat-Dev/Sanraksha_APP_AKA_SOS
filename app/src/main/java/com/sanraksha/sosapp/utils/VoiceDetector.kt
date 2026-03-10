package com.sanraksha.sosapp.utils

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

class VoiceDetector(
    private val context: Context,
    private val onSOSDetected: () -> Unit
) {
    private var speechRecognizer: SpeechRecognizer? = null
    private val sosKeywords = listOf("help", "sos", "emergency")
    private val handler = Handler(Looper.getMainLooper())
    private var isListening = false

    fun start() {
        try {
            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                return
            }

            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    restartListening()
                }

                override fun onError(error: Int) {
                    restartListening()
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    matches?.forEach { text ->
                        if (sosKeywords.any { keyword ->
                                text.contains(keyword, ignoreCase = true)
                            }) {
                            onSOSDetected()
                            return
                        }
                    }
                    restartListening()
                }

                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            isListening = true
            startListening()
        } catch (e: Exception) {
            stop()
        }
    }

    private fun startListening() {
        if (!isListening) return
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            }
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            restartListening()
        }
    }

    private fun restartListening() {
        if (!isListening) return
        try {
            speechRecognizer?.stopListening()
        } catch (e: Exception) {
            // Ignore and retry.
        }
        handler.postDelayed({
            startListening()
        }, 500)
    }

    fun stop() {
        isListening = false
        handler.removeCallbacksAndMessages(null)
        try {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            // Ignore cleanup failures.
        }
        speechRecognizer = null
    }
}
