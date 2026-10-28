package com.waivera.waivera.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Every colour role the app uses. Feature code reads these through [WaiveraTheme.colors];
 * literal colours outside this module fail review.
 *
 * Text roles (`text`, `textSecondary`, `*Text`, `on*`) meet 4.5:1 against the backgrounds
 * they are paired with in both themes. Plain `danger`, `success` and `warning` are for icons,
 * dots and fills only.
 */
@Immutable
data class WaiveraColors(
    val isDark: Boolean,
    val background: Color,
    val surface: Color,
    val surfaceMuted: Color,
    val border: Color,
    val borderStrong: Color,
    val text: Color,
    val textSecondary: Color,
    val accent: Color,
    /** Fill of primary buttons and other filled accent controls; [onAccent] sits on it. */
    val accentFill: Color,
    val onAccent: Color,
    val accentSoft: Color,
    val onAccentSoft: Color,
    val accentWash: Color,
    val accentBorder: Color,
    val accentBorderStrong: Color,
    val inverseAccent: Color,
    val unread: Color,
    val danger: Color,
    val dangerText: Color,
    val dangerFill: Color,
    val onDangerFill: Color,
    val dangerContainer: Color,
    val onDangerContainer: Color,
    val success: Color,
    val successText: Color,
    val warning: Color,
    val warningText: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    val milestoneDot: Color,
    val holidayIcon: Color,
    val birthdayContainer: Color,
    val onBirthdayContainer: Color,
    val anniversaryContainer: Color,
    val onAnniversaryContainer: Color,
    val newHireContainer: Color,
    val onNewHireContainer: Color,
    /** Badge for a person with more than one milestone on the same day. */
    val multipleContainer: Color,
    val onMultipleContainer: Color,
    val presenceOnline: Color,
    val presenceAway: Color,
    val presenceOffline: Color,
    val scrim: Color,
    /** Resting skeleton bone; the static frame when motion is reduced. At least 1.3:1 on background. */
    val skeletonBase: Color,
    /** The other end of the skeleton pulse. */
    val skeletonHighlight: Color,
    /** Secondary (outlined) button: a tonal raised step in dark instead of a stark outlined block. */
    val secondaryContainer: Color,
    val secondaryBorder: Color,
    /**
     * The chosen segment of a segmented control: a white step on the lilac track in light, the
     * soft accent in dark (a darker surface step there read as the unselected one).
     */
    val segmentSelected: Color,
    /** Category tints for [com.waivera.waivera.core.designsystem.component.WaiveraIconTile]. */
    val tiles: WaiveraTileColors,
    /** Opacity of the brand gradient when it washes a surface (celebration cards, sign-in hero). */
    val brandWashAlpha: Float,
)

/** One tile tint: the soft container and the outlined glyph on it (at least 4.5:1). */
@Immutable
data class WaiveraTileTint(val container: Color, val content: Color)

/** Every icon-tile category, resolved for one theme. */
@Immutable
data class WaiveraTileColors(
    val brand: WaiveraTileTint,
    val rose: WaiveraTileTint,
    val amber: WaiveraTileTint,
    val teal: WaiveraTileTint,
    val neutral: WaiveraTileTint,
    val danger: WaiveraTileTint,
)

