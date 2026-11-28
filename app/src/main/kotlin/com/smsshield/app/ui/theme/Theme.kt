package com.smsshield.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.smsshield.app.data.database.ThemeMode

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF1B5E20),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD7F2DA),
    onPrimaryContainer = Color(0xFF062109),
    secondary = Color(0xFF00695C),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFC8F0EA),
    onSecondaryContainer = Color(0xFF00201B),
    tertiary = Color(0xFF8C5319),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFE5CC),
    onTertiaryContainer = Color(0xFF2D1600),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    background = Color(0xFFF7FAF7),
    onBackground = Color(0xFF191C19),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF191C19),
    surfaceVariant = Color(0xFFE6ECE6),
    onSurfaceVariant = Color(0xFF424942)
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF81C784),
    onPrimary = Color(0xFF00390F),
    primaryContainer = Color(0xFF1B4321),
    onPrimaryContainer = Color(0xFFD7F2DA),
    secondary = Color(0xFF80CBC4),
    onSecondary = Color(0xFF003731),
    secondaryContainer = Color(0xFF134B45),
    onSecondaryContainer = Color(0xFFC8F0EA),
    tertiary = Color(0xFFFFB77C),
    onTertiary = Color(0xFF4A2800),
    tertiaryContainer = Color(0xFF5D3A14),
    onTertiaryContainer = Color(0xFFFFE5CC),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
    background = Color(0xFF101411),
    onBackground = Color(0xFFE2E6E2),
    surface = Color(0xFF171D18),
    onSurface = Color(0xFFE2E6E2),
    surfaceVariant = Color(0xFF252D26),
    onSurfaceVariant = Color(0xFFC2CBC2)
)

@Composable
fun SMSShieldTheme(
    themeMode: ThemeMode,
    content: @Composable () -> Unit
) {
    val useDarkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    MaterialTheme(
        colorScheme = if (useDarkTheme) DarkColorScheme else LightColorScheme,
        content = content
    )
}
