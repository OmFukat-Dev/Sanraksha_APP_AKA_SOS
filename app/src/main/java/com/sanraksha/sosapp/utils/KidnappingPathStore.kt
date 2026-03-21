package com.sanraksha.sosapp.utils

import android.content.Context
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.util.concurrent.atomic.AtomicBoolean

data class PathPoint(
    val lat: Double,
    val lon: Double,
    val timestampMs: Long
)

class KidnappingPathStore private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val file = File(appContext.filesDir, FILE_NAME)
    private val lock = Any()
    private val points = mutableListOf<PathPoint>()
    private val loaded = AtomicBoolean(false)

    fun startNewSession() {
        synchronized(lock) {
            points.clear()
            runCatching { file.writeText("") }
        }
    }

    fun append(point: PathPoint): List<PathPoint> {
        ensureLoaded()
        synchronized(lock) {
            points.add(point)
            appendToFile(point)
            return points.toList()
        }
    }

    fun getPoints(): List<PathPoint> {
        ensureLoaded()
        synchronized(lock) {
            return points.toList()
        }
    }

    fun clear() {
        synchronized(lock) {
            points.clear()
            runCatching { file.delete() }
        }
    }

    private fun ensureLoaded() {
        if (loaded.get()) return
        synchronized(lock) {
            if (loaded.get()) return
            points.clear()
            if (!file.exists()) {
                loaded.set(true)
                return
            }
            runCatching {
                BufferedReader(FileReader(file)).use { reader ->
                    reader.lineSequence().forEach { line ->
                        parseLine(line)?.let { points.add(it) }
                    }
                }
            }
            loaded.set(true)
        }
    }

    private fun appendToFile(point: PathPoint) {
        runCatching {
            BufferedWriter(FileWriter(file, true)).use { writer ->
                writer.appendLine("${point.lat},${point.lon},${point.timestampMs}")
            }
        }
    }

    private fun parseLine(line: String): PathPoint? {
        val parts = line.split(',')
        if (parts.size != 3) return null
        val lat = parts[0].toDoubleOrNull() ?: return null
        val lon = parts[1].toDoubleOrNull() ?: return null
        val time = parts[2].toLongOrNull() ?: return null
        return PathPoint(lat, lon, time)
    }

    companion object {
        private const val FILE_NAME = "kidnapping_path.csv"

        @Volatile
        private var instance: KidnappingPathStore? = null

        fun getInstance(context: Context): KidnappingPathStore {
            return instance ?: synchronized(this) {
                instance ?: KidnappingPathStore(context).also { instance = it }
            }
        }
    }
}
