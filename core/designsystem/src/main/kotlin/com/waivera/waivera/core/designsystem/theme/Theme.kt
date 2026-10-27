package com.waivera.waivera.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = WaiveraPalette.Purple,
    onPrimary = Color.White,
    primaryContainer = WaiveraPalette.PurpleContainerLight,
    onPrimaryContainer = WaiveraPalette.PurpleContainerDark,
    surface = WaiveraPalette.SurfaceLight,
    onSurface = WaiveraPalette.OnSurfaceLight,
    background = WaiveraPalette.SurfaceLight,
    onBackground = WaiveraPalette.OnSurfaceLight,
    error = WaiveraPalette.Error,
)

private val DarkColors = darkColorScheme(
    primary = WaiveraPalette.PurpleDarkAccent,
    onPrimary = WaiveraPalette.PurpleContainerDark,
    primaryContainer = WaiveraPalette.PurpleContainerDark,
    onPrimaryContainer = WaiveraPalette.PurpleContainerLight,
    surface = WaiveraPalette.SurfaceDark,
    onSurface = WaiveraPalette.OnSurfaceDark,
    background = WaiveraPalette.SurfaceDark,
    onBackground = WaiveraPalette.OnSurfaceDark,
    error = WaiveraPalette.ErrorDark,
)

/**
 * Root theme for every Waivera screen. Brand colours are fixed (no dynamic colour)
 * so the app matches the web product.
 */
@Composable
fun WaiveraTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
