package com.waivera.waivera.feature.calendar.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.waivera.waivera.feature.calendar.R

/**
 * Calendar-only sizes from 07-calendar.md. Only the schedule grid and agenda use them, so they
 * stay in the feature; shared sizes (the milestone badge) live in `WaiveraSizes`.
 */
internal object CalendarSizes {
    /** Event and milestone dots under a day number. */
    val Dot: Dp = 5.dp
    val DotGap: Dp = 3.dp

    /** Celebration diamond; a little larger than [Dot] so both read as the same visual weight. */
    val MilestoneDot: Dp = 6.dp

    /** The selected day's tinted rounded square in the month grid (v5: 38) and in the taller week strip (40). */
    val DaySquare: Dp = 38.dp
    val DaySquareLarge: Dp = 40.dp
    val DaySquareCorner: Dp = 12.dp

    /**
     * The short dash under today's number (v5 decision 4, option C): 14 × 3, in its own slot
     * [TodayDashGap] under the number and [TodayDashInset] above the square's foot, so it keeps
     * its breathing room inside the square at every font size.
     */
    val TodayDashWidth: Dp = 14.dp
    val TodayDashHeight: Dp = 3.dp
    val TodayDashGap: Dp = 2.dp
    val TodayDashInset: Dp = 4.dp

    /** Owner-colour bar on the leading edge of an event card. */
    val EventAccentBar: Dp = 4.dp

    /** The line of today's "Now" divider in the agenda. */
    val NowLine: Dp = 1.5.dp
    val EventCardMinHeight: Dp = 66.dp
    val WeekCellMinHeight: Dp = 76.dp

    /**
     * A celebration card narrower than this (the Day view's timeline on a small phone) moves its
     * emoji into a smaller tile in the top-end corner, so the sentence gets the width back (UI10-05).
     */
    val CelebrationNarrowCard: Dp = 280.dp
    val CelebrationCornerTile: Dp = 28.dp

    val OrganizerAvatar: Dp = 32.dp
    val AttendeeAvatar: Dp = 34.dp
}

/** Calendar-only shapes. */
internal object CalendarShapes {
    /** Celebration marker under a day number; events use a circle. */
    val Diamond: Shape = GenericShape { size, _ ->
        moveTo(size.width / 2f, 0f)
        lineTo(size.width, size.height / 2f)
        lineTo(size.width / 2f, size.height)
        lineTo(0f, size.height / 2f)
        close()
    }
}

/** Icons the design system does not ship yet; same Material shapes, prefixed for this module. */
internal object CalendarIcons {
    @DrawableRes val ChevronLeft: Int = R.drawable.feature_calendar_ic_chevron_left

    @DrawableRes val Place: Int = R.drawable.feature_calendar_ic_place

    @DrawableRes val TimeOff: Int = R.drawable.feature_calendar_ic_time_off
}
