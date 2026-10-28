package com.waivera.waivera.core.designsystem.theme

/**
 * The colour roles for content drawn on the solid brand violet ([WaiveraColors.accentFill]), my
 * own message bubble (v5 decision 3): white inks, and anything nested on it (a file card, a
 * reaction pill, a mention, a code span) as a dark tint of the violet rather than a white glass. v5's
 * mockup draws the file card as 14% white, but white on that glass fell to 4.1:1 on the dark
 * theme's #8048E6; the dark tint keeps white at 6.9:1 or better. Provide it through
 * [LocalWaiveraColors] around that content only.
 *
 * Every text role keeps 4.5:1 on the fill of both themes, nested tints included
 * (`WaiveraOnAccentFillContrastTest`).
 */
fun WaiveraColors.onAccentFill(): WaiveraColors {
    val white = WaiveraPalette.White
    val inset = WaiveraPalette.Black.copy(alpha = OnAccentFillAlpha.INSET)
    val insetStrong = WaiveraPalette.Black.copy(alpha = OnAccentFillAlpha.INSET_STRONG)
    val tile = WaiveraTileTint(container = insetStrong, content = white)
    return copy(
        // A reaction someone else added sits on `background`: the page colour put white ink on
        // near-white inside my bubble, so it takes the dark tint too.
        background = inset,
        surface = inset,
        surfaceMuted = insetStrong,
        raised = inset,
        field = inset,
        border = white.copy(alpha = OnAccentFillAlpha.BORDER),
        borderStrong = white.copy(alpha = OnAccentFillAlpha.BORDER_STRONG),
        text = white,
        textSecondary = white.copy(alpha = OnAccentFillAlpha.SECONDARY),
        accent = white,
        link = white,
        accentSoft = insetStrong,
        onAccentSoft = white,
        accentWash = inset,
        accentBorder = white.copy(alpha = OnAccentFillAlpha.BORDER),
        accentBorderStrong = white.copy(alpha = OnAccentFillAlpha.BORDER_STRONG),
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

/** Opacities of [onAccentFill]'s roles over the violet. */
internal object OnAccentFillAlpha {
    /** Secondary text: 92% white keeps 4.5:1 on the dark theme's violet, the lighter of the two. */
    const val SECONDARY = 0.92f

    /** A nested card or a mention's background: a dark tint, so white on it gains contrast. */
    const val INSET = 0.16f
    const val INSET_STRONG = 0.22f
    const val BORDER = 0.3f
    const val BORDER_STRONG = 0.6f
}