internal val LightWaiveraColors = WaiveraColors(
    isDark = false,
    background = WaiveraPalette.BackgroundLight,
    surface = WaiveraPalette.SurfaceLight,
    surfaceMuted = WaiveraPalette.SurfaceMutedLight,
    border = WaiveraPalette.BorderLight,
    borderStrong = WaiveraPalette.BorderStrongLight,
    text = WaiveraPalette.TextLight,
    textSecondary = WaiveraPalette.TextSecondaryLight,
    accent = WaiveraPalette.Purple,
    accentFill = WaiveraPalette.Purple,
    onAccent = WaiveraPalette.White,
    accentSoft = WaiveraPalette.AccentSoftLight,
    onAccentSoft = WaiveraPalette.PurpleDeep,
    accentWash = WaiveraPalette.Purple.copy(alpha = WaiveraPalette.WASH_ALPHA),
    accentBorder = WaiveraPalette.Purple.copy(alpha = WaiveraPalette.BORDER_ALPHA),
    accentBorderStrong = WaiveraPalette.Purple.copy(alpha = WaiveraPalette.BORDER_STRONG_ALPHA),
    inverseAccent = WaiveraPalette.PurpleDarkAccent,
    unread = WaiveraPalette.Purple,
    danger = WaiveraPalette.DangerLight,
    dangerText = WaiveraPalette.DangerTextLight,
    dangerFill = WaiveraPalette.DangerFillLight,
    onDangerFill = WaiveraPalette.White,
    dangerContainer = WaiveraPalette.DangerContainerLight,
    onDangerContainer = WaiveraPalette.DangerTextLight,
    success = WaiveraPalette.SuccessLight,
    successText = WaiveraPalette.SuccessTextLight,
    warning = WaiveraPalette.WarningLight,
    warningText = WaiveraPalette.WarningTextLight,
    warningContainer = WaiveraPalette.WarningContainerLight,
    onWarningContainer = WaiveraPalette.OnWarningContainerLight,
    milestoneDot = WaiveraPalette.MilestoneDot,
    holidayIcon = WaiveraPalette.HolidayIcon,
    birthdayContainer = WaiveraPalette.BirthdayContainerLight,
    onBirthdayContainer = WaiveraPalette.OnBirthdayContainerLight,
    anniversaryContainer = WaiveraPalette.AnniversaryContainerLight,
    onAnniversaryContainer = WaiveraPalette.OnAnniversaryContainerLight,
    newHireContainer = WaiveraPalette.NewHireContainerLight,
    onNewHireContainer = WaiveraPalette.OnNewHireContainerLight,
    multipleContainer = WaiveraPalette.AccentSoftLight,
    onMultipleContainer = WaiveraPalette.PurpleDeep,
    presenceOnline = WaiveraPalette.SuccessLight,
    presenceAway = WaiveraPalette.WarningLight,
    presenceOffline = WaiveraPalette.TextSecondaryLight,
    scrim = WaiveraPalette.Black.copy(alpha = WaiveraPalette.SCRIM_ALPHA),
    skeletonBase = WaiveraPalette.SkeletonBaseLight,
    skeletonHighlight = WaiveraPalette.SkeletonHighlightLight,
    secondaryContainer = WaiveraPalette.SurfaceLight,
    secondaryBorder = WaiveraPalette.BorderStrongLight,
    segmentSelected = WaiveraPalette.SurfaceLight,
    tiles = WaiveraTileColors(
        brand = WaiveraTileTint(WaiveraPalette.AccentSoftLight, WaiveraPalette.PurpleDeep),
        rose = WaiveraTileTint(WaiveraPalette.TileRoseLight, WaiveraPalette.OnTileRoseLight),
        amber = WaiveraTileTint(WaiveraPalette.TileAmberLight, WaiveraPalette.OnTileAmberLight),
        teal = WaiveraTileTint(WaiveraPalette.TileTealLight, WaiveraPalette.OnTileTealLight),
        neutral = WaiveraTileTint(WaiveraPalette.SurfaceMutedLight, WaiveraPalette.TextSecondaryLight),
        danger = WaiveraTileTint(WaiveraPalette.TileDangerLight, WaiveraPalette.OnTileDangerLight),
    ),
    brandWashAlpha = WaiveraPalette.BRAND_WASH_ALPHA_LIGHT,
)

