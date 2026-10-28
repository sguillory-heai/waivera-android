package com.waivera.waivera.core.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * The brand gradient from the logo's looped "W": violet, pink, then gold, with the logo's own
 * stop positions. Under v5's gradient rule A (owner pick) it lives on heroes and backgrounds only:
 * the logo, the plum heroes with their glows and light trails, and the soft wash behind
 * celebration cards, which is a background. Everything else is solid: the progress and reward
 * rings, the schedule's "now" line, buttons, bubbles, chips, segments, the tab pill, badges,
 * spinners, sheets, the selected day and empty-state art. Confetti picks one of [Colors] per
 * piece, so it is multicolour but never a gradient. Never on body text.
 */
// The gradients take their stops as varargs; spreading four pairs once per brush is cheap.
@Suppress("SpreadOperator")
object WaiveraBrand {
    val Violet: Color = WaiveraPalette.BrandViolet
    val Pink: Color = WaiveraPalette.BrandPink
    val Gold: Color = WaiveraPalette.BrandGold

    /** Stop positions taken from the logo SVG (`landinglogo.svg`). */
    const val VIOLET_STOP = 0.245f
    const val PINK_STOP = 0.567f
    const val GOLD_STOP = 1f

    /** The three colours, for confetti and other art that picks one colour per piece. */
    val Colors: List<Color> = listOf(Violet, Pink, Gold)

    /**
     * The gradient as a soft wash over a surface, at the theme's [WaiveraColors.brandWashAlpha].
     * It ends on the theme's [WaiveraColors.brandWashEnd]: gold in light, a softer peach in dark,
     * where gold over plum composited to khaki (UI8-04). Text colours keep 4.5:1 on every point of
     * it (`WaiveraColorsContrastTest`).
     */
    @Composable
    @ReadOnlyComposable
    fun wash(): Brush = washFor(WaiveraTheme.colors)

    /** [wash] for a resolved colour set, outside composition. */
    fun washFor(colors: WaiveraColors): Brush = Brush.linearGradient(*washStops(colors))

    /** The colours of [wash] at each stop, alpha included, for contrast checks. */
    fun washColors(colors: WaiveraColors): List<Color> = washStops(colors).map { it.second }.distinct()

    private fun washStops(colors: WaiveraColors): Array<Pair<Float, Color>> = arrayOf(
        0f to Violet.copy(alpha = colors.brandWashAlpha),
        VIOLET_STOP to Violet.copy(alpha = colors.brandWashAlpha),
        PINK_STOP to Pink.copy(alpha = colors.brandWashAlpha),
        GOLD_STOP to colors.brandWashEnd,
    )
}
