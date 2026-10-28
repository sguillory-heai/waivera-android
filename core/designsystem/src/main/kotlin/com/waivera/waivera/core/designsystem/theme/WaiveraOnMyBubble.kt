package com.waivera.waivera.core.designsystem.theme

/**
 * The colour roles for content nested in my own message bubble ([WaiveraColors.myBubble], v5
 * decision 3, option B). The body keeps the theme's dark (light theme) or light (dark theme) ink;
 * only the roles whose theme value loses contrast on the tint change:
 *
 * - links, mentions, my reaction's outline and a quote's bar take a deeper violet in light (the
 *   accent violet fell to 4.9:1 on the tint, close to the line) and the link lilac in dark;
 * - danger text takes a deeper red in light (the theme's fell to 4.2:1 under the arrival wash)
 *   and the danger container's pink in dark, where the plain red fell to 4.3:1;
 * - success and warning inks fall back to the body ink, since no status text sits in a bubble
 *   and the theme's green and amber fail on the light tint;
 * - someone else's reaction pill sits on the surface, as the file card does, instead of the page,
 *   so the nested pieces read as one family of white (light) or plum (dark) cards.
 *
 * Every text role keeps 4.5:1 on the tint and on the nested surface in both themes
 * (`WaiveraMyBubbleContrastTest`). Provide it through [LocalWaiveraColors] around that content only.
 */
fun WaiveraColors.onMyBubble(): WaiveraColors {
    val violet = if (isDark) WaiveraPalette.VioletDarkLink else WaiveraPalette.TonalInkLight
    return copy(
        background = surface,
        accent = violet,
        link = violet,
        dangerText = if (isDark) WaiveraPalette.OnDangerContainerDark else WaiveraPalette.DangerTextOnMyBubbleLight,
        successText = text,
        warningText = text,
    )
}
