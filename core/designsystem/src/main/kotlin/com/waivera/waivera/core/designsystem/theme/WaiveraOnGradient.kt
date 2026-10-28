package com.waivera.waivera.core.designsystem.theme

/**
 * The colour roles for content drawn on the CTA gradient (concept A's own message bubble): white
 * inks, and anything nested on it (a file card, a reaction, a mention, a code span) as a dark tint
 * of the gradient rather than a white glass, because white on a white glass fell under 4.5:1 at
 * the magenta end. Provide it through [LocalWaiveraColors] around that content only.
 *
 * Every text role keeps 4.5:1 at both ends of both themes' gradients, nested tints included
 * (`WaiveraOnGradientContrastTest`).
 */
fun WaiveraColors.onGradient(): WaiveraColors {
    val white = WaiveraPalette.White
    val inset = WaiveraPalette.Black.copy(alpha = OnGradientAlpha.INSET)
    val insetStrong = WaiveraPalette.Black.copy(alpha = OnGradientAlpha.INSET_STRONG)
    val tile = WaiveraTileTint(container = insetStrong, content = white)
    return copy(
        surface = inset,
        surfaceMuted = insetStrong,
        raised = inset,
        field = inset,
        border = white.copy(alpha = OnGradientAlpha.BORDER),
        borderStrong = white.copy(alpha = OnGradientAlpha.BORDER_STRONG),
        text = white,
        textSecondary = white.copy(alpha = OnGradientAlpha.SECONDARY),
        accent = white,
        link = white,
        accentSoft = insetStrong,
        onAccentSoft = white,
        accentWash = inset,
        accentBorder = white.copy(alpha = OnGradientAlpha.BORDER),
        accentBorderStrong = white.copy(alpha = OnGradientAlpha.BORDER_STRONG),
        dangerText = white,
        successText = white,
        warningText = white,
        tiles = WaiveraTileColors(
            brand = tile,
            rose = tile,
            amber = tile,
            teal = tile,
            neutral = tile,
            danger = tile,
            celebration = tile,
        ),
    )
}

/** Opacities of [onGradient]'s roles over the gradient. */
internal object OnGradientAlpha {
    /** Secondary text: 92% white keeps 4.8:1 at the dark theme's magenta end, the weakest point. */
    const val SECONDARY = 0.92f

    /** A nested card or a mention's background: a dark tint, so white on it gains contrast. */
    const val INSET = 0.16f
    const val INSET_STRONG = 0.22f
    const val BORDER = 0.3f
    const val BORDER_STRONG = 0.6f
}
