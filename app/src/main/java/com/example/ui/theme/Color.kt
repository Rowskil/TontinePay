package com.example.ui.theme

import androidx.compose.ui.graphics.Color

enum class DisplayMode(val labelFr: String, val description: String) {
    SYSTEM("Système", "S'adapte automatiquement aux réglages du téléphone"),
    LIGHT("Clair Épuré", "Fond clair et aéré"),
    DARK("Sombre", "Interface sombre reposante pour les yeux");

    companion object {
        val AUTO = SYSTEM
        val CLEAN_LIGHT = LIGHT
        val DEEP_NIGHT = DARK
        val AMOLED = DARK
        val GRAPHITE = DARK
    }
}

enum class AccentTheme(val labelFr: String, val primary: Color, val container: Color) {
    NEUTRAL("Neutre", Color(0xFF475569), Color(0xFFE2E8F0)),
    EMERALD("Émeraude", Color(0xFF10B981), Color(0xFFD1FAE5)),
    AMBER("Or d'Afrique", Color(0xFFF59E0B), Color(0xFFFEF3C7)),
    WAVE("Wave Cyan", Color(0xFF06B6D4), Color(0xFFCFFAFE)),
    INDIGO("Indigo Royal", Color(0xFF6366F1), Color(0xFFE0E7FF)),
    CORAL("Corail", Color(0xFFF43F5E), Color(0xFFFFE4E6))
}

// Default Primary FinTech Greens
val EmeraldPrimary = Color(0xFF10B981)
val EmeraldOnPrimary = Color(0xFF022C22)
val EmeraldContainer = Color(0xFF064E3B)
val EmeraldOnContainer = Color(0xFFA7F3D0)

// Secondary Warm Gold / Amber
val AmberGold = Color(0xFFF59E0B)
val AmberGoldContainer = Color(0xFF78350F)
val AmberGoldOnContainer = Color(0xFFFDE68A)

// Wave Cyan
val WaveCyan = Color(0xFF06B6D4)
val WaveCyanContainer = Color(0xFF164E63)

// Status Colors
val StatusValidatedGreen = Color(0xFF10B981)
val StatusPendingYellow = Color(0xFFF59E0B)
val StatusUnpaidRed = Color(0xFFEF4444)
val StatusRejectedGray = Color(0xFF94A3B8)

// Dark Surfaces (#0F172A / #090D16)
val DeepNightBg = Color(0xFF090D16)
val DeepNightSurface = Color(0xFF111827)
val DeepNightSurfaceVariant = Color(0xFF1F2937)
val DeepNightBorder = Color(0xFF374151)
val DeepNightTextPrimary = Color(0xFFF9FAFB)
val DeepNightTextSecondary = Color(0xFF9CA3AF)

// Clean Light Slate Gray Surfaces (#F8FAFC)
val LightBg = Color(0xFFF8FAFC)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFF1F5F9)
val LightSurfaceElevated = Color(0xFFFFFFFF)
val LightBorder = Color(0xFFE2E8F0)
val LightTextPrimary = Color(0xFF0F172A)
val LightTextSecondary = Color(0xFF475569)
val LightTextMuted = Color(0xFF94A3B8)

// Legacy aliases for backward compatibility
val DarkBg = DeepNightBg
val DarkSurface = DeepNightSurface
val DarkSurfaceVariant = DeepNightSurfaceVariant
val DarkSurfaceElevated = Color(0xFF1E293B)
val DarkBorder = DeepNightBorder
val DarkTextPrimary = DeepNightTextPrimary
val DarkTextSecondary = DeepNightTextSecondary
val DarkTextMuted = Color(0xFF64748B)

val TextPrimary = LightTextPrimary
val TextSecondary = LightTextSecondary
val TextMuted = LightTextMuted
