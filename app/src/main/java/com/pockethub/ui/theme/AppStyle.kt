package com.pockethub.ui.theme

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily

/**
 * PocketHub style variants — each one is a complete visual identity (palette + typography
 * + corner language). Switching restyles every screen, dialog and bottom sheet because
 * they all read from MaterialTheme + these locals.
 */
enum class AppStyle(val key: String) {
    /** Linear-inspired calm dark (the original PocketHub look). */
    LinearDark("linear_dark"),
    /** GitHub Primer-inspired airy light. */
    PrimerLight("primer_light"),
    /** Solarized paper — warm beige, low-glare reading theme. */
    Paper("paper"),
    /** Neon cyber terminal — pure black, electric cyan/magenta. */
    Neon("neon"),
    /** Lavender dream — soft violet pastels, rounded, friendly. */
    Lavender("lavender"),
    /** Forest — deep green botanical, earthy accent. */
    Forest("forest");

    companion object {
        fun fromKey(key: String?): AppStyle = entries.firstOrNull { it.key == key } ?: LinearDark
    }
}

/**
 * Per-style design tokens beyond color/typography.
 *
 * @param cornerScale Multiplier applied to the base corner radii. 0.0 = fully square
 *   (terminal/brutalist), 1.0 = default M3 radii, 2.0 = very round (friendly).
 * @param monoFont    Monospace family used for code/diff viewers.
 * @param displayFont Primary family for headings/body when a style wants a distinct voice;
 *   null = system default.
 */
data class StyleTokens(
    val cornerScale: Float,
    val monoFont: FontFamily,
    val displayFont: FontFamily?,
    /** Accent gradient pair for hero surfaces (headers, FABs) — flat color if equal. */
    val accentA: Color,
    val accentB: Color,
)

val LocalStyleTokens = compositionLocalOf {
    StyleTokens(
        cornerScale = 1.0f,
        monoFont = FontFamily.Monospace,
        displayFont = null,
        accentA = Color(0xFF7C8BFF),
        accentB = Color(0xFF7C8BFF),
    )
}

/** Currently-active style, for places that need to branch beyond tokens. */
val LocalAppStyle = compositionLocalOf { AppStyle.LinearDark }
