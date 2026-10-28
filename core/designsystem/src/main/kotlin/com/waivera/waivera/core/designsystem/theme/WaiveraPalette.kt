package com.waivera.waivera.core.designsystem.theme

import androidx.compose.ui.graphics.Color

/**
 * Raw brand values. Screens read colour roles from [WaiveraTheme.colors], never these.
 *
 * Values follow the approved concept A ("Confident", `concepts/src/A.html`). Light neutrals are
 * faintly warm and lilac-tinted rather than Tailwind's cool grey. Dark is one plum-tinted family
 * that steps by lightness only (page #120F17, surface #1C1824, raised #262032), so elevation
 * reads as light from above and nothing reads slate-blue. Where a fill colour fails 4.5:1 for
 * text it keeps its role for icons and fills, and a darker `*Text` value carries text instead.
 * `WaiveraColorsContrastTest` checks every text and control pair.
 */
internal object WaiveraPalette {
    val White = Color(0xFFFFFFFF)
    val Black = Color(0xFF000000)

    // Brand gradient, from the logo's looped "W" (top to bottom).
    val BrandViolet = Color(0xFFA640DA)
    val BrandPink = Color(0xFFFF6FBC)
    val BrandGold = Color(0xFFFFD45C)

    // The "Your day" track's event blocks (concept B): solid, the deeper end of the old top-to-bottom
    // shading, since v5's gradient rule A keeps gradients to heroes and backgrounds.
    val TimelineTeal = Color(0xFF1FA08B)
    val TimelineViolet = Color(0xFF7A55DA)
    val TimelineGold = Color(0xFFE0A032)

    /** The small pink sparkle beside the all-caught-up ring. */
    val SparklePink = Color(0xFFFF8CC8)

    /** The dark wash's warm end: gold over plum turned khaki, peach stays in the plum family. */
    val BrandPeach = Color(0xFFFFB38A)

    // Light: warm lilac neutrals
    val BackgroundLight = Color(0xFFF5F2F9)
    val SurfaceLight = Color(0xFFFFFFFF)

    /** A text field at rest, one faint step off the white sheet. */
    val FieldLight = Color(0xFFF7F4FB)

    /** Tracks, disabled fills and the neutral tile: a step below the page so they still read on it. */
    val SurfaceMutedLight = Color(0xFFEEE9F4)
    val BorderLight = Color(0xFFE8E3EE)
    val BorderStrongLight = Color(0xFF867E93)

    /**
     * A field's edge: lilac-tinted, the lightest that still holds 3:1 on every page surface and
     * the field (3.14:1 on the muted sheet, the weakest).
     */
    val FieldBorderLight = Color(0xFF8A7EA4)
    val TextLight = Color(0xFF1A1324)
    val TextSecondaryLight = Color(0xFF5B5169)

    // 3.1:1 or more on every light page surface: quieter than secondary for non-essential text.
    val TextTertiaryLight = Color(0xFF8A8197)

    /** The brand violet: links, accents and every solid filled control in light (white on it is 6.8:1). */
    val Violet = Color(0xFF6A3DC4)

    /** The lilac tonal fill (v5 `tonal`): tonal buttons, the brand tile, quiet chips. */
    val AccentSoftLight = Color(0xFFEEE7FB)

    /** Violet ink on the lilac tonal fills (v5 `tonalInk`): 9.0:1 on the tonal, 6.8:1 on the selected fill. */
    val TonalInkLight = Color(0xFF4A2391)
    val DangerLight = Color(0xFFEF4444)

    /** A destructive fill in light: v5's red, white on it is 5.5:1 (4.7:1 under the button sheen). */
    val DangerFillLight = Color(0xFFC42F47)
    val DangerTextLight = Color(0xFFB91C1C)
    val DangerContainerLight = Color(0xFFFEE2E2)
    val SuccessLight = Color(0xFF16A34A)

    // #15803D is 4.42:1 on the lilac muted surface; this passes on every light surface.
    val SuccessTextLight = Color(0xFF14763A)
    val WarningLight = Color(0xFFD97706)
    val WarningTextLight = Color(0xFFA04A06)
    val WarningContainerLight = Color(0xFFFEF3C7)
    val OnWarningContainerLight = Color(0xFF92400E)

    // Skeleton bones need at least 1.3:1 against the page to read as "loading", not blank.
    val SkeletonBaseLight = Color(0xFFD6CEE0)
    val SkeletonHighlightLight = Color(0xFFE6E0EE)

    // An avatar's fallback fill. Accent-soft was about 1.0:1 against the brand wash's violet end,
    // so the avatar vanished on a hero (UI8-05). This lilac keeps 1.3:1 on every surface and wash
    // point by its fill alone: avatars carry no border or ring.
    val AvatarFillLight = Color(0xFFD4C5EC)

    // Dark: one plum family, stepping by lightness only
    val BackgroundDark = Color(0xFF120F17)
    val SurfaceDark = Color(0xFF1C1824)

