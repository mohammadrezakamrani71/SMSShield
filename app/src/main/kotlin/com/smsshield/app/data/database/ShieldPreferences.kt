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

data class ListenerDiagnostics(
    val isListenerConnected: Boolean = false,
    val lastSmsPackage: String = "",
    val lastSmsTimestamp: Long = 0L,
    val lastSmsOutcome: String = "",
    val totalSmsNotificationsSeen: Int = 0
)

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

    private val _diagnostics = MutableStateFlow(getDiagnostics())
    val diagnosticsFlow: StateFlow<ListenerDiagnostics> = _diagnostics.asStateFlow()

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

    fun getDiagnostics(): ListenerDiagnostics = ListenerDiagnostics(
        isListenerConnected = prefs.getBoolean(KEY_LISTENER_CONNECTED, false),
        lastSmsPackage = prefs.getString(KEY_LAST_SMS_PACKAGE, "").orEmpty(),
        lastSmsTimestamp = prefs.getLong(KEY_LAST_SMS_TIMESTAMP, 0L),
        lastSmsOutcome = prefs.getString(KEY_LAST_SMS_OUTCOME, "").orEmpty(),
        totalSmsNotificationsSeen = prefs.getInt(KEY_TOTAL_SMS_SEEN, 0)
    )

    fun setListenerConnected(connected: Boolean) {
        prefs.edit().putBoolean(KEY_LISTENER_CONNECTED, connected).apply()
        _diagnostics.value = _diagnostics.value.copy(isListenerConnected = connected)
    }

    fun recordSmsDiagnostic(
        packageName: String,
        timestamp: Long,
        outcome: String
    ) {
        val updatedCount = prefs.getInt(KEY_TOTAL_SMS_SEEN, 0) + 1
        prefs.edit()
            .putString(KEY_LAST_SMS_PACKAGE, packageName)
            .putLong(KEY_LAST_SMS_TIMESTAMP, timestamp)
            .putString(KEY_LAST_SMS_OUTCOME, outcome)
            .putInt(KEY_TOTAL_SMS_SEEN, updatedCount)
            .apply()
        _diagnostics.value = _diagnostics.value.copy(
            lastSmsPackage = packageName,
            lastSmsTimestamp = timestamp,
            lastSmsOutcome = outcome,
            totalSmsNotificationsSeen = updatedCount
        )
    }

    companion object {
        private const val PREFS_NAME = "smsshield_prefs"
        private const val KEY_ACTIVE_MODE = "is_active_mode"
        private const val KEY_SPAM_THRESHOLD = "spam_threshold"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_LISTENER_CONNECTED = "diag_listener_connected"
        private const val KEY_LAST_SMS_PACKAGE = "diag_last_sms_package"
        private const val KEY_LAST_SMS_TIMESTAMP = "diag_last_sms_timestamp"
        private const val KEY_LAST_SMS_OUTCOME = "diag_last_sms_outcome"
        private const val KEY_TOTAL_SMS_SEEN = "diag_total_sms_seen"
    }
}
