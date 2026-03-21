package com.sanraksha.sosapp.utils

import android.content.Context
import android.content.Intent

object KidnappingPathEvents {
    const val ACTION_NEW_POINT = "com.sanraksha.sosapp.action.KIDNAPPING_PATH_POINT"
    const val ACTION_RESET = "com.sanraksha.sosapp.action.KIDNAPPING_PATH_RESET"
    const val EXTRA_LAT = "extra_lat"
    const val EXTRA_LON = "extra_lon"
    const val EXTRA_TIME_MS = "extra_time_ms"

    fun buildIntent(context: Context, point: PathPoint): Intent {
        return Intent(ACTION_NEW_POINT).apply {
            setPackage(context.packageName)
            putExtra(EXTRA_LAT, point.lat)
            putExtra(EXTRA_LON, point.lon)
            putExtra(EXTRA_TIME_MS, point.timestampMs)
        }
    }

    fun buildResetIntent(context: Context): Intent {
        return Intent(ACTION_RESET).apply {
            setPackage(context.packageName)
        }
    }
}