    /** The raised step: sheets, the focused field, tonal buttons, switch tracks. */
    val SurfaceMutedDark = Color(0xFF262032)
    val BorderDark = Color(0xFF302A3A)
    val BorderStrongDark = Color(0xFF857A97)
    val FieldBorderDark = Color(0xFF776B8F)
    val TextDark = Color(0xFFF4F0FA)
    val TextSecondaryDark = Color(0xFFB3A9C4)

    // 3.2:1 or more on every dark page surface, in the plum family.
    val TextTertiaryDark = Color(0xFF776D85)

    /** Pastel lilac for links, icons and data marks on dark surfaces only (8.3:1 on the page). */
    val VioletDarkAccent = Color(0xFFB79CFF)

    /** Text links in dark, a touch lighter again (8.8:1 on the surface). */
    val VioletDarkLink = Color(0xFFC3ABFF)

    /**
     * Filled accent controls in dark, with white text (5.3:1). v5 moved it from #7A3BE0, which
     * reached only 2.9:1 against the plum surfaces; this clears 3:1 on the page and the surface.
     * Dark ink on a pastel fill was hard on the eyes (owner, 2026-10-28).
     */
    val VioletDarkFill = Color(0xFF8048E6)

    /** The lilac tonal fill in dark (v5 `tonal`), with [OnAccentSoftDark] ink at 10.5:1. */
    val AccentSoftDark = Color(0xFF2E2448)

    /** Ink for stock Material fills on the pastel accent only; Waivera's own controls never use it. */
    val InkOnPastelDark = Color(0xFF1A1226)
    val AvatarFillDark = Color(0xFF4D3F6A)
    val OnAccentSoftDark = Color(0xFFE2D6FF)
    val DangerDark = Color(0xFFF87171)

    /** A destructive fill in dark: v5's red, white on it 5.1:1 (4.6:1 under the sheen), never pastel with dark ink. */
    val DangerFillDark = Color(0xFFC7375A)
    val DangerContainerDark = Color(0xFF3A1A24)
    val OnDangerContainerDark = Color(0xFFFF8A9B)
    val SuccessDark = Color(0xFF4ADE80)
    val WarningDark = Color(0xFFFBBF24)
    val WarningContainerDark = Color(0xFF3A2C17)
    val OnWarningContainerDark = Color(0xFFFDE68A)
    val SkeletonBaseDark = Color(0xFF332C3F)
    val SkeletonHighlightDark = Color(0xFF3E3650)

    // Icon tiles: one soft tinted tile per category (container, then glyph), the concept's tones.
    val TileRoseLight = Color(0xFFFBE6F1)
    val OnTileRoseLight = Color(0xFFB4307D)
    val TileAmberLight = Color(0xFFFFF1D6)
    val OnTileAmberLight = Color(0xFF8A5600)
    val TileTealLight = Color(0xFFDDF4EF)
    val OnTileTealLight = Color(0xFF0A6B60)
    val TileDangerLight = Color(0xFFFCE8EC)

    // The concept's #C8334B is 4.44:1 on its tile; this is the closest red that passes.
    val OnTileDangerLight = Color(0xFFBE2E46)
    val TileRoseDark = Color(0xFF3A1A33)
    val OnTileRoseDark = Color(0xFFFF8CC8)

    // Warmed towards rose: #3A2C17 had green well above blue and read khaki on the plum wash (UI9-06).
    val TileAmberDark = Color(0xFF3D2A2A)
    val OnTileAmberDark = Color(0xFFFFD27A)
    val TileTealDark = Color(0xFF133430)
    val OnTileTealDark = Color(0xFF6EE0CB)
    val TileNeutralDark = Color(0xFF2A2434)

    /** A celebration's emoji tile in dark: warm plum, so it sits in the wash's family (UI9-06). */
    val TileCelebrationDark = Color(0xFF3A2533)

    /**
     * A tonal button on the brand wash. Accent-soft was about 1.0:1 against the wash's violet end,
     * so "Send wishes" read as an icon and a link (UI9-08); these keep 1.3:1 on every wash point.
     */
    val TonalOnWashLight = Color(0xFFD5C6ED)

    // 1.46:1 at the wash's pink point, the weakest; #4A3C66 sat on the 1.3 line (wave 13).
    val TonalOnWashDark = Color(0xFF524270)

    // Calendar and milestone colours that were raw literals in the Expo app (spec 10 §1).
    val MilestoneDot = Color(0xFFF59E0B)
    val HolidayIcon = Color(0xFF8B5CF6)
    val BirthdayContainerLight = Color(0xFFFEF3C7)
    val OnBirthdayContainerLight = Color(0xFF92400E)
    val AnniversaryContainerLight = Color(0xFFD1FAE5)
    val OnAnniversaryContainerLight = Color(0xFF047857)
    val NewHireContainerLight = Color(0xFFEDE9FE)
    val OnNewHireContainerLight = Color(0xFF6D28D9)

