package com.pockethub.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * All PocketHub palettes. Each style defines one full scheme; light styles also
 * flip the status-bar icons via [AppStyleDef.isDark].
 */

// ── Linear dark (original) ───────────────────────────────────────────────────
val LinearDarkColors: ColorScheme = darkColorScheme(
    primary = Color(0xFF7C8BFF), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF2A3140), onPrimaryContainer = Color(0xFFD6DEFF),
    secondary = Color(0xFF8FAEFF), onSecondary = Color(0xFF0E1116),
    secondaryContainer = Color(0xFF1E2230), onSecondaryContainer = Color(0xFFC0C9FF),
    tertiary = Color(0xFF5BC8A8), onTertiary = Color(0xFF001A14),
    tertiaryContainer = Color(0xFF1B2D2A), onTertiaryContainer = Color(0xFF9AEFDE),
    background = Color(0xFF0B0E14), onBackground = Color(0xFFE7EAEE),
    surface = Color(0xFF11131A), onSurface = Color(0xFFE7EAEE),
    surfaceVariant = Color(0xFF1A1D26), onSurfaceVariant = Color(0xFFB3B8C3),
    surfaceTint = Color(0xFF7C8BFF),
    inverseSurface = Color(0xFFE7EAEE), inverseOnSurface = Color(0xFF0B0E14),
    error = Color(0xFFE75B5B), onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFF2A1518), onErrorContainer = Color(0xFFFFD0D0),
    outline = Color(0xFF4A4F5A), outlineVariant = Color(0xFF2A2D38),
    scrim = Color(0xFF000000),
)

// ── Primer light (original) ──────────────────────────────────────────────────
val PrimerLightColors: ColorScheme = lightColorScheme(
    primary = Color(0xFF0969DA), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDDEEFF), onPrimaryContainer = Color(0xFF07418A),
    secondary = Color(0xFF57606A), onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFEAEEF2), onSecondaryContainer = Color(0xFF2F363D),
    tertiary = Color(0xFF1A7F37), onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD6F3DE), onTertiaryContainer = Color(0xFF0B5A26),
    background = Color(0xFFFFFFFF), onBackground = Color(0xFF1F2328),
    surface = Color(0xFFFFFFFF), onSurface = Color(0xFF1F2328),
    surfaceVariant = Color(0xFFEFF1F4), onSurfaceVariant = Color(0xFF57606A),
    surfaceTint = Color(0xFF0969DA),
    inverseSurface = Color(0xFF1F2328), inverseOnSurface = Color(0xFFE7EAEE),
    error = Color(0xFFCF222E), onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFE3E3), onErrorContainer = Color(0xFF6E0E16),
    outline = Color(0xFF8C959F), outlineVariant = Color(0xFFD0D7DE),
    scrim = Color(0xFF000000),
)

// ── Paper — Solarized-inspired warm reading theme ───────────────────────────
val PaperColors: ColorScheme = lightColorScheme(
    primary = Color(0xFF8A6D3B), onPrimary = Color(0xFFFFF8EC),
    primaryContainer = Color(0xFFEADFC3), onPrimaryContainer = Color(0xFF5C4718),
    secondary = Color(0xFF6E7B52), onSecondary = Color(0xFFFFFDF5),
    secondaryContainer = Color(0xFFE3E4CC), onSecondaryContainer = Color(0xFF424A2E),
    tertiary = Color(0xFFB05C4A), onTertiary = Color(0xFFFFF6F0),
    tertiaryContainer = Color(0xFFF0D2C6), onTertiaryContainer = Color(0xFF6E2F22),
    background = Color(0xFFFAF3E3), onBackground = Color(0xFF4A4030),
    surface = Color(0xFFF6EEDA), onSurface = Color(0xFF4A4030),
    surfaceVariant = Color(0xFFECE2C8), onSurfaceVariant = Color(0xFF6E6248),
    surfaceTint = Color(0xFF8A6D3B),
    inverseSurface = Color(0xFF3A3226), inverseOnSurface = Color(0xFFF1E8D2),
    error = Color(0xFFAE3B2C), onError = Color(0xFFFFF5F0),
    errorContainer = Color(0xFFF2CFC5), onErrorContainer = Color(0xFF6E1F14),
    outline = Color(0xFFA8946E), outlineVariant = Color(0xFFD8CBAA),
    scrim = Color(0xFF2A2416),
)

