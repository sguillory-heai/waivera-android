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

    /** The today ring is always drawn at this width, transparent when not today. */
    val CellBorder: Dp = 1.5.dp

    /** Owner-colour bar on the leading edge of an event card. */
    val EventAccentBar: Dp = 4.dp
    val EventCardMinHeight: Dp = 66.dp
    val WeekCellMinHeight: Dp = 76.dp

    val OrganizerAvatar: Dp = 32.dp
    val AttendeeAvatar: Dp = 34.dp
    val MilestoneCardAvatar: Dp = 40.dp
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