    // The same warmed amber as the dark tile: the khaki #3A2C17 was off the plum family (UI9-06).
    val BirthdayContainerDark = Color(0xFF3D2A2A)
    val OnBirthdayContainerDark = Color(0xFFFDE68A)
    val AnniversaryContainerDark = Color(0xFF0F3530)
    val OnAnniversaryContainerDark = Color(0xFF6EE7B7)
    val NewHireContainerDark = Color(0xFF2F2643)
    val OnNewHireContainerDark = Color(0xFFC4B5FD)

    /**
     * The selected state shared by the tab pill, segments and chips (v5 decision 5, tonal): a
     * deeper lilac than the tonal button, with [TonalInkLight] / [OnAccentSoftDark] ink at 6.8 /
     * 7.7:1. The fill alone is only 1.3 / 1.6:1 against its track, so the ink, the heavier weight
     * and the chip's check carry the state too (WCAG 1.4.11).
     */
    val SelectedFillLight = Color(0xFFD6C6F6)
    val SelectedFillDark = Color(0xFF46356E)

    /** The bar behind the tabs in dark, one step under the surface so the pill lifts off it. */
    val NavBarDark = Color(0xFF18141F)

    /**
     * Attention (v5 decision 6): unread counts, the Needs you count and the tab's count, in solid
     * rose with white (5.7:1 light, 4.6:1 dark), kept apart from the danger red and the violet of
     * a selection, so the eye goes straight to what is waiting.
     */
    val AttentionLight = Color(0xFFC2255C)
    val AttentionDark = Color(0xFFD6336C)

    /** Informational totals: a neutral chip, not a call for attention (v5 decision 6). */
    val ChipNeutralLight = Color(0xFFECE7F2)
    val ChipNeutralDark = Color(0xFF2B2436)

    /**
     * My own message bubble (v5 decision 3, option B): a soft violet tint with the dark ink in
     * light, a lifted plum in dark. One step deeper than the board's #E9DFFB / #3A2D52 so it
     * separates from the page better (1.25 / 1.60:1 against it, was 1.15 / 1.52) while every ink
     * on it keeps 4.5:1 (`WaiveraMyBubbleContrastTest`). Ownership reads from the right alignment
     * and the tint.
     */
    val MyBubbleLight = Color(0xFFE2D5FA)
    val MyBubbleDark = Color(0xFF3E3058)

    /** Danger text inside my light bubble: #B91C1C fell to 4.2:1 under the arrival wash on the tint. */
    val DangerTextOnMyBubbleLight = Color(0xFF991B1B)

    /**
     * The calendar's selected day (v5 decision 4, option C): a soft lilac rounded square. The tint
     * alone is 1.6 / 1.7:1 against the card, under the 3:1 for UI, so the selected number is also
     * bold violet ([TonalInkLight] 6.8:1 / [VioletDarkLink] 5.3:1 on it): the state never rests on
     * the fill alone.
     */
    val DayTintLight = Color(0xFFD6C6F6)
    val DayTintDark = Color(0xFF46356E)

    /** A snackbar in light: the hero's deep plum, so a passing note carries the brand. */
    val SnackbarLight = Color(0xFF2A1145)

    /** A snackbar in dark: the hero's lower plum, lifted off the page. */
    val SnackbarDark = Color(0xFF33195A)

    /**
     * The filled buttons' sheen (v5 decision 2, option C): white over the top half of the fill,
     * fading out at [BUTTON_SHEEN_END]. The board's 13% put white text on the lightened top at
     * 4.2:1 on the dark violet and 4.5:1 on the light red; these keep the label at 4.5:1 or better
     * at the sheen's brightest point on every filled button (`WaiveraColorsContrastTest`).
     */
    const val BUTTON_SHEEN_ALPHA_LIGHT = 0.1f
    const val BUTTON_SHEEN_ALPHA_DARK = 0.07f
    const val BUTTON_SHEEN_END = 0.52f

    /** The 1dp light top edge of a filled button; it sits above the label, never under it. */
    const val BUTTON_EDGE_ALPHA = 0.25f

    /** The primary button's soft violet glow below it (the board's 55–60% brand shadow). */
    const val BUTTON_GLOW_ALPHA = 0.55f

    /** The raised secondary button's faint drop shadow (the board's 14% plum). */
    const val BUTTON_RAISE_ALPHA = 0.14f
    val ButtonRaiseShadow = Color(0xFF140828)

    const val SCRIM_ALPHA = 0.36f
    const val WASH_ALPHA = 0.08f
    const val BORDER_ALPHA = 0.16f
    const val BORDER_STRONG_ALPHA = 0.21f

    /** The brand gradient laid over a surface as a soft wash (celebration cards, the sign-in hero). */
    const val BRAND_WASH_ALPHA_LIGHT = 0.14f
    const val BRAND_WASH_ALPHA_DARK = 0.18f
    const val BRAND_WASH_END_ALPHA_DARK = 0.12f
}
