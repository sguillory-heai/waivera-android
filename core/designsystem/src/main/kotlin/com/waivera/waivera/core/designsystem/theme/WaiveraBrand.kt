package com.waivera.waivera.core.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * The brand gradient from the logo's looped "W": violet, pink, then gold, with the logo's own
 * stop positions. It is the signature, used with restraint: the logo, the sign-in hero,
 * celebration cards, empty-state art and the confetti. Never on body text or dense UI.
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

    /** Top to bottom, as in the logo. */
    fun verticalGradient(alpha: Float = 1f): Brush = Brush.verticalGradient(*stops(alpha))

    /** Start to end, for bands and card edges. */
    fun horizontalGradient(alpha: Float = 1f): Brush = Brush.horizontalGradient(*stops(alpha))

    /** Top start to bottom end, for washes behind a hero or a celebration card. */
    fun diagonalGradient(alpha: Float = 1f): Brush = Brush.linearGradient(*stops(alpha))

    /**
     * The gradient as a soft wash over a surface, at the theme's [WaiveraColors.brandWashAlpha].
     * Text colours keep 4.5:1 on every point of it (`WaiveraColorsContrastTest`).
     */
    @Composable
    @ReadOnlyComposable
    fun wash(): Brush = diagonalGradient(WaiveraTheme.colors.brandWashAlpha)

    private fun stops(alpha: Float): Array<Pair<Float, Color>> = arrayOf(
        0f to Violet.copy(alpha = alpha),
        VIOLET_STOP to Violet.copy(alpha = alpha),
        PINK_STOP to Pink.copy(alpha = alpha),
        GOLD_STOP to Gold.copy(alpha = alpha),
    )
}
