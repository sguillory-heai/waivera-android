package com.waivera.waivera.feature.calendar.ui

import androidx.annotation.DrawableRes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.waivera.waivera.feature.calendar.R

/**
 * Calendar-only sizes from 07-calendar.md that the design system has no token for yet. Kept in
 * one place so they can move to `WaiveraSizes` (docs/requests/calendar.md) without a hunt.
 */
internal object CalendarSizes {
    /** Event and milestone dots under a day number. */
    val Dot: Dp = 5.dp
    val DotGap: Dp = 3.dp

    /** The today ring is always drawn at this width, transparent when not today. */
    val CellBorder: Dp = 1.5.dp

    /** Owner-colour bar on the leading edge of an event card. */
    val EventAccentBar: Dp = 4.dp
    val EventCardMinHeight: Dp = 66.dp
    val WeekCellMinHeight: Dp = 76.dp

    val OrganizerAvatar: Dp = 32.dp
    val AttendeeAvatar: Dp = 34.dp
    val MilestoneCardAvatar: Dp = 40.dp

    /** Milestone badge: `max(17, round(size × 0.43))`, nudged out past the top-end corner. */
    val BadgeMin: Dp = 17.dp
    const val BADGE_RATIO = 0.43f
    const val BADGE_OFFSET_X_RATIO = 0.28f
    const val BADGE_OFFSET_Y_RATIO = 0.25f
    const val BADGE_GLYPH_RATIO = 0.52f
    const val BADGE_YEARS_RATIO = 0.42f
    val BadgeBorder: Dp = 2.dp

    /** Anniversary badges show the year count only on avatars at least this big. */
    val BadgeYearsMinAvatar: Dp = 40.dp
}

/** Icons the design system does not ship yet; same Material shapes, prefixed for this module. */
internal object CalendarIcons {
    @DrawableRes val ChevronLeft: Int = R.drawable.feature_calendar_ic_chevron_left

    @DrawableRes val Place: Int = R.drawable.feature_calendar_ic_place

    @DrawableRes val Video: Int = R.drawable.feature_calendar_ic_videocam

    @DrawableRes val OpenInNew: Int = R.drawable.feature_calendar_ic_open_in_new

    @DrawableRes val TimeOff: Int = R.drawable.feature_calendar_ic_time_off

    @DrawableRes val Award: Int = R.drawable.feature_calendar_ic_award

    @DrawableRes val Cake: Int = R.drawable.feature_calendar_ic_cake

    @DrawableRes val Celebration: Int = R.drawable.feature_calendar_ic_celebration
}
