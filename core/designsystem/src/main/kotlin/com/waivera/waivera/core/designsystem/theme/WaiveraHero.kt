package com.waivera.waivera.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * The full-bleed plum hero of concept A ("Confident"): the sign-in header, the hero of every tab
 * screen, and the compact header of the post-sign-in gates. A rounded sheet rises over it.
 *
 * Every text pair keeps 4.5:1 at every point of its gradient, under both glows at full strength
 * (`WaiveraHeroContrastTest`): white and the on-hero secondary ink on the header, and white on the
 * CTA gradient in both themes. The light end of the brand gradient (`#FF6FBC`) fails with white,
 * so the CTA uses deeper stops and the trails stay decorative.
 */
@Immutable
data class WaiveraHeroColors(
    val headerTop: Color,
    val headerBottom: Color,
    /** The pink glow in the top-end corner and the violet glow in the bottom-start corner. */
    val glowPink: Color,
    val glowViolet: Color,
    /** Headings, numbers, the logo's ink and the bar title on the hero. */
    val onHeader: Color,
    /** Supporting lines, eyebrows and labels on the hero. */
    val onHeaderSecondary: Color,
    /** The summary card on the hero (glass): its fill and hairline. */
    val card: Color,
    val cardBorder: Color,
    /** A stat tile on the hero: its fill and hairline. */
    val tile: Color,
    val tileBorder: Color,
    /** The progress ring's unfilled track. */
    val ringTrack: Color,
    /** The signed-in person's avatar fill on the hero bar. */
    val accountFill: Color,
    /** The countdown chip ("in 30 min"): mint on a mint glass. */
    val chip: Color,
    val onChip: Color,
    /** The clock glyph beside the next item. */
    val nextIcon: Color,
    /** The sign-in sheet: white in light, the plum page in dark (fields sit on the surface step). */
    val sheet: Color,
    val buttonStart: Color,
    val buttonEnd: Color,
    val onButton: Color,
    /** The soft coloured glow under a CTA. */
    val buttonGlow: Color,
) {
    /** Start to end, violet to magenta: every primary CTA's fill. */
    val buttonBrush: Brush get() = Brush.horizontalGradient(listOf(buttonStart, buttonEnd))

    /** The header gradient at the concept's 165° (top, leaning start, to bottom, leaning end). */
    fun headerBrush(size: Size): Brush =
        cssLinearGradient(size, WaiveraHeroGeometry.HEADER_ANGLE_DEGREES, listOf(headerTop, headerBottom))
}

/**
 * Where the hero's glows sit, in dp on the concept's 390-wide frame (scaled to the window): the
 * pink one a 360 circle hanging off the top-end corner, the violet one a 380 circle below the
 * bottom-start corner, each fading out at [GLOW_FADE] of its radius. `WaiveraHeroContrastTest`
 * samples the same geometry to check the text that sits on the hero.
 */
internal object WaiveraHeroGeometry {
    const val HEADER_ANGLE_DEGREES = 165f
    const val GLOW_FADE = 0.65f
    const val PINK_RADIUS = 180f
    const val PINK_END_INSET = 40f
    const val PINK_TOP = 30f
    const val VIOLET_RADIUS = 190f
    const val VIOLET_START = 20f
    const val VIOLET_BELOW = 30f
}

/** A CSS `linear-gradient(<angle>deg, …)` over [size]: the line runs through the centre at [degrees]. */
internal fun cssLinearGradient(size: Size, degrees: Float, colors: List<Color>): Brush {
    val radians = Math.toRadians(degrees.toDouble())
    val dx = sin(radians).toFloat()
    val dy = -cos(radians).toFloat()
    val half = (abs(size.width * dx) + abs(size.height * dy)) / 2f
    val center = Offset(size.width / 2f, size.height / 2f)
    return Brush.linearGradient(
        colors = colors,
        start = Offset(center.x - dx * half, center.y - dy * half),
        end = Offset(center.x + dx * half, center.y + dy * half),
    )
}

internal object WaiveraHeroPalette {
    val HeaderTopLight = Color(0xFF2A1145)
    val HeaderBottomLight = Color(0xFF4C2384)
    val HeaderTopDark = Color(0xFF1B0F29)
    val HeaderBottomDark = Color(0xFF33195A)
    val ButtonStartLight = Color(0xFF6A3DC4)
    val ButtonEndLight = Color(0xFFB4307D)
    val ButtonStartDark = Color(0xFF7A3BE0)
    val ButtonEndDark = Color(0xFFB8338A)
    val ButtonGlowLight = Color(0x477C3AC8)
    val ButtonGlowDark = Color(0x73A03CC8)
    val Mint = Color(0xFF8EF0DC)

    // The concept's rgba(110,224,203,.18) lightened the chip until mint fell to 3.6:1 under the pink
    // glow; a deep teal glass keeps the mint at 5:1 and still reads as a mint chip.
    val MintGlass = Color(0x660A6B60)
    val NextIcon = Color(0xFFFFB8DC)
    const val GLOW_PINK_ALPHA = 0.42f
    const val GLOW_VIOLET_ALPHA = 0.55f

