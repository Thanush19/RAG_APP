package com.ragclient.android.core.designsystem

import androidx.compose.ui.graphics.Color

/**
 * Design-token layer (client plan 5.1): hand-picked fallback palette.
 * Shaped as a typed token object so it can migrate to the re-evaluated
 * Compose Styles API later with minimal churn.
 */
object AppColors {

    val Ink = Color(0xFF0F1412)          // near-neutral surface text
    val Surface = Color(0xFFF7F6F2)      // warm paper background
    val SurfaceDim = Color(0xFFEDEBE4)
    val Accent = Color(0xFF16A37A)       // single accent: emerald
    val AccentDark = Color(0xFF0E7A5B)
    val OnAccent = Color(0xFF052017)
    val Muted = Color(0xFF6E756F)
    val Error = Color(0xFFB3261E)

    val DarkInk = Color(0xFFE8E6E1)
    val DarkSurface = Color(0xFF131715)
    val DarkSurfaceDim = Color(0xFF1B201D)

    val StatusProcessing = Color(0xFFC79A2E)
    val StatusReady = Accent
    val StatusFailed = Error
}
