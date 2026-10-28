package com.waivera.waivera.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The full-bleed brand hero: a deep plum-to-violet header that a curved sheet rises over (the
 * sign-in screen). Its own file so the hero can grow without touching the shared palette.
 *
 * Every text pair here keeps 4.5:1 at every point of its gradient (`WaiveraHeroContrastTest`):
 * white on the header, white on the light button gradient and dark ink on the dark one. The
 * light end of the brand gradient (`#FF6FBC`) fails with white, so the button uses deeper stops.
 */
@Immutable
data class WaiveraHeroColors(
    val headerTop: Color,
    val headerBottom: Color,
    /** The soft glow in the header's top corner, the brand gradient's pink and violet. */
    val glowPink: Color,
    val glowViolet: Color,
    /** The logo's wordmark and base lines, and anything else drawn on the header. */
    val onHeader: Color,
    /** The sheet that holds the form. */
    val sheet: Color,
    val buttonStart: Color,
    val buttonEnd: Color,
    val onButton: Color,
) {
    /** Top to bottom, plum to violet. */
    val headerBrush: Brush get() = Brush.verticalGradient(listOf(headerTop, headerBottom))

    /** Start to end, violet to magenta: the primary button's fill. */
    val buttonBrush: Brush get() = Brush.horizontalGradient(listOf(buttonStart, buttonEnd))
}

internal object WaiveraHeroPalette {
    val HeaderTopLight = Color(0xFF2A1145)
    val HeaderBottomLight = Color(0xFF4C2384)
    val HeaderTopDark = Color(0xFF1C1027)
    val HeaderBottomDark = Color(0xFF341A55)
    val ButtonStartLight = Color(0xFF6A3DC4)
    val ButtonEndLight = Color(0xFFB4307D)
    val ButtonStartDark = Color(0xFFA78BFA)
    val ButtonEndDark = Color(0xFFF28DC4)
    const val GLOW_PINK_ALPHA = 0.34f
    const val GLOW_VIOLET_ALPHA = 0.42f
}

internal val LightWaiveraHeroColors = WaiveraHeroColors(
    headerTop = WaiveraHeroPalette.HeaderTopLight,
    headerBottom = WaiveraHeroPalette.HeaderBottomLight,
    glowPink = WaiveraPalette.BrandPink.copy(alpha = WaiveraHeroPalette.GLOW_PINK_ALPHA),
    glowViolet = WaiveraPalette.BrandViolet.copy(alpha = WaiveraHeroPalette.GLOW_VIOLET_ALPHA),
    onHeader = WaiveraPalette.White,
    sheet = WaiveraPalette.SurfaceLight,
    buttonStart = WaiveraHeroPalette.ButtonStartLight,
    buttonEnd = WaiveraHeroPalette.ButtonEndLight,
    onButton = WaiveraPalette.White,
)

internal val DarkWaiveraHeroColors = WaiveraHeroColors(
    headerTop = WaiveraHeroPalette.HeaderTopDark,
    headerBottom = WaiveraHeroPalette.HeaderBottomDark,
    glowPink = WaiveraPalette.BrandPink.copy(alpha = WaiveraHeroPalette.GLOW_PINK_ALPHA),
    glowViolet = WaiveraPalette.BrandViolet.copy(alpha = WaiveraHeroPalette.GLOW_VIOLET_ALPHA),
    onHeader = WaiveraPalette.TextDark,
    // The plum page, so the fields (the surface step) stand out on it.
    sheet = WaiveraPalette.BackgroundDark,
    buttonStart = WaiveraHeroPalette.ButtonStartDark,
    buttonEnd = WaiveraHeroPalette.ButtonEndDark,
    onButton = WaiveraPalette.OnAccentDark,
)

/** Hero colours for the current theme. */
object WaiveraHero {
    val colors: WaiveraHeroColors
        @Composable
        @ReadOnlyComposable
        get() = if (WaiveraTheme.colors.isDark) DarkWaiveraHeroColors else LightWaiveraHeroColors

    /** The sheet's top corners: larger than a bottom sheet's, so the curve reads as the screen's shape. */
    val SheetShape: Shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)

    /** How far the sheet's curve reaches up into the header. */
    val SheetOverlap: Dp = 32.dp

    /** Header height on a regular phone: the logo with room for the glow. */
    val HeaderHeight: Dp = 228.dp

    /** Header on a short window, at large font or with the keyboard up. */
    val HeaderHeightCompact: Dp = 116.dp

    /** The logo lockup in the header. */
    val Lockup: Dp = 48.dp
    val LockupCompact: Dp = 36.dp

    /** How far the sheet rises on its entrance. */
    val EntranceRise: Dp = 24.dp

    /** Radius of the header glow, as a fraction of the header width. */
    const val GLOW_RADIUS_FRACTION = 0.75f
}
