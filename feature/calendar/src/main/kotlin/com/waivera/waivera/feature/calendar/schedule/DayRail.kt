package com.waivera.waivera.feature.calendar.schedule

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.waivera.waivera.core.designsystem.component.WaiveraTimelineEntry
import com.waivera.waivera.core.designsystem.component.WaiveraTimelineTone
import com.waivera.waivera.feature.calendar.R
import com.waivera.waivera.feature.calendar.model.AgendaItem
import com.waivera.waivera.feature.calendar.model.EventTiming
import com.waivera.waivera.feature.calendar.ui.clock
import java.time.ZonedDateTime

/**
 * What the Day view's timeline rail says beside an item: the time it starts, "Until" the time an
 * overnight item that began the day before ends, or "All day". Times are already in the
 * employee's saved zone.
 */
internal sealed interface RailTime {
    data class At(val time: ZonedDateTime) : RailTime

    data class Until(val time: ZonedDateTime) : RailTime

    data object AllDay : RailTime
}

/** The rail time for an item's [timing]; celebrations and holidays (no timing) are all day. */
internal fun railTime(timing: EventTiming?): RailTime = when (timing) {
    null, is EventTiming.AllDay -> RailTime.AllDay
    is EventTiming.Timed -> {
        val end = timing.end
        if (timing.startsBeforeDay && end != null) RailTime.Until(end) else RailTime.At(timing.start)
    }
}

/**
 * The rail block's colour, B's "Your day" tones: teal for events (the schedule's tile colour),
 * violet for time off and holidays, gold for celebrations. An ended item has none (quiet).
 */
internal fun railTone(item: AgendaItem, ended: Boolean): WaiveraTimelineTone? = when {
    ended -> null
    item is AgendaItem.EventCard -> WaiveraTimelineTone.Teal
    item is AgendaItem.MilestoneCard -> WaiveraTimelineTone.Gold
    else -> WaiveraTimelineTone.Violet
}

private val AgendaItem.timing: EventTiming?
    get() = when (this) {
        is AgendaItem.EventCard -> timing
        is AgendaItem.TimeOffCard -> timing
        is AgendaItem.HolidayCard, is AgendaItem.MilestoneCard -> null
    }

/**
 * One agenda item in the Day view, on concept B's timeline: its start time, the rail's block in
 * its tone, then the card. Week and Month keep the plain list, where the cards span days.
 */
@Composable
internal fun AgendaRailEntry(
    item: AgendaItem,
    ended: Boolean,
    isLast: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val rail = railTime(item.timing)
    val text = when (rail) {
        is RailTime.At -> clock(rail.time)
        is RailTime.Until -> clock(rail.time)
        RailTime.AllDay -> stringResource(R.string.feature_calendar_all_day)
    }
    // "Until" goes over the time, so the column never wraps "Until 6:00 / AM" (UI10-11).
    val kicker = if (rail is RailTime.Until) stringResource(R.string.feature_calendar_until_kicker) else null
    WaiveraTimelineEntry(
        time = text,
        kicker = kicker,
        tone = railTone(item, ended),
        isLast = isLast,
        modifier = modifier,
        content = content,
    )
}