// ── Neon — cyber terminal on pure black ─────────────────────────────────────
val NeonColors: ColorScheme = darkColorScheme(
    primary = Color(0xFF00E5FF), onPrimary = Color(0xFF001417),
    primaryContainer = Color(0xFF00282E), onPrimaryContainer = Color(0xFF7DF3FF),
    secondary = Color(0xFFFF2FD6), onSecondary = Color(0xFF1A0014),
    secondaryContainer = Color(0xFF2E0026), onSecondaryContainer = Color(0xFFFF8FEA),
    tertiary = Color(0xFF7CFF4F), onTertiary = Color(0xFF0B1A00),
    tertiaryContainer = Color(0xFF143000), onTertiaryContainer = Color(0xFFAEFF94),
    background = Color(0xFF000000), onBackground = Color(0xFFD8F6FF),
    surface = Color(0xFF05070A), onSurface = Color(0xFFD8F6FF),
    surfaceVariant = Color(0xFF0A1016), onSurfaceVariant = Color(0xFF7FB3C4),
    surfaceTint = Color(0xFF00E5FF),
    inverseSurface = Color(0xFFD8F6FF), inverseOnSurface = Color(0xFF000000),
    error = Color(0xFFFF2F5E), onError = Color(0xFF1A0008),
    errorContainer = Color(0xFF2E0010), onErrorContainer = Color(0xFFFF8FA8),
    outline = Color(0xFF1F5A66), outlineVariant = Color(0xFF0E2830),
    scrim = Color(0xFF000000),
)

// ── Lavender — soft violet pastel dream ─────────────────────────────────────
val LavenderColors: ColorScheme = lightColorScheme(
    primary = Color(0xFF7B5CE0), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE8DEFF), onPrimaryContainer = Color(0xFF3D2590),
    secondary = Color(0xFFD05CB8), onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFBD9F2), onSecondaryContainer = Color(0xFF6E1E5E),
    tertiary = Color(0xFF4FA8C8), onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD2F0FB), onTertiaryContainer = Color(0xFF14566E),
    background = Color(0xFFFBF7FF), onBackground = Color(0xFF3A3148),
    surface = Color(0xFFF8F2FE), onSurface = Color(0xFF3A3148),
    surfaceVariant = Color(0xFFEFE6FA), onSurfaceVariant = Color(0xFF6B5E84),
    surfaceTint = Color(0xFF7B5CE0),
    inverseSurface = Color(0xFF322A42), inverseOnSurface = Color(0xFFF3EBFF),
    error = Color(0xFFD84A6E), onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFBDCE6), onErrorContainer = Color(0xFF6E1636),
    outline = Color(0xFFA894C4), outlineVariant = Color(0xFFE0D4F0),
    scrim = Color(0xFF2A2040),
)

// ── Forest — deep botanical green ───────────────────────────────────────────
val ForestColors: ColorScheme = darkColorScheme(
    primary = Color(0xFF8FBC7A), onPrimary = Color(0xFF0D1A0A),
    primaryContainer = Color(0xFF1E301A), onPrimaryContainer = Color(0xFFC4E3B0),
    secondary = Color(0xFFD8B25C), onSecondary = Color(0xFF1A1200),
    secondaryContainer = Color(0xFF33280C), onSecondaryContainer = Color(0xFFF0D9A0),
    tertiary = Color(0xFF6FBFA8), onTertiary = Color(0xFF04201A),
    tertiaryContainer = Color(0xFF123129), onTertiaryContainer = Color(0xFFA4E6D2),
    background = Color(0xFF0B120C), onBackground = Color(0xFFDDE8DA),
    surface = Color(0xFF101812), onSurface = Color(0xFFDDE8DA),
    surfaceVariant = Color(0xFF182219), onSurfaceVariant = Color(0xFF9AB094),
    surfaceTint = Color(0xFF8FBC7A),
    inverseSurface = Color(0xFFDDE8DA), inverseOnSurface = Color(0xFF0B120C),
    error = Color(0xFFE07856), onError = Color(0xFF1A0800),
    errorContainer = Color(0xFF331610), onErrorContainer = Color(0xFFF5B8A2),
    outline = Color(0xFF3E5240), outlineVariant = Color(0xFF223022),
    scrim = Color(0xFF000000),
)
