package com.sanraksha.sosapp

import android.app.Application
import com.sanraksha.sosapp.utils.ThemeUtils
import org.osmdroid.config.Configuration
import java.io.File

class SOSApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ThemeUtils.applySavedTheme(this)

        val config = Configuration.getInstance()
        config.userAgentValue = packageName
        val basePath = File(cacheDir, "osmdroid")
        val tileCache = File(basePath, "tiles")
        config.osmdroidBasePath = basePath
        config.osmdroidTileCache = tileCache
    }
}
