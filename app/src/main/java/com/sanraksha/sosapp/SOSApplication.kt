package com.sanraksha.sosapp

import android.app.Application
import com.sanraksha.sosapp.utils.ThemeUtils

class SOSApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ThemeUtils.applySavedTheme(this)
    }
}
