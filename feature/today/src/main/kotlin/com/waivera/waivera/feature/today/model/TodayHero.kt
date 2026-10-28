package com.waivera.waivera.feature.today.model

import androidx.compose.runtime.Immutable
import java.time.Duration
import java.time.LocalTime

/** The hero ring: [handled] today and [open] still needing the person. */
@Immutable
data class TodayProgress(val handled: Int, val open: Int) {
    val total: Int get() = handled + open
}

/**
 * The hero's stat tiles. Each is null while its source is still loading (the tile shows a dash),
 * never a guess. Events skip invitations not yet answered and ones the person declined, as Up
 * next does; the week is the employee's Sunday-to-Saturday week in their saved zone.
 */
@Immutable
data class TodayStats(val eventsToday: Int? = null, val unreadChats: Int? = null, val eventsThisWeek: Int? = null)

/**
 * Today's timed events on a track from [windowStart] to [windowEnd] (8 AM to 6 PM, widened to fit
 * the day's events). Positions are shares of the window's real duration, so a DST day places
 * every event and hour where its instant falls.
 */
@Immutable
data class DayTimeline(
    val windowStart: LocalTime,
    val windowEnd: LocalTime,
    val blocks: List<DayBlock>,
    /** Every whole hour inside the window. */
    val ticks: List<Float>,
    /** Every other hour from the window's start, with its clock time. */
    val labels: List<DayHourLabel>,
    /** Where now falls, while it is inside the window. */
    val now: Float?,
    val eventCount: Int,
    /** Time covered by events, overlaps counted once. */
    val scheduled: Duration,
)

/** One event on the track; [tone] cycles through the track's three colours in start order. */
@Immutable
data class DayBlock(val eventId: String, val start: Float, val end: Float, val tone: Int)

@Immutable
data class DayHourLabel(val at: Float, val time: LocalTime)
