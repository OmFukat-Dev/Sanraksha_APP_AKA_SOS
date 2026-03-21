package com.sanraksha.sosapp.utils

import android.content.Context
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.concurrent.atomic.AtomicBoolean

data class PendingSms(
    val phone: String,
    val message: String,
    val attempts: Int,
    val lastAttemptMs: Long
)

class SmsOutbox(context: Context) {
    private val appContext = context.applicationContext
    private val file = File(appContext.filesDir, FILE_NAME)
    private val lock = Any()
    private val loaded = AtomicBoolean(false)
    private val pending = mutableListOf<PendingSms>()

    fun enqueue(phone: String, message: String) {
        ensureLoaded()
        synchronized(lock) {
            pending.add(PendingSms(phone, message, 0, 0L))
            persist()
        }
    }

    fun flush(smsHelper: SMSHelper, maxPerRun: Int, nowMs: Long = System.currentTimeMillis()): Int {
        ensureLoaded()
        var sentCount = 0
        synchronized(lock) {
            val iterator = pending.listIterator()
            while (iterator.hasNext() && sentCount < maxPerRun) {
                val item = iterator.next()
                if (item.attempts >= MAX_ATTEMPTS) {
                    iterator.remove()
                    continue
                }
                val backoffMs = baseBackoffMs(item.attempts)
                if (item.lastAttemptMs > 0 && nowMs - item.lastAttemptMs < backoffMs) {
                    continue
                }
                val sent = smsHelper.sendSMS(item.phone, item.message)
                if (sent) {
                    iterator.remove()
                    sentCount++
                } else {
                    iterator.set(
                        item.copy(
                            attempts = item.attempts + 1,
                            lastAttemptMs = nowMs
                        )
                    )
                }
            }
            persist()
        }
        return sentCount
    }

    private fun ensureLoaded() {
        if (loaded.get()) return
        synchronized(lock) {
            if (loaded.get()) return
            pending.clear()
            if (file.exists()) {
                runCatching {
                    BufferedReader(FileReader(file)).use { reader ->
                        reader.lineSequence().forEach { line ->
                            parseLine(line)?.let { pending.add(it) }
                        }
                    }
                }
            }
            loaded.set(true)
        }
    }

    private fun persist() {
        runCatching {
            BufferedWriter(FileWriter(file, false)).use { writer ->
                pending.forEach { item ->
                    writer.appendLine(encode(item))
                }
            }
        }
    }

    private fun encode(item: PendingSms): String {
        val phone = URLEncoder.encode(item.phone, CHARSET)
        val msg = URLEncoder.encode(item.message, CHARSET)
        return "$phone|$msg|${item.attempts}|${item.lastAttemptMs}"
    }

    private fun parseLine(line: String): PendingSms? {
        val parts = line.split('|')
        if (parts.size != 4) return null
        val phone = URLDecoder.decode(parts[0], CHARSET)
        val msg = URLDecoder.decode(parts[1], CHARSET)
        val attempts = parts[2].toIntOrNull() ?: return null
        val lastAttempt = parts[3].toLongOrNull() ?: return null
        return PendingSms(phone, msg, attempts, lastAttempt)
    }

    private fun baseBackoffMs(attempts: Int): Long {
        val base = 5_000L
        val factor = 1 shl attempts.coerceAtMost(5)
        return base * factor
    }

    companion object {
        private const val FILE_NAME = "sms_outbox.txt"
        private const val CHARSET = "UTF-8"
        private const val MAX_ATTEMPTS = 6
    }
}
