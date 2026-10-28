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
    /** Sheets and cards that sit above the surface (the dark elevation step; white in light). */
    val raised: Color,
    /** A text field's fill at rest. */
    val field: Color,
    /** A text field's edge at rest: at least 3:1 against the sheet and the field fill. */
    val fieldBorder: Color,
    val border: Color,
    val borderStrong: Color,
    val text: Color,
    val textSecondary: Color,
    val accent: Color,
    /** Text links ("Forgot password?", "Show all"): the accent in light, a lighter lilac in dark. */
    val link: Color,
    /** Fill of primary buttons and other filled accent controls; [onAccent] sits on it. */
    val accentFill: Color,
    val onAccent: Color,
    val accentSoft: Color,
    val onAccentSoft: Color,
    val accentWash: Color,
    val accentBorder: Color,
    val accentBorderStrong: Color,
    val inverseAccent: Color,
    /** Unread dots and count badges; [onAccent] (white) sits on it. */
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
    /**
     * Fill behind an avatar's initials or fallback glyph. Avatars have no border, so the fill
     * alone sets them apart from every surface and the brand wash (at least 1.3:1).
     */
    val avatarFill: Color,
    /**
     * Fill of a tonal button drawn on the brand wash ("Send wishes"): at least 1.3:1 against every
     * wash point, with [onAccentSoft] on it.
     */
    val tonalOnWash: Color,
    /** Category tints for [com.waivera.waivera.core.designsystem.component.WaiveraIconTile]. */
    val tiles: WaiveraTileColors,
    /** Opacity of the brand gradient when it washes a surface (celebration cards, sign-in hero). */
    val brandWashAlpha: Float,
    /**
     * The wash's last stop, alpha included: the logo's gold in light. In dark it is a peach at a
     * lower alpha, because gold over plum composited to an off-family khaki (UI8-04).
     */
    val brandWashEnd: Color,
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
    /** Celebration emoji tiles: the soft gold in light, a warm plum in dark (UI9-06). */
    val celebration: WaiveraTileTint,
)

internal val LightWaiveraColors = WaiveraColors(
    isDark = false,
    background = WaiveraPalette.BackgroundLight,
    surface = WaiveraPalette.SurfaceLight,
    surfaceMuted = WaiveraPalette.SurfaceMutedLight,
    raised = WaiveraPalette.SurfaceLight,
    field = WaiveraPalette.FieldLight,
    fieldBorder = WaiveraPalette.FieldBorderLight,
    border = WaiveraPalette.BorderLight,
    borderStrong = WaiveraPalette.BorderStrongLight,
    text = WaiveraPalette.TextLight,
    textSecondary = WaiveraPalette.TextSecondaryLight,
    accent = WaiveraPalette.Violet,
    link = WaiveraPalette.Violet,
    accentFill = WaiveraPalette.Violet,
    onAccent = WaiveraPalette.White,
    accentSoft = WaiveraPalette.AccentSoftLight,
    onAccentSoft = WaiveraPalette.PurpleDeep,
    accentWash = WaiveraPalette.Violet.copy(alpha = WaiveraPalette.WASH_ALPHA),
    accentBorder = WaiveraPalette.Violet.copy(alpha = WaiveraPalette.BORDER_ALPHA),
    accentBorderStrong = WaiveraPalette.Violet.copy(alpha = WaiveraPalette.BORDER_STRONG_ALPHA),
    inverseAccent = WaiveraPalette.VioletDarkAccent,
    unread = WaiveraPalette.Violet,
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
    avatarFill = WaiveraPalette.AvatarFillLight,
    tonalOnWash = WaiveraPalette.TonalOnWashLight,
    tiles = WaiveraTileColors(
        brand = WaiveraTileTint(WaiveraPalette.AccentSoftLight, WaiveraPalette.Violet),
        rose = WaiveraTileTint(WaiveraPalette.TileRoseLight, WaiveraPalette.OnTileRoseLight),
        amber = WaiveraTileTint(WaiveraPalette.TileAmberLight, WaiveraPalette.OnTileAmberLight),
        teal = WaiveraTileTint(WaiveraPalette.TileTealLight, WaiveraPalette.OnTileTealLight),
        neutral = WaiveraTileTint(WaiveraPalette.SurfaceMutedLight, WaiveraPalette.TextSecondaryLight),
        danger = WaiveraTileTint(WaiveraPalette.TileDangerLight, WaiveraPalette.OnTileDangerLight),
        celebration = WaiveraTileTint(WaiveraPalette.TileAmberLight, WaiveraPalette.OnTileAmberLight),
    ),
    brandWashAlpha = WaiveraPalette.BRAND_WASH_ALPHA_LIGHT,
    brandWashEnd = WaiveraPalette.BrandGold.copy(alpha = WaiveraPalette.BRAND_WASH_ALPHA_LIGHT),
)

