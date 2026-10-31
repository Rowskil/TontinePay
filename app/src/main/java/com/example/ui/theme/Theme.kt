package com.example.ui.theme

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

@Composable
fun MyApplicationTheme(
    displayMode: DisplayMode = DisplayMode.SYSTEM,
    darkTheme: Boolean = false,
    accentTheme: AccentTheme = AccentTheme.EMERALD,
    content: @Composable () -> Unit
) {
    val systemInDark = isSystemInDarkTheme()

    val isDark = when (displayMode) {
        DisplayMode.LIGHT -> false
        DisplayMode.DARK -> true
        DisplayMode.SYSTEM -> systemInDark
    }

    val colorScheme = if (isDark) {
        darkColorScheme(
            primary = accentTheme.primary,
            onPrimary = if (accentTheme == AccentTheme.NEUTRAL) Color.White else Color(0xFF022C22),
            primaryContainer = accentTheme.primary.copy(alpha = 0.22f),
            onPrimaryContainer = Color(0xFFA7F3D0),
            secondary = AmberGold,
            onSecondary = DeepNightBg,
            secondaryContainer = AmberGoldContainer,
            onSecondaryContainer = AmberGoldOnContainer,
            tertiary = WaveCyan,
            background = DeepNightBg,
            surface = DeepNightSurface,
            surfaceVariant = DeepNightSurfaceVariant,
            onBackground = DeepNightTextPrimary,
            onSurface = DeepNightTextPrimary,
            onSurfaceVariant = DeepNightTextSecondary,
            outline = DeepNightBorder,
            outlineVariant = Color(0xFF1F2937),
            error = StatusUnpaidRed
        )
    } else {
        lightColorScheme(
            primary = accentTheme.primary,
            onPrimary = Color.White,
            primaryContainer = accentTheme.container,
            onPrimaryContainer = accentTheme.primary,
            secondary = AmberGold,
            onSecondary = Color.White,
            secondaryContainer = Color(0xFFFEF3C7),
            onSecondaryContainer = Color(0xFF92400E),
            tertiary = WaveCyan,
            background = LightBg,
            surface = LightSurface,
            surfaceVariant = LightSurfaceVariant,
            onBackground = LightTextPrimary,
            onSurface = LightTextPrimary,
            onSurfaceVariant = LightTextSecondary,
            outline = LightBorder,
            outlineVariant = Color(0xFFCBD5E1),
            error = StatusUnpaidRed
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val insets = WindowCompat.getInsetsController(window, view)
            insets.isAppearanceLightStatusBars = !isDark
            insets.isAppearanceLightNavigationBars = !isDark
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