internal val DarkWaiveraColors = WaiveraColors(
    isDark = true,
    background = WaiveraPalette.BackgroundDark,
    surface = WaiveraPalette.SurfaceDark,
    surfaceMuted = WaiveraPalette.SurfaceMutedDark,
    border = WaiveraPalette.BorderDark,
    borderStrong = WaiveraPalette.BorderStrongDark,
    text = WaiveraPalette.TextDark,
    textSecondary = WaiveraPalette.TextSecondaryDark,
    accent = WaiveraPalette.PurpleDarkAccent,
    accentFill = WaiveraPalette.PurpleDarkFill,
    onAccent = WaiveraPalette.OnAccentDark,
    accentSoft = WaiveraPalette.AccentSoftDark,
    onAccentSoft = WaiveraPalette.OnAccentSoftDark,
    accentWash = WaiveraPalette.PurpleDarkAccent.copy(alpha = WaiveraPalette.WASH_ALPHA),
    accentBorder = WaiveraPalette.PurpleDarkAccent.copy(alpha = WaiveraPalette.BORDER_ALPHA),
    accentBorderStrong = WaiveraPalette.PurpleDarkAccent.copy(alpha = WaiveraPalette.BORDER_STRONG_ALPHA),
    inverseAccent = WaiveraPalette.Purple,
    unread = WaiveraPalette.PurpleDarkAccent,
    danger = WaiveraPalette.DangerDark,
    dangerText = WaiveraPalette.DangerDark,
    dangerFill = WaiveraPalette.DangerDark,
    onDangerFill = WaiveraPalette.OnDangerFillDark,
    dangerContainer = WaiveraPalette.DangerContainerDark,
    onDangerContainer = WaiveraPalette.OnDangerContainerDark,
    success = WaiveraPalette.SuccessDark,
    successText = WaiveraPalette.SuccessDark,
    warning = WaiveraPalette.WarningDark,
    warningText = WaiveraPalette.WarningDark,
    warningContainer = WaiveraPalette.WarningContainerDark,
    onWarningContainer = WaiveraPalette.OnWarningContainerDark,
    milestoneDot = WaiveraPalette.MilestoneDot,
    holidayIcon = WaiveraPalette.HolidayIcon,
    birthdayContainer = WaiveraPalette.BirthdayContainerDark,
    onBirthdayContainer = WaiveraPalette.OnBirthdayContainerDark,
    anniversaryContainer = WaiveraPalette.AnniversaryContainerDark,
    onAnniversaryContainer = WaiveraPalette.OnAnniversaryContainerDark,
    newHireContainer = WaiveraPalette.NewHireContainerDark,
    onNewHireContainer = WaiveraPalette.OnNewHireContainerDark,
    multipleContainer = WaiveraPalette.AccentSoftDark,
    onMultipleContainer = WaiveraPalette.OnAccentSoftDark,
    presenceOnline = WaiveraPalette.SuccessDark,
    presenceAway = WaiveraPalette.WarningDark,
    presenceOffline = WaiveraPalette.TextSecondaryDark,
    scrim = WaiveraPalette.Black.copy(alpha = WaiveraPalette.SCRIM_ALPHA),
    skeletonBase = WaiveraPalette.SkeletonBaseDark,
    skeletonHighlight = WaiveraPalette.SkeletonHighlightDark,
    secondaryContainer = WaiveraPalette.SurfaceMutedDark,
    secondaryBorder = WaiveraPalette.BorderDark,
    segmentSelected = WaiveraPalette.AccentSoftDark,
    tiles = WaiveraTileColors(
        brand = WaiveraTileTint(WaiveraPalette.AccentSoftDark, WaiveraPalette.OnAccentSoftDark),
        rose = WaiveraTileTint(WaiveraPalette.TileRoseDark, WaiveraPalette.OnTileRoseDark),
        amber = WaiveraTileTint(WaiveraPalette.TileAmberDark, WaiveraPalette.OnTileAmberDark),
        teal = WaiveraTileTint(WaiveraPalette.TileTealDark, WaiveraPalette.OnTileTealDark),
        neutral = WaiveraTileTint(WaiveraPalette.TileNeutralDark, WaiveraPalette.TextSecondaryDark),
        danger = WaiveraTileTint(WaiveraPalette.DangerContainerDark, WaiveraPalette.OnDangerContainerDark),
    ),
    brandWashAlpha = WaiveraPalette.BRAND_WASH_ALPHA_DARK,
)

/** Resolves the colour set for a theme. Exposed for screenshot tests and the app's window colours. */
fun waiveraColors(darkTheme: Boolean): WaiveraColors = if (darkTheme) DarkWaiveraColors else LightWaiveraColors

val LocalWaiveraColors = staticCompositionLocalOf { LightWaiveraColors }
