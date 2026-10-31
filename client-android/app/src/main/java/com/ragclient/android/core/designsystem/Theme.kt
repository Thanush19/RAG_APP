package com.ragclient.android.core.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.isSystemInDarkTheme

private val LightScheme = lightColorScheme(
    primary = AppColors.Accent,
    onPrimary = AppColors.OnAccent,
    secondary = AppColors.AccentDark,
    background = AppColors.Surface,
    onBackground = AppColors.Ink,
    surface = AppColors.Surface,
    onSurface = AppColors.Ink,
    surfaceVariant = AppColors.SurfaceDim,
    onSurfaceVariant = AppColors.Muted,
    error = AppColors.Error
)

private val DarkScheme = darkColorScheme(
    primary = AppColors.Accent,
    onPrimary = AppColors.OnAccent,
    secondary = AppColors.AccentDark,
    background = AppColors.DarkSurface,
    onBackground = AppColors.DarkInk,
    surface = AppColors.DarkSurface,
    onSurface = AppColors.DarkInk,
    surfaceVariant = AppColors.DarkSurfaceDim,
    onSurfaceVariant = AppColors.Muted,
    error = AppColors.Error
)

/**
 * M0 uses the hand-picked palette; Material You dynamic color
 * (dynamicLightColorScheme) is wired in M4 polish per client plan 5.
 */
@Composable
fun RagClientTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        typography = AppType.Typography,
        shapes = AppShapes.Shapes,
        content = content
    )
}