internal val DarkWaiveraColors = WaiveraColors(
    isDark = true,
    background = WaiveraPalette.BackgroundDark,
    surface = WaiveraPalette.SurfaceDark,
    surfaceMuted = WaiveraPalette.SurfaceMutedDark,
    raised = WaiveraPalette.SurfaceMutedDark,
    field = WaiveraPalette.SurfaceDark,
    fieldBorder = WaiveraPalette.FieldBorderDark,
    border = WaiveraPalette.BorderDark,
    borderStrong = WaiveraPalette.BorderStrongDark,
    text = WaiveraPalette.TextDark,
    textSecondary = WaiveraPalette.TextSecondaryDark,
    accent = WaiveraPalette.VioletDarkAccent,
    link = WaiveraPalette.VioletDarkLink,
    accentFill = WaiveraPalette.VioletDarkFill,
    onAccent = WaiveraPalette.White,
    accentSoft = WaiveraPalette.AccentSoftDark,
    onAccentSoft = WaiveraPalette.OnAccentSoftDark,
    accentWash = WaiveraPalette.VioletDarkAccent.copy(alpha = WaiveraPalette.WASH_ALPHA),
    accentBorder = WaiveraPalette.VioletDarkAccent.copy(alpha = WaiveraPalette.BORDER_ALPHA),
    accentBorderStrong = WaiveraPalette.VioletDarkAccent.copy(alpha = WaiveraPalette.BORDER_STRONG_ALPHA),
    inverseAccent = WaiveraPalette.Violet,
    // Badges carry white counts, so the unread fill is the saturated violet, not the pastel accent.
    unread = WaiveraPalette.VioletDarkFill,
    danger = WaiveraPalette.DangerDark,
    dangerText = WaiveraPalette.DangerDark,
    dangerFill = WaiveraPalette.DangerFillDark,
    onDangerFill = WaiveraPalette.White,
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
    avatarFill = WaiveraPalette.AvatarFillDark,
    tonalOnWash = WaiveraPalette.TonalOnWashDark,
    tiles = WaiveraTileColors(
        brand = WaiveraTileTint(WaiveraPalette.AccentSoftDark, WaiveraPalette.VioletDarkLink),
        rose = WaiveraTileTint(WaiveraPalette.TileRoseDark, WaiveraPalette.OnTileRoseDark),
        amber = WaiveraTileTint(WaiveraPalette.TileAmberDark, WaiveraPalette.OnTileAmberDark),
        teal = WaiveraTileTint(WaiveraPalette.TileTealDark, WaiveraPalette.OnTileTealDark),
        neutral = WaiveraTileTint(WaiveraPalette.TileNeutralDark, WaiveraPalette.TextSecondaryDark),
        danger = WaiveraTileTint(WaiveraPalette.DangerContainerDark, WaiveraPalette.OnDangerContainerDark),
        celebration = WaiveraTileTint(WaiveraPalette.TileCelebrationDark, WaiveraPalette.OnTileAmberDark),
    ),
    brandWashAlpha = WaiveraPalette.BRAND_WASH_ALPHA_DARK,
    brandWashEnd = WaiveraPalette.BrandPeach.copy(alpha = WaiveraPalette.BRAND_WASH_END_ALPHA_DARK),
)

/** Resolves the colour set for a theme. Exposed for screenshot tests and the app's window colours. */
fun waiveraColors(darkTheme: Boolean): WaiveraColors = if (darkTheme) DarkWaiveraColors else LightWaiveraColors

val LocalWaiveraColors = staticCompositionLocalOf { LightWaiveraColors }
