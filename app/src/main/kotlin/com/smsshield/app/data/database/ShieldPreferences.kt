package com.smsshield.app.data.database

import android.content.Context
import com.smsshield.engine.SpamRules
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

class ShieldPreferences(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    private val _isActiveMode = MutableStateFlow(isActiveMode())
    val isActiveModeFlow: StateFlow<Boolean> = _isActiveMode.asStateFlow()

    private val _spamThreshold = MutableStateFlow(getSpamThreshold())
    val spamThresholdFlow: StateFlow<Int> = _spamThreshold.asStateFlow()

    private val _themeMode = MutableStateFlow(getThemeMode())
    val themeModeFlow: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    fun isActiveMode(): Boolean = prefs.getBoolean(KEY_ACTIVE_MODE, false)

    fun setActiveMode(active: Boolean) {
        prefs.edit().putBoolean(KEY_ACTIVE_MODE, active).apply()
        _isActiveMode.value = active
    }

    fun getSpamThreshold(): Int =
        prefs.getInt(KEY_SPAM_THRESHOLD, SpamRules.DEFAULT_SPAM_THRESHOLD)
            .coerceIn(50, 100)

    fun setSpamThreshold(threshold: Int) {
        val clamped = threshold.coerceIn(50, 100)
        prefs.edit().putInt(KEY_SPAM_THRESHOLD, clamped).apply()
        _spamThreshold.value = clamped
    }

    fun getThemeMode(): ThemeMode {
        val stored = prefs.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name
        return runCatching { ThemeMode.valueOf(stored) }.getOrDefault(ThemeMode.SYSTEM)
    }

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
        _themeMode.value = mode
    }

    companion object {
        private const val PREFS_NAME = "smsshield_prefs"
        private const val KEY_ACTIVE_MODE = "is_active_mode"
        private const val KEY_SPAM_THRESHOLD = "spam_threshold"
        private const val KEY_THEME_MODE = "theme_mode"
    }
}
