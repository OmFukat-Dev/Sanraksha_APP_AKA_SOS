package com.sanraksha.sosapp.utils

import android.Manifest
import android.app.Activity
import android.os.Build
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

object PermissionHelper {
    const val PERMISSION_REQUEST_CODE = 100
    const val LOCATION_PERMISSION_REQUEST_CODE = 101

    private fun monitoringPermissions(needsMicrophone: Boolean): Array<String> {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.SEND_SMS
        )
        if (needsMicrophone) {
            permissions.add(Manifest.permission.RECORD_AUDIO)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        return permissions.toTypedArray()
    }

    fun hasRequiredMonitoringPermissions(activity: Activity, needsMicrophone: Boolean): Boolean {
        val hasSms = hasPermission(activity, Manifest.permission.SEND_SMS)
        val hasMic = !needsMicrophone || hasPermission(activity, Manifest.permission.RECORD_AUDIO)
        return checkLocationPermissions(activity) && hasSms && hasMic
    }

    fun requestMonitoringPermissions(activity: Activity, needsMicrophone: Boolean) {
        ActivityCompat.requestPermissions(
            activity,
            monitoringPermissions(needsMicrophone),
            PERMISSION_REQUEST_CODE
        )
    }

    fun checkLocationPermissions(activity: Activity): Boolean {
        val fineGranted = ContextCompat.checkSelfPermission(
            activity,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(
            activity,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fineGranted || coarseGranted
    }

    fun requestLocationPermissions(activity: Activity) {
        ActivityCompat.requestPermissions(
            activity,
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ),
            LOCATION_PERMISSION_REQUEST_CODE
        )
    }

    fun hasPermission(activity: Activity, permission: String): Boolean {
        return ContextCompat.checkSelfPermission(activity, permission) ==
                PackageManager.PERMISSION_GRANTED
    }
}
