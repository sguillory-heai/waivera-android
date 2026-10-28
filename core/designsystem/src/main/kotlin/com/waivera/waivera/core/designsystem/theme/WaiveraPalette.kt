package com.waivera.waivera.core.designsystem.theme

import androidx.compose.ui.graphics.Color

/**
 * Raw brand values. Screens read colour roles from [WaiveraTheme.colors], never these.
 *
 * Light neutrals are faintly warm and lilac-tinted rather than Tailwind's cool grey. Dark is one
 * plum-tinted family that steps by lightness only (page, surface, raised, border), so nothing
 * reads slate-blue. Where a fill colour fails 4.5:1 for text it keeps its role for icons and
 * fills, and a darker `*Text` value carries text instead. `WaiveraColorsContrastTest` checks
 * every text and control pair.
 */
internal object WaiveraPalette {
    val White = Color(0xFFFFFFFF)
    val Black = Color(0xFF000000)

    // Brand gradient, from the logo's looped "W" (top to bottom).
    val BrandViolet = Color(0xFFA640DA)
    val BrandPink = Color(0xFFFF6FBC)
    val BrandGold = Color(0xFFFFD45C)

    // Light: warm lilac neutrals
    val BackgroundLight = Color(0xFFFAF8FC)
    val SurfaceLight = Color(0xFFFFFFFF)
    val SurfaceMutedLight = Color(0xFFF3EFF7)
    val BorderLight = Color(0xFFE7E1EE)
    val BorderStrongLight = Color(0xFF8A8296)
    val TextLight = Color(0xFF1D1726)
    val TextSecondaryLight = Color(0xFF605869)
    val Purple = Color(0xFF7E57C2)
    val PurpleDeep = Color(0xFF5B33AE)
    val AccentSoftLight = Color(0xFFEFE7FA)
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
    val SkeletonBaseLight = Color(0xFFDAD3E3)
    val SkeletonHighlightLight = Color(0xFFE9E4EF)

    // Dark: one plum family, stepping by lightness only
    val BackgroundDark = Color(0xFF151219)
    val SurfaceDark = Color(0xFF1D1924)

    /** The raised step: inputs at rest, tonal buttons, switch tracks. */
    val SurfaceMutedDark = Color(0xFF262131)
    val BorderDark = Color(0xFF352E42)
    val BorderStrongDark = Color(0xFF7E7490)
    val TextDark = Color(0xFFF3EFF8)
    val TextSecondaryDark = Color(0xFFB4AAC2)
    val PurpleDarkAccent = Color(0xFFA78BFA)

    /**
     * Filled accent controls in dark: a step softer than the accent text colour, so a primary
     * button doesn't glare against the plum page, with dark ink on it (5.2:1).
     */
    val PurpleDarkFill = Color(0xFF9478E0)
    val OnAccentDark = Color(0xFF1A1226)
    val AccentSoftDark = Color(0xFF2F2643)
    val OnAccentSoftDark = Color(0xFFD9CCFF)
    val DangerDark = Color(0xFFF87171)
    val OnDangerFillDark = Color(0xFF1F0A0A)
    val DangerContainerDark = Color(0xFF3D1C22)
    val OnDangerContainerDark = Color(0xFFFCA5A5)
    val SuccessDark = Color(0xFF4ADE80)
    val WarningDark = Color(0xFFFBBF24)
    val WarningContainerDark = Color(0xFF3A2C17)
    val OnWarningContainerDark = Color(0xFFFDE68A)
    val SkeletonBaseDark = Color(0xFF332C3F)
    val SkeletonHighlightDark = Color(0xFF3E3650)

    // Icon tiles: one soft tinted tile per category (container, then glyph).
    val TileRoseLight = Color(0xFFFCE6F0)
    val OnTileRoseLight = Color(0xFFA8285F)
    val TileAmberLight = Color(0xFFFCEFD3)
    val OnTileAmberLight = Color(0xFF8A4B00)
    val TileTealLight = Color(0xFFDCF1EE)
    val OnTileTealLight = Color(0xFF0B6A63)
    val TileDangerLight = Color(0xFFFDE7E7)
    val OnTileDangerLight = Color(0xFFB42318)
    val TileRoseDark = Color(0xFF3A2131)
    val OnTileRoseDark = Color(0xFFFFB1D2)
    val TileAmberDark = Color(0xFF3A2C17)
    val OnTileAmberDark = Color(0xFFFFD38A)
    val TileTealDark = Color(0xFF173230)
    val OnTileTealDark = Color(0xFF8EDDD3)
    val TileNeutralDark = Color(0xFF2A2434)

    // Calendar and milestone colours that were raw literals in the Expo app (spec 10 §1).
    val MilestoneDot = Color(0xFFF59E0B)
    val HolidayIcon = Color(0xFF8B5CF6)
    val BirthdayContainerLight = Color(0xFFFEF3C7)
    val OnBirthdayContainerLight = Color(0xFF92400E)
    val AnniversaryContainerLight = Color(0xFFD1FAE5)
    val OnAnniversaryContainerLight = Color(0xFF047857)
    val NewHireContainerLight = Color(0xFFEDE9FE)
    val OnNewHireContainerLight = Color(0xFF6D28D9)
    val BirthdayContainerDark = Color(0xFF3A2C17)
    val OnBirthdayContainerDark = Color(0xFFFDE68A)
    val AnniversaryContainerDark = Color(0xFF0F3530)
    val OnAnniversaryContainerDark = Color(0xFF6EE7B7)
    val NewHireContainerDark = Color(0xFF2F2643)
    val OnNewHireContainerDark = Color(0xFFC4B5FD)

    const val SCRIM_ALPHA = 0.36f
    const val WASH_ALPHA = 0.08f
    const val BORDER_ALPHA = 0.16f
    const val BORDER_STRONG_ALPHA = 0.21f

    /** The brand gradient laid over a surface as a soft wash (celebration cards, the sign-in hero). */
    const val BRAND_WASH_ALPHA_LIGHT = 0.14f
    const val BRAND_WASH_ALPHA_DARK = 0.18f
}
