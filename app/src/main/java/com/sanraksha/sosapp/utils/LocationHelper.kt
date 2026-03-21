package com.sanraksha.sosapp.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.annotation.SuppressLint
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

class LocationHelper(private val context: Context) {
    private val locationManager: LocationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private var activeListener: LocationListener? = null

    suspend fun getFreshLocation(timeoutMs: Long = DEFAULT_TIMEOUT_MS): Pair<Double, Double>? {
        return try {
            val hasFine = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            val hasCoarse = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasFine && !hasCoarse) {
                return null
            }

            val freshLocation = withTimeoutOrNull(timeoutMs) {
                requestSingleUpdate()
            }
            freshLocation?.let {
                Pair(it.latitude, it.longitude)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun getFreshLocationInfo(timeoutMs: Long = DEFAULT_TIMEOUT_MS): Location? {
        return try {
            val hasFine = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            val hasCoarse = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasFine && !hasCoarse) {
                return null
            }

            withTimeoutOrNull(timeoutMs) {
                requestSingleUpdate()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun getCurrentLocation(timeoutMs: Long = DEFAULT_TIMEOUT_MS): Pair<Double, Double>? {
        return try {
            val hasFine = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            val hasCoarse = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasFine && !hasCoarse) {
                return null
            }

            val bestLastKnown = listOf(
                LocationManager.GPS_PROVIDER,
                LocationManager.NETWORK_PROVIDER
            ).mapNotNull { provider ->
                locationManager.getLastKnownLocation(provider)
            }.maxByOrNull { it.time }

            if (bestLastKnown != null) {
                return Pair(bestLastKnown.latitude, bestLastKnown.longitude)
            }

            val freshLocation = withTimeoutOrNull(timeoutMs) {
                requestSingleUpdate()
            }
            freshLocation?.let {
                Pair(it.latitude, it.longitude)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun getCurrentLocationInfo(timeoutMs: Long = DEFAULT_TIMEOUT_MS): Location? {
        return try {
            val hasFine = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            val hasCoarse = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasFine && !hasCoarse) {
                return null
            }

            val bestLastKnown = listOf(
                LocationManager.GPS_PROVIDER,
                LocationManager.NETWORK_PROVIDER
            ).mapNotNull { provider ->
                locationManager.getLastKnownLocation(provider)
            }.maxByOrNull { it.time }

            if (bestLastKnown != null) {
                return bestLastKnown
            }

            withTimeoutOrNull(timeoutMs) {
                requestSingleUpdate()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun requestSingleUpdate(): Location? = suspendCancellableCoroutine { cont ->
        val provider = when {
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ->
                LocationManager.GPS_PROVIDER
            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) ->
                LocationManager.NETWORK_PROVIDER
            else -> {
                cont.resume(null)
                return@suspendCancellableCoroutine
            }
        }

        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                locationManager.removeUpdates(this)
                if (cont.isActive) cont.resume(location)
            }

            override fun onProviderDisabled(provider: String) {}
            override fun onProviderEnabled(provider: String) {}
            @Deprecated("Deprecated in Android")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        }

        try {
            locationManager.requestLocationUpdates(
                provider,
                0L,
                0f,
                listener,
                Looper.getMainLooper()
            )
        } catch (e: SecurityException) {
            cont.resume(null)
            return@suspendCancellableCoroutine
        }

        cont.invokeOnCancellation {
            locationManager.removeUpdates(listener)
        }
    }

    fun startLocationUpdates(
        minTimeMs: Long = DEFAULT_UPDATE_INTERVAL_MS,
        minDistanceMeters: Float = DEFAULT_MIN_DISTANCE_M,
        callback: (Location) -> Unit
    ): Boolean {
        val hasFine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasFine && !hasCoarse) {
            return false
        }

        val providers = mutableListOf<String>()
        if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            providers.add(LocationManager.GPS_PROVIDER)
        }
        if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            providers.add(LocationManager.NETWORK_PROVIDER)
        }
        if (providers.isEmpty()) {
            return false
        }

        if (activeListener != null) {
            return true
        }

        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                callback(location)
            }

            override fun onProviderDisabled(provider: String) {}
            override fun onProviderEnabled(provider: String) {}
            @Deprecated("Deprecated in Android")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        }

        try {
            providers.forEach { provider ->
                requestUpdatesInternal(provider, minTimeMs, minDistanceMeters, listener)
            }
        } catch (e: SecurityException) {
            activeListener = null
            return false
        }
        activeListener = listener
        return true
    }

    @SuppressLint("MissingPermission")
    private fun requestUpdatesInternal(
        provider: String,
        minTimeMs: Long,
        minDistanceMeters: Float,
        listener: LocationListener
    ) {
        locationManager.requestLocationUpdates(
            provider,
            minTimeMs,
            minDistanceMeters,
            listener,
            Looper.getMainLooper()
        )
    }

    fun stopLocationUpdates() {
        activeListener?.let { listener ->
            locationManager.removeUpdates(listener)
        }
        activeListener = null
    }

    fun getLocationString(lat: Double, lon: Double): String {
        return "https://maps.google.com/?q=$lat,$lon"
    }

    companion object {
        private const val DEFAULT_TIMEOUT_MS = 8_000L
        private const val DEFAULT_UPDATE_INTERVAL_MS = 2_000L
        private const val DEFAULT_MIN_DISTANCE_M = 3f
    }
}
