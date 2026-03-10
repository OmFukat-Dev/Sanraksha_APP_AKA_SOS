package com.sanraksha.sosapp.utils

import android.content.Context
import android.content.SharedPreferences

class PrefManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("SOSAppPrefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_LOGGED_IN = "logged_in"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_SAFETY_MODE = "safety_mode"
        private const val KEY_SHAKE_ENABLED = "shake_enabled"
        private const val KEY_VOICE_ENABLED = "voice_enabled"
        private const val KEY_SOUND_ENABLED = "sound_enabled"
        private const val KEY_SHAKE_SENSITIVITY = "shake_sensitivity"
        private const val KEY_DARK_MODE = "dark_mode"
        private const val KEY_SOS_MODE = "sos_mode"
        private const val KEY_BLOCK_SCREENSHOTS = "block_screenshots"
        private const val KEY_HIDE_SENSITIVE_INFO = "hide_sensitive_info"
        private const val KEY_REQUIRE_SOS_CONFIRMATION = "require_sos_confirmation"
    }

    var isLoggedIn: Boolean
        get() = prefs.getBoolean(KEY_LOGGED_IN, false)
        set(value) = prefs.edit().putBoolean(KEY_LOGGED_IN, value).apply()

    var userId: String?
        get() = prefs.getString(KEY_USER_ID, null)
        set(value) = prefs.edit().putString(KEY_USER_ID, value).apply()

    var safetyMode: Boolean
        get() = prefs.getBoolean(KEY_SAFETY_MODE, false)
        set(value) = prefs.edit().putBoolean(KEY_SAFETY_MODE, value).apply()

    var shakeEnabled: Boolean
        get() = prefs.getBoolean(KEY_SHAKE_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_SHAKE_ENABLED, value).apply()

    var voiceEnabled: Boolean
        get() = prefs.getBoolean(KEY_VOICE_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_VOICE_ENABLED, value).apply()

    var soundEnabled: Boolean
        get() = prefs.getBoolean(KEY_SOUND_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_SOUND_ENABLED, value).apply()

    var shakeSensitivity: Int
        get() = prefs.getInt(KEY_SHAKE_SENSITIVITY, 1) // 0=Low, 1=Medium, 2=High
        set(value) = prefs.edit().putInt(KEY_SHAKE_SENSITIVITY, value).apply()

    var darkMode: Boolean
        get() = prefs.getBoolean(KEY_DARK_MODE, false)
        set(value) = prefs.edit().putBoolean(KEY_DARK_MODE, value).apply()

    var sosMode: Boolean
        get() = prefs.getBoolean(KEY_SOS_MODE, false)
        set(value) = prefs.edit().putBoolean(KEY_SOS_MODE, value).apply()

    var blockScreenshots: Boolean
        get() = prefs.getBoolean(KEY_BLOCK_SCREENSHOTS, true)
        set(value) = prefs.edit().putBoolean(KEY_BLOCK_SCREENSHOTS, value).apply()

    var hideSensitiveInfo: Boolean
        get() = prefs.getBoolean(KEY_HIDE_SENSITIVE_INFO, true)
        set(value) = prefs.edit().putBoolean(KEY_HIDE_SENSITIVE_INFO, value).apply()

    var requireSosConfirmation: Boolean
        get() = prefs.getBoolean(KEY_REQUIRE_SOS_CONFIRMATION, true)
        set(value) = prefs.edit().putBoolean(KEY_REQUIRE_SOS_CONFIRMATION, value).apply()

    fun logout() {
        prefs.edit().clear().apply()
    }
}
