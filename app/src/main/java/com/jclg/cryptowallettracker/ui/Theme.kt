package com.jclg.cryptowallettracker.ui

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.jclg.cryptowallettracker.data.ThemeMode

private val LightColors = lightColorScheme(
    primary = Color(0xFF9A6216),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF2D8A4),
    onPrimaryContainer = Color(0xFF3A2405),
    secondary = Color(0xFF6E5A3D),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEADCC3),
    onSecondaryContainer = Color(0xFF2A1F10),
    background = Color(0xFFFAF3E7),
    onBackground = Color(0xFF241C12),
    surface = Color(0xFFFFFAF1),
    onSurface = Color(0xFF241C12),
    surfaceVariant = Color(0xFFEDE0CB),
    onSurfaceVariant = Color(0xFF6B5C46),
    outline = Color(0xFFB9A98F),
    surfaceContainerLowest = Color(0xFFFFFDF8),
    surfaceContainerLow = Color(0xFFFBF3E5),
    surfaceContainer = Color(0xFFF6EBD9),
    surfaceContainerHigh = Color(0xFFF1E4CE),
    surfaceContainerHighest = Color(0xFFEBDCC2),
    error = Color(0xFFB3261E),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFE3B264),
    onPrimary = Color(0xFF3A2405),
    primaryContainer = Color(0xFF4A3313),
    onPrimaryContainer = Color(0xFFF6E2BE),
    secondary = Color(0xFFD6C4A4),
    onSecondary = Color(0xFF352A1A),
    secondaryContainer = Color(0xFF3A2F1F),
    onSecondaryContainer = Color(0xFFEDE0CB),
    background = Color(0xFF120E08),
    onBackground = Color(0xFFEDE3D4),
    surface = Color(0xFF1C160E),
    onSurface = Color(0xFFEDE3D4),
    surfaceVariant = Color(0xFF2B2318),
    onSurfaceVariant = Color(0xFFC8B99F),
    outline = Color(0xFF8A7A61),
    surfaceContainerLowest = Color(0xFF0D0A06),
    surfaceContainerLow = Color(0xFF181208),
    surfaceContainer = Color(0xFF1E170E),
    surfaceContainerHigh = Color(0xFF251D12),
    surfaceContainerHighest = Color(0xFF2E2417),
    error = Color(0xFFF2B8B5),
)

@Composable
fun AppTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, content = content)
}
