package com.waivera.waivera.core.designsystem.theme

import androidx.compose.ui.graphics.Color

/**
 * Raw brand values. Screens read colour roles from [WaiveraTheme.colors], never these.
 *
 * Values follow spec 10-design-feedback §1. Where a spec value fails 4.5:1 for text it keeps
 * its role for icons and fills, and a darker `*Text` value carries text instead.
 */
internal object WaiveraPalette {
    val White = Color(0xFFFFFFFF)
    val Black = Color(0xFF000000)

    // Light
    val BackgroundLight = Color(0xFFF8F9FA)
    val SurfaceLight = Color(0xFFFFFFFF)
    val SurfaceMutedLight = Color(0xFFF3F4F6)
    val BorderLight = Color(0xFFE5E7EB)
    val BorderStrongLight = Color(0xFF8C929C)
    val TextLight = Color(0xFF111827)

    // Spec #6B7280 is 4.39:1 on surfaceMuted; this is the closest value that passes everywhere.
    val TextSecondaryLight = Color(0xFF626A77)
    val Purple = Color(0xFF7E57C2)
    val PurpleDeep = Color(0xFF5E35B1)
    val AccentSoftLight = Color(0xFFEEE7F8)
    val DangerLight = Color(0xFFEF4444)
    val DangerFillLight = Color(0xFFDC2626)
    val DangerTextLight = Color(0xFFB91C1C)
    val DangerContainerLight = Color(0xFFFEE2E2)
    val SuccessLight = Color(0xFF16A34A)
    val SuccessTextLight = Color(0xFF15803D)
    val WarningLight = Color(0xFFD97706)
    val WarningTextLight = Color(0xFFB45309)
    val WarningContainerLight = Color(0xFFFEF3C7)
    val OnWarningContainerLight = Color(0xFF92400E)

    // Skeleton bones need at least 1.3:1 against the page to read as "loading", not blank.
    val SkeletonBaseLight = Color(0xFFD6D9DF)
    val SkeletonHighlightLight = Color(0xFFE5E7EB)

    // Dark
    val BackgroundDark = Color(0xFF0F0F12)
    val SurfaceDark = Color(0xFF1F2937)

    // Darker than surface on purpose: the muted surface recedes in dark and advances in light.
    val SurfaceMutedDark = Color(0xFF111827)
    val BorderDark = Color(0xFF374151)
    val BorderStrongDark = Color(0xFF7B8494)
    val TextDark = Color(0xFFF9FAFB)
    val TextSecondaryDark = Color(0xFF9CA3AF)
    val PurpleDarkAccent = Color(0xFFA78BFA)

    // White on #A78BFA is 2.72:1, so filled accent controls use a dark label in dark mode.
    val OnAccentDark = Color(0xFF1E1534)
    val AccentSoftDark = Color(0xFF2C2340)
    val OnAccentSoftDark = Color(0xFFD9CCFF)
    val DangerDark = Color(0xFFF87171)
    val OnDangerFillDark = Color(0xFF1F0A0A)
    val DangerContainerDark = Color(0xFF3B1212)
    val OnDangerContainerDark = Color(0xFFFCA5A5)
    val SuccessDark = Color(0xFF4ADE80)
    val WarningDark = Color(0xFFFBBF24)
    val WarningContainerDark = Color(0xFF3A2A0A)
    val OnWarningContainerDark = Color(0xFFFDE68A)
    val SkeletonBaseDark = Color(0xFF2B3546)
    val SkeletonHighlightDark = Color(0xFF374151)

    // Calendar and milestone colours that were raw literals in the Expo app (spec 10 §1).
    val MilestoneDot = Color(0xFFF59E0B)
    val HolidayIcon = Color(0xFF8B5CF6)
    val BirthdayContainerLight = Color(0xFFFEF3C7)
    val OnBirthdayContainerLight = Color(0xFF92400E)
    val AnniversaryContainerLight = Color(0xFFD1FAE5)
    val OnAnniversaryContainerLight = Color(0xFF047857)
    val NewHireContainerLight = Color(0xFFEDE9FE)
    val OnNewHireContainerLight = Color(0xFF6D28D9)
    val BirthdayContainerDark = Color(0xFF3A2A0A)
    val OnBirthdayContainerDark = Color(0xFFFDE68A)
    val AnniversaryContainerDark = Color(0xFF0B3B2E)
    val OnAnniversaryContainerDark = Color(0xFF6EE7B7)
    val NewHireContainerDark = Color(0xFF2C2340)
    val OnNewHireContainerDark = Color(0xFFC4B5FD)

    const val SCRIM_ALPHA = 0.36f
    const val WASH_ALPHA = 0.08f
    const val BORDER_ALPHA = 0.16f
    const val BORDER_STRONG_ALPHA = 0.21f
}
