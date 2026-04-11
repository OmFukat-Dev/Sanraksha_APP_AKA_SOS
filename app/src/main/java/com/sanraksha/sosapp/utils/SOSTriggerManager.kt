package com.sanraksha.sosapp.utils

import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.sanraksha.sosapp.R
import com.sanraksha.sosapp.database.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.*

class SOSTriggerManager(private val context: Context) {
    private val appContext = context.applicationContext
    private val database = AppDatabase.getDatabase(appContext)
    private val locationHelper = LocationHelper(appContext)
    private val smsDispatcher = SmsDispatcher(appContext)
    private val pathStore = KidnappingPathStore.getInstance(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var mediaPlayer: MediaPlayer? = null
    private var sosLocationUpdatesJob: Job? = null
    private var kidnappingUpdatesJob: Job? = null
    private val sosLocationHistory = mutableListOf<Pair<Double, Double>>()
    private val kidnappingLocationHistory = mutableListOf<Pair<Double, Double>>()
    private var lastFixLatLon: Pair<Double, Double>? = null
    private var lastFixTimeMs: Long = 0L
    private var lastSpeedKmh: Double = 0.0
    private var lastBearingDeg: Double = 0.0
    private var kidnappingContacts: List<String> = emptyList()
    private var kidnappingUserName: String = ""
    private val maxPathPointsForLink = 20
    private val kidnappingUpdateIntervalMs = 5 * 60 * 1000L
    private val maxChainPointsForSms = 12

    fun isKidnappingTrackingActive(): Boolean {
        return kidnappingUpdatesJob?.isActive == true
    }

    fun triggerSOS(userId: String, userName: String, kidnappingMode: Boolean) {
        scope.launch {
            try {
                // Play siren
                withContext(Dispatchers.Main) { playSiren() }

                // Get contacts
                val contacts = withContext(Dispatchers.IO) {
                    database.contactDao().getContactsByUserId(userId)
                }

                if (contacts.isEmpty()) {
                    stopSiren()
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "No emergency contacts found", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                // Get location
                val locationInfo = getReliableLocationInfo()
                val locationText = if (locationInfo != null) {
                    val location = Pair(locationInfo.latitude, locationInfo.longitude)
                    "Location: ${locationHelper.getLocationString(location.first, location.second)}. " +
                            "Coords: ${formatPlainLocation(locationInfo)}"
                } else {
                    "Location unavailable"
                }

                // Send SMS to all contacts
                val message = "SOS! Emergency alert from $userName. $locationText"
                val phoneNumbers = contacts.map { it.phone }

                withContext(Dispatchers.IO) {
                    smsDispatcher.sendBulk(phoneNumbers, message)
                }
                scheduleOutboxFlush()

                if (kidnappingMode && locationInfo != null) {
                    val location = Pair(locationInfo.latitude, locationInfo.longitude)
                    sosLocationHistory.clear()
                    sosLocationHistory.add(location)
                    startSosLocationUpdates(phoneNumbers, userName)
                }

                // Call first contact
                if (contacts.isNotEmpty()) {
                    makeCall(contacts[0].phone)
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "SOS alerts sent!", Toast.LENGTH_LONG).show()
                }

            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Error sending SOS: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun playSiren() {
        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer.create(context, R.raw.siren_sound)
            mediaPlayer?.isLooping = true
            mediaPlayer?.start()

            // Stop after 30 seconds
            mainHandler.postDelayed({
                stopSiren()
            }, 30000)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun stopSiren() {
        runCatching { mediaPlayer?.stop() }
        mediaPlayer?.release()
        mediaPlayer = null
    }

    fun stopSosLocationUpdates() {
        sosLocationUpdatesJob?.cancel()
        sosLocationUpdatesJob = null
    }

    private fun makeCall(phoneNumber: String) {
        try {
            val intent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:$phoneNumber")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: SecurityException) {
            mainHandler.post {
                Toast.makeText(context, "Call permission denied", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun startSosLocationUpdates(phoneNumbers: List<String>, userName: String) {
        stopSosLocationUpdates()
        sosLocationUpdatesJob = scope.launch {
            val updateIntervalMs = 5 * 60 * 1000L
            val maxUpdates = 6 // 30 minutes total
            repeat(maxUpdates) { index ->
                delay(updateIntervalMs)

            val locationInfo = getReliableLocationInfo()
            if (locationInfo != null) {
                val location = Pair(locationInfo.latitude, locationInfo.longitude)
                addSosPoint(location)
                val pathLink = buildPathLink(sosLocationHistory, maxPathPointsForLink)
                val coords = formatPlainLocation(locationInfo)
                val updateMsg = "SOS Update #${index + 1} from $userName. " +
                        "Location: ${locationHelper.getLocationString(location.first, location.second)}. " +
                        "Coords: $coords. " +
                        "Path: $pathLink"

                withContext(Dispatchers.IO) {
                    smsDispatcher.sendBulk(phoneNumbers, updateMsg)
                }
                scheduleOutboxFlush()
            }
        }
    }
    }

    fun startKidnappingTracking(userId: String, userName: String) {
        stopKidnappingTracking()
        kidnappingUpdatesJob = scope.launch {
            val contacts = withContext(Dispatchers.IO) {
                database.contactDao().getContactsByUserId(userId)
            }
            if (contacts.isEmpty()) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "No emergency contacts found", Toast.LENGTH_SHORT).show()
                }
                return@launch
            }

            val phoneNumbers = contacts.map { it.phone }.take(3)
            kidnappingContacts = phoneNumbers
            kidnappingUserName = userName

            pathStore.startNewSession()
            appContext.sendBroadcast(KidnappingPathEvents.buildResetIntent(appContext))
            val locationInfo = getReliableLocationInfo(freshFirst = true)
            if (locationInfo == null) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Location unavailable", Toast.LENGTH_SHORT).show()
                }
                return@launch
            }
            val location = Pair(locationInfo.latitude, locationInfo.longitude)

            kidnappingLocationHistory.clear()
            addKidnappingPoint(location)
            updateLastFix(location, System.currentTimeMillis())

            val initialPathLink = buildPathLink(kidnappingLocationHistory, maxPathPointsForLink)
            val initialChain = buildLocationChain(kidnappingLocationHistory, maxChainPointsForSms)
            val initialCoords = formatPlainLocation(locationInfo)
            val initialMsg = "Kidnapping Alert from $userName. " +
                    "L1: ${locationHelper.getLocationString(location.first, location.second)}. " +
                    "Coords: $initialCoords. " +
                    "Chain: $initialChain. " +
                    "Path: $initialPathLink"
            withContext(Dispatchers.IO) {
                smsDispatcher.sendBulk(phoneNumbers, initialMsg)
            }
            scheduleOutboxFlush()

            while (true) {
                delay(kidnappingUpdateIntervalMs)
                val newLocationInfo = getReliableLocationInfo(freshFirst = true)
                val newLocation = newLocationInfo?.let { Pair(it.latitude, it.longitude) }

                val nowMs = System.currentTimeMillis()
                val locationToUse = if (newLocation != null) {
                    updateLastFix(newLocation, nowMs)
                    newLocation
                } else {
                    null
                }

                if (locationToUse != null) {
                    addKidnappingPoint(locationToUse)
                    val pathLink = buildPathLink(kidnappingLocationHistory, maxPathPointsForLink)
                    val chain = buildLocationChain(kidnappingLocationHistory, maxChainPointsForSms)
                    val coords = if (newLocationInfo != null) formatPlainLocation(newLocationInfo) else "unknown"
                    val updateMsg = "Kidnapping Update from $userName. " +
                            "Latest: ${locationHelper.getLocationString(locationToUse.first, locationToUse.second)}. " +
                            "Coords: $coords. " +
                            "Chain: $chain. " +
                            "Path: $pathLink"
                    withContext(Dispatchers.IO) {
                        smsDispatcher.sendBulk(phoneNumbers, updateMsg)
                    }
                    scheduleOutboxFlush()
                } else {
                    val pathLink = buildPathLink(kidnappingLocationHistory, maxPathPointsForLink)
                    val chain = buildLocationChain(kidnappingLocationHistory, maxChainPointsForSms)
                    val updateMsg = "Kidnapping Update from $userName. " +
                            "Location unavailable this interval. " +
                            "Chain: $chain. " +
                            "Path: $pathLink"
                    withContext(Dispatchers.IO) {
                        smsDispatcher.sendBulk(phoneNumbers, updateMsg)
                    }
                    scheduleOutboxFlush()
                }
            }
        }
    }

    fun stopKidnappingTracking() {
        val contacts = kidnappingContacts
        val userName = kidnappingUserName
        val historySnapshot = kidnappingLocationHistory.toMutableList()

        kidnappingUpdatesJob?.cancel()
        kidnappingUpdatesJob = null

        if (contacts.isNotEmpty() && historySnapshot.isNotEmpty()) {
            scope.launch {
                val finalHistory = historySnapshot.toMutableList()
                val latest = getReliableLocation(freshFirst = true)
                if (latest != null) {
                    finalHistory.add(latest)
                }
                val finalPath = buildPathLink(finalHistory, maxPathPointsForLink)
                val finalMsg = "Kidnapping Tracking Stopped for $userName. Final Path: $finalPath"
                withContext(Dispatchers.IO) {
                    smsDispatcher.sendBulk(contacts, finalMsg)
                }
                scheduleOutboxFlush()
            }
        }

        kidnappingLocationHistory.clear()
        pathStore.clear()
        appContext.sendBroadcast(KidnappingPathEvents.buildResetIntent(appContext))
        lastFixLatLon = null
        lastFixTimeMs = 0L
        lastSpeedKmh = 0.0
        lastBearingDeg = 0.0
        kidnappingContacts = emptyList()
        kidnappingUserName = ""
    }

    private fun buildPathLink(points: List<Pair<Double, Double>>, maxPoints: Int): String {
        if (points.isEmpty()) return "Location unavailable"
        if (points.size == 1) {
            val (lat, lon) = points[0]
            return locationHelper.getLocationString(lat, lon)
        }

        val trimmed = if (points.size <= maxPoints) {
            points
        } else {
            val tail = points.takeLast(maxPoints - 1)
            listOf(points.first()) + tail
        }

        val origin = "${trimmed.first().first},${trimmed.first().second}"
        val destination = "${trimmed.last().first},${trimmed.last().second}"
        val waypoints = trimmed.subList(1, trimmed.size - 1)
            .joinToString("|") { "${it.first},${it.second}" }

        val waypointsEncoded = URLEncoder.encode(waypoints, "UTF-8")
        return "https://www.google.com/maps/dir/?api=1" +
                "&origin=$origin" +
                "&destination=$destination" +
                (if (waypoints.isNotBlank()) "&waypoints=$waypointsEncoded" else "")
    }

    private fun buildLocationChain(points: List<Pair<Double, Double>>, maxPoints: Int): String {
        if (points.isEmpty()) return "No locations yet"
        val total = points.size
        val subset = if (total <= maxPoints) {
            points
        } else {
            points.takeLast(maxPoints)
        }
        val startIndex = total - subset.size
        val chain = subset.mapIndexed { index, point ->
            val labelIndex = startIndex + index + 1
            val lat = String.format(Locale.US, "%.5f", point.first)
            val lon = String.format(Locale.US, "%.5f", point.second)
            "l$labelIndex($lat,$lon)"
        }.joinToString(" -> ")

        return if (total > maxPoints) {
            "latest ${subset.size} of $total: $chain"
        } else {
            chain
        }
    }

    private fun formatPlainLocation(location: android.location.Location): String {
        val lat = String.format(Locale.US, "%.5f", location.latitude)
        val lon = String.format(Locale.US, "%.5f", location.longitude)
        val accuracy = if (location.hasAccuracy()) {
            "±${location.accuracy.toInt()}m"
        } else {
            "±unknown"
        }
        val time = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(location.time))
        return "$lat,$lon $accuracy @ $time"
    }

    private fun addKidnappingPoint(point: Pair<Double, Double>) {
        kidnappingLocationHistory.add(point)
        // Persist and broadcast so the UI can render a continuous path.
        val pathPoint = PathPoint(point.first, point.second, System.currentTimeMillis())
        pathStore.append(pathPoint)
        appContext.sendBroadcast(KidnappingPathEvents.buildIntent(appContext, pathPoint))
    }

    private fun addSosPoint(point: Pair<Double, Double>) {
        sosLocationHistory.add(point)
    }

    private fun updateLastFix(location: Pair<Double, Double>, nowMs: Long) {
        val prev = lastFixLatLon
        val prevTime = lastFixTimeMs
        if (prev != null && prevTime > 0L && nowMs > prevTime) {
            val distanceKm = haversineKm(prev.first, prev.second, location.first, location.second)
            val hours = (nowMs - prevTime) / 3600000.0
            if (hours > 0) {
                lastSpeedKmh = distanceKm / hours
                lastBearingDeg = bearingDeg(prev.first, prev.second, location.first, location.second)
            }
        }
        lastFixLatLon = location
        lastFixTimeMs = nowMs
    }

    private fun estimateLocation(nowMs: Long): Pair<Double, Double>? {
        val base = lastFixLatLon ?: return null
        if (lastFixTimeMs <= 0L) return null
        if (lastSpeedKmh <= 0.1) return null
        val elapsedHours = (nowMs - lastFixTimeMs) / 3600000.0
        if (elapsedHours <= 0) return null
        val distanceKm = lastSpeedKmh * elapsedHours
        return destinationPoint(base.first, base.second, lastBearingDeg, distanceKm)
    }

    private fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }

    private fun bearingDeg(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val y = sin(Math.toRadians(lon2 - lon1)) * cos(Math.toRadians(lat2))
        val x = cos(Math.toRadians(lat1)) * sin(Math.toRadians(lat2)) -
                sin(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                cos(Math.toRadians(lon2 - lon1))
        val brng = atan2(y, x)
        return (Math.toDegrees(brng) + 360) % 360
    }

    private fun destinationPoint(lat: Double, lon: Double, bearingDeg: Double, distanceKm: Double): Pair<Double, Double> {
        val r = 6371.0
        val d = distanceKm / r
        val brng = Math.toRadians(bearingDeg)
        val lat1 = Math.toRadians(lat)
        val lon1 = Math.toRadians(lon)

        val lat2 = asin(sin(lat1) * cos(d) + cos(lat1) * sin(d) * cos(brng))
        val lon2 = lon1 + atan2(
            sin(brng) * sin(d) * cos(lat1),
            cos(d) - sin(lat1) * sin(lat2)
        )
        return Pair(Math.toDegrees(lat2), Math.toDegrees(lon2))
    }

    private suspend fun getReliableLocation(
        freshFirst: Boolean = false,
        maxAttempts: Int = 3
    ): Pair<Double, Double>? {
        // Retry with exponential backoff to tolerate transient GPS/network failures.
        var delayMs = 1_000L
        repeat(maxAttempts) { attempt ->
            val location = withContext(Dispatchers.IO) {
                if (freshFirst) {
                    locationHelper.getFreshLocation() ?: locationHelper.getCurrentLocation()
                } else {
                    locationHelper.getCurrentLocation() ?: locationHelper.getFreshLocation()
                }
            }
            if (location != null) return location
            if (attempt < maxAttempts - 1) {
                delay(delayMs)
                delayMs = (delayMs * 2).coerceAtMost(8_000L)
            }
        }
        return null
    }

    private suspend fun getReliableLocationInfo(
        freshFirst: Boolean = false,
        maxAttempts: Int = 3
    ): android.location.Location? {
        var delayMs = 1_000L
        repeat(maxAttempts) { attempt ->
            val location = withContext(Dispatchers.IO) {
                if (freshFirst) {
                    locationHelper.getFreshLocationInfo() ?: locationHelper.getCurrentLocationInfo()
                } else {
                    locationHelper.getCurrentLocationInfo() ?: locationHelper.getFreshLocationInfo()
                }
            }
            if (location != null) return location
            if (attempt < maxAttempts - 1) {
                delay(delayMs)
                delayMs = (delayMs * 2).coerceAtMost(8_000L)
            }
        }
        return null
    }

    private fun scheduleOutboxFlush() {
        scope.launch {
            smsDispatcher.flushOutbox(10)
        }
    }

    fun shutdown() {
        stopSiren()
        stopSosLocationUpdates()
        stopKidnappingTracking()
        scope.cancel()
    }
}
