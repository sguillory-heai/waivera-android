package com.waivera.waivera.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import com.waivera.waivera.core.designsystem.feedback.LocalHapticsEnabled

/**
 * Root theme for every Waivera screen. Brand colours are fixed (no dynamic colour) so the
 * app matches the web product.
 *
 * @param darkTheme resolved from the Appearance setting by the app; defaults to the system.
 * @param motion the motion scheme; defaults to following the system animator scale.
 * @param hapticsEnabled the Settings "Haptics" switch. When off,
 *   [com.waivera.waivera.core.designsystem.feedback.Haptics] does nothing.
 */
@Composable
fun WaiveraTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    motion: WaiveraMotionScheme = rememberSystemMotionScheme(),
    hapticsEnabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colors = waiveraColors(darkTheme)
    CompositionLocalProvider(
        LocalWaiveraColors provides colors,
        LocalWaiveraMotion provides motion,
        LocalHapticsEnabled provides hapticsEnabled,
    ) {
        MaterialTheme(
            colorScheme = colors.toMaterialColorScheme(),
            typography = WaiveraMaterialTypography,
            shapes = WaiveraMaterialShapes,
        ) {
            // Compose's own default ink is black, which a Text or Icon with no colour of its own
            // drew on the dark sheet cards (SheetPieces). The theme sets the token ink instead.
            CompositionLocalProvider(LocalContentColor provides colors.defaultContentColor(), content = content)
        }
    }
}

/** The ink for text and icons that set no colour of their own: the [WaiveraColors.text] token. */
internal fun WaiveraColors.defaultContentColor(): Color = text

/** Token accessors, mirroring `MaterialTheme.colorScheme` style. */
object WaiveraTheme {
    val colors: WaiveraColors
        @Composable
        @ReadOnlyComposable
        get() = LocalWaiveraColors.current

    val motion: WaiveraMotionScheme
        @Composable
        @ReadOnlyComposable
        get() = LocalWaiveraMotion.current
}

private fun WaiveraColors.toMaterialColorScheme(): ColorScheme {
    val base = if (isDark) darkColorScheme() else lightColorScheme()
    // Material pairs onPrimary with primary (the pastel accent in dark). Waivera's own filled
    // controls use accentFill with white; this keeps any stock Material fill legible too.
    val onPrimary = if (isDark) WaiveraPalette.InkOnPastelDark else onAccent
    return base.copy(
        primary = accent,
        onPrimary = onPrimary,
        primaryContainer = accentSoft,
        onPrimaryContainer = onAccentSoft,
        inversePrimary = inverseAccent,
        secondary = accent,
        onSecondary = onPrimary,
        secondaryContainer = accentSoft,
        onSecondaryContainer = onAccentSoft,
        tertiary = accent,
        onTertiary = onPrimary,
        background = background,
        onBackground = text,
        surface = surface,
        onSurface = text,
        surfaceVariant = surfaceMuted,
        onSurfaceVariant = textSecondary,
        surfaceTint = accent,
        inverseSurface = text,
        inverseOnSurface = background,
        error = dangerText,
        onError = onDangerFill,
        errorContainer = dangerContainer,
        onErrorContainer = onDangerContainer,
        outline = borderStrong,
        outlineVariant = border,
        scrim = scrim,
        surfaceBright = surface,
        surfaceDim = surfaceMuted,
        surfaceContainerLowest = surface,
        surfaceContainerLow = surface,
        surfaceContainer = surface,
        surfaceContainerHigh = surface,
        surfaceContainerHighest = surfaceMuted,
    )
}
