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

    // The "Your day" track's event blocks (concept B), top to bottom.
    val TimelineTealTop = Color(0xFF3CC4AE)
    val TimelineTealBottom = Color(0xFF1FA08B)
    val TimelineVioletTop = Color(0xFF9C7BF0)
    val TimelineVioletBottom = Color(0xFF7A55DA)
    val TimelineGoldTop = Color(0xFFF2BE5A)
    val TimelineGoldBottom = Color(0xFFE0A032)

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

    /** The concept's violet: links, accents and the start of the light CTA gradient. */
    val Violet = Color(0xFF6A3DC4)
    val PurpleDeep = Color(0xFF5B33AE)
    val AccentSoftLight = Color(0xFFEEE6FB)
    val DangerLight = Color(0xFFEF4444)
    val DangerFillLight = Color(0xFFDC2626)
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
     * Filled accent controls in dark: the saturated violet of the CTA gradient with white text
     * (5.95:1). Dark ink on a pastel fill was hard on the eyes (owner, 2026-10-28).
     */
    val VioletDarkFill = Color(0xFF7A3BE0)
    val AccentSoftDark = Color(0xFF2C2245)

    /** Ink for stock Material fills on the pastel accent only; Waivera's own controls never use it. */
    val InkOnPastelDark = Color(0xFF1A1226)
    val AvatarFillDark = Color(0xFF4D3F6A)
    val OnAccentSoftDark = Color(0xFFD9CCFF)
    val DangerDark = Color(0xFFF87171)

    /** A destructive fill in dark: saturated red with white text (5.48:1), never pastel with dark ink. */
    val DangerFillDark = Color(0xFFC42E47)
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

    // The selected tab's pill (concept A): a pastel lilac-to-pink wash with deep violet ink in
    // light, the saturated CTA gradient with white in dark (never dark ink on a pastel there).
    val NavPillStartLight = Color(0xFFEBDDFB)
    val NavPillEndLight = Color(0xFFFBDDEC)
    val OnNavPillLight = Color(0xFF4A2391)

    /** The bar behind the tabs in dark, one step under the surface so the pill lifts off it. */
    val NavBarDark = Color(0xFF18141F)

    /** The tab's unread count: the concept's rose, 4.62:1 with white, kept apart from the danger red. */
    val NavBadge = Color(0xFFD6336C)

    /** A snackbar in light: the hero's deep plum, so a passing note carries the brand. */
    val SnackbarLight = Color(0xFF2A1145)

    /** A snackbar in dark: the hero's lower plum, lifted off the page. */
    val SnackbarDark = Color(0xFF33195A)

    const val SCRIM_ALPHA = 0.36f
    const val WASH_ALPHA = 0.08f
    const val BORDER_ALPHA = 0.16f
    const val BORDER_STRONG_ALPHA = 0.21f

    /** The brand gradient laid over a surface as a soft wash (celebration cards, the sign-in hero). */
    const val BRAND_WASH_ALPHA_LIGHT = 0.14f
    const val BRAND_WASH_ALPHA_DARK = 0.18f
    const val BRAND_WASH_END_ALPHA_DARK = 0.12f
}