    // The concept sets secondary lines at 62–72% white; 80% keeps 4.5:1 under the glows at full strength.
    const val ON_HEADER_SECONDARY_ALPHA = 0.8f
    const val CARD_ALPHA_LIGHT = 0.08f
    const val CARD_ALPHA_DARK = 0.06f
    const val CARD_BORDER_ALPHA = 0.13f
    const val TILE_ALPHA = 0.07f
    const val TILE_BORDER_ALPHA = 0.1f
    const val RING_TRACK_ALPHA = 0.14f
    const val ACCOUNT_ALPHA = 0.16f
}

private fun white(alpha: Float) = WaiveraPalette.White.copy(alpha = alpha)

internal val LightWaiveraHeroColors = WaiveraHeroColors(
    headerTop = WaiveraHeroPalette.HeaderTopLight,
    headerBottom = WaiveraHeroPalette.HeaderBottomLight,
    glowPink = WaiveraPalette.BrandPink.copy(alpha = WaiveraHeroPalette.GLOW_PINK_ALPHA),
    glowViolet = WaiveraPalette.BrandViolet.copy(alpha = WaiveraHeroPalette.GLOW_VIOLET_ALPHA),
    onHeader = WaiveraPalette.White,
    onHeaderSecondary = white(WaiveraHeroPalette.ON_HEADER_SECONDARY_ALPHA),
    card = white(WaiveraHeroPalette.CARD_ALPHA_LIGHT),
    cardBorder = white(WaiveraHeroPalette.CARD_BORDER_ALPHA),
    tile = white(WaiveraHeroPalette.TILE_ALPHA),
    tileBorder = white(WaiveraHeroPalette.TILE_BORDER_ALPHA),
    ringTrack = white(WaiveraHeroPalette.RING_TRACK_ALPHA),
    accountFill = white(WaiveraHeroPalette.ACCOUNT_ALPHA),
    chip = WaiveraHeroPalette.MintGlass,
    onChip = WaiveraHeroPalette.Mint,
    nextIcon = WaiveraHeroPalette.NextIcon,
    sheet = WaiveraPalette.SurfaceLight,
    buttonStart = WaiveraHeroPalette.ButtonStartLight,
    buttonEnd = WaiveraHeroPalette.ButtonEndLight,
    onButton = WaiveraPalette.White,
    buttonGlow = WaiveraHeroPalette.ButtonGlowLight,
)

internal val DarkWaiveraHeroColors = LightWaiveraHeroColors.copy(
    headerTop = WaiveraHeroPalette.HeaderTopDark,
    headerBottom = WaiveraHeroPalette.HeaderBottomDark,
    card = white(WaiveraHeroPalette.CARD_ALPHA_DARK),
    // The plum page, so the fields (the surface step) stand out on it.
    sheet = WaiveraPalette.BackgroundDark,
    buttonStart = WaiveraHeroPalette.ButtonStartDark,
    buttonEnd = WaiveraHeroPalette.ButtonEndDark,
    buttonGlow = WaiveraHeroPalette.ButtonGlowDark,
)

/** Hero colours and measurements for the current theme. */
object WaiveraHero {
    val colors: WaiveraHeroColors
        @Composable
        @ReadOnlyComposable
        get() = if (WaiveraTheme.colors.isDark) DarkWaiveraHeroColors else LightWaiveraHeroColors

    /** Hero colours for a resolved theme, outside composition (tests, previews). */
    fun colorsFor(darkTheme: Boolean): WaiveraHeroColors =
        if (darkTheme) DarkWaiveraHeroColors else LightWaiveraHeroColors

    /** The sign-in sheet's top corners: the curve reads as the screen's shape. */
    val SheetShape: Shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)

    /** A tab screen's sheet, a little tighter than sign-in's. */
    val TabSheetShape: Shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

    /** How far the hero runs on under the sign-in sheet's curve. */
    val SheetOverlap: Dp = 44.dp

    /** The tab sheet's first band: its curve and the padding above its first section (22 in the concept). */
    val TabSheetLip: Dp = 22.dp

    /** Sign-in header below the status bar on a regular phone: the concept's 300 minus the bar. */
    val HeaderHeight: Dp = 260.dp

    /** Header on a short window, at large font or with the keyboard up. */
    val HeaderHeightCompact: Dp = 116.dp

    /** The gates' compact header: the lockup with room for the trails. */
    val GateHeaderHeight: Dp = 120.dp

    /** Where the sign-in lockup sits below the status bar (132 in the concept, less its 40 bar). */
    val LockupTop: Dp = 92.dp

    /** The logo lockup in the header. */
    val Lockup: Dp = 42.dp
    val LockupCompact: Dp = 32.dp

    /** How far the sheet rises on its entrance. */
    val EntranceRise: Dp = 24.dp

    /** The hero bar on a tab screen: the mark, the title and the account avatar. */
    val BarHeight: Dp = 56.dp
    val BarMark: Dp = 30.dp
    val BarAvatar: Dp = 38.dp

    /** The concept's frame width: trails and glows are placed on it and scaled to the window. */
    val FrameWidth: Dp = 390.dp
}
