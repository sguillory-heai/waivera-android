package com.waivera.waivera.feature.calendar.model

import com.waivera.waivera.core.data.model.DateRange
import com.waivera.waivera.core.data.model.Holiday
import com.waivera.waivera.core.data.model.Milestone
import com.waivera.waivera.core.data.model.ScheduleEvent
import com.waivera.waivera.core.data.model.ScheduleFeed
import com.waivera.waivera.core.data.model.TimeOffEntry
import com.waivera.waivera.core.network.dto.MilestoneType
import com.waivera.waivera.core.network.time.WaiveraTime
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AgendaTest {
    private val chicago = ZoneId.of("America/Chicago")
    private val tokyo = ZoneId.of("Asia/Tokyo")
    private val sydney = ZoneId.of("Australia/Sydney")
    private val range = DateRange(LocalDate.of(2026, 9, 27), LocalDate.of(2026, 11, 8))

    private fun timed(id: String, start: String, end: String?, title: String = id) = ScheduleEvent(
        id = id,
        title = title,
        startAt = requireNotNull(WaiveraTime.parseInstant(start)),
        endAt = end?.let(WaiveraTime::parseInstant),
    )

    private fun feed(
        vararg events: ScheduleEvent,
        timeOff: List<TimeOffEntry> = emptyList(),
        holidays: List<Holiday> = emptyList(),
    ) = ScheduleFeed(range, chicago.id, events.toList(), timeOff, holidays)

    private fun daysOf(event: ScheduleEvent, zone: ZoneId, vararg candidates: LocalDate) =
        candidates.filter { day -> Agenda.eventsOn(listOf(event), zone, day).isNotEmpty() }

    @Test
    fun agendaOrderIsMilestoneHolidayTimeOffThenEventsByStartInstant() {
        val day = LocalDate.of(2026, 10, 15)
        // Mixed zone representations: string order would put 11:30-05:00 (16:30Z) after 17:00Z wrongly.
        val late = timed("late", "2026-10-15T17:00:00Z", "2026-10-15T18:00:00Z", "B late")
        val early = timed("early", "2026-10-15T11:30:00-05:00", "2026-10-15T12:00:00-05:00", "A early")
        val holiday = Holiday(1, "Founders day", day, day)
        val timeOff = TimeOffEntry(
            2,
            startAt = Instant.parse("2026-10-15T05:00:00Z"),
            endAt = Instant.parse("2026-10-16T05:00:00Z"),
            status = "APPROVED",
        )
        val milestones = MilestoneIndex(listOf(Milestone(MilestoneType.BIRTHDAY, "u1", "Priya", month = 10, day = 15)))

        val items = Agenda.forDay(
            feed(late, early, timeOff = listOf(timeOff), holidays = listOf(holiday)),
            chicago,
            milestones,
            day,
        )

        assertIs<AgendaItem.MilestoneCard>(items[0])
        assertIs<AgendaItem.HolidayCard>(items[1])
        assertIs<AgendaItem.TimeOffCard>(items[2])
        assertEquals(listOf("early", "late"), items.drop(3).map { (it as AgendaItem.EventCard).event.id })
    }

    @Test
    fun eventFrom23To01AppearsOnBothDays() {
        // 23:00 CDT on 20 Oct to 01:00 CDT on 21 Oct.
        val event = timed("night", "2026-10-21T04:00:00Z", "2026-10-21T06:00:00Z")
        val days =
            daysOf(event, chicago, LocalDate.of(2026, 10, 20), LocalDate.of(2026, 10, 21), LocalDate.of(2026, 10, 22))
        assertEquals(listOf(LocalDate.of(2026, 10, 20), LocalDate.of(2026, 10, 21)), days)

        val onSecondDay = Agenda.timing(event, chicago, LocalDate.of(2026, 10, 21)) as EventTiming.Timed
        assertTrue(onSecondDay.startsBeforeDay)
        assertFalse(onSecondDay.endsAfterDay)
    }

    @Test
    fun eventEndingExactlyAtMidnightAppearsOnlyOnItsStartDay() {
        // 22:00 to 00:00 CDT: the Expo app's inclusive end also put it on the next day.
        val event = timed("evening", "2026-10-21T03:00:00Z", "2026-10-21T05:00:00Z")
        assertEquals(
            listOf(LocalDate.of(2026, 10, 20)),
            daysOf(event, chicago, LocalDate.of(2026, 10, 20), LocalDate.of(2026, 10, 21)),
        )
    }

    @Test
    fun eventSpanningSpringForwardHasCorrectClocksAndDay() {
        // 01:30 CST to 03:30 CDT on 8 Mar 2026: one real hour across the missing 02:00 hour.
        val event = timed("spring", "2026-03-08T07:30:00Z", "2026-03-08T08:30:00Z")
        val day = LocalDate.of(2026, 3, 8)
        assertEquals(listOf(day), daysOf(event, chicago, LocalDate.of(2026, 3, 7), day, LocalDate.of(2026, 3, 9)))
        val timing = Agenda.timing(event, chicago, day) as EventTiming.Timed
        assertEquals(LocalTime.of(1, 30), timing.start.toLocalTime())
        assertEquals(LocalTime.of(3, 30), timing.end?.toLocalTime())
        assertEquals("1:30 AM", ScheduleClock.clock(timing.start, Locale.US))
        assertEquals("3:30 AM", ScheduleClock.clock(requireNotNull(timing.end), Locale.US))
    }

    @Test
    fun eventAt0030OnTheFallBackDayIsPlacedOnExactlyOneDay() {
        val event = timed("early", "2026-11-01T05:30:00Z", "2026-11-01T06:00:00Z")
        val days =
            daysOf(event, chicago, LocalDate.of(2026, 10, 31), LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 2))
        assertEquals(listOf(LocalDate.of(2026, 11, 1)), days)
        val timing = Agenda.timing(event, chicago, LocalDate.of(2026, 11, 1)) as EventTiming.Timed
        assertEquals("12:30 AM", ScheduleClock.clock(timing.start, Locale.US))
    }

    @Test
    fun repeatedFallBackHourIsPlacedOnceAndNamesItsOffset() {
        // 01:30 happens twice on 1 Nov 2026 in Chicago: 06:30Z (CDT) and 07:30Z (CST).
        val first = timed("first", "2026-11-01T06:30:00Z", "2026-11-01T06:45:00Z")
        val second = timed("second", "2026-11-01T07:30:00Z", "2026-11-01T07:45:00Z")
        val day = LocalDate.of(2026, 11, 1)
        listOf(first, second).forEach { event ->
            assertEquals(
                listOf(day),
                daysOf(event, chicago, LocalDate.of(2026, 10, 31), day, LocalDate.of(2026, 11, 2)),
            )
        }
        val firstStart = (Agenda.timing(first, chicago, day) as EventTiming.Timed).start
        val secondStart = (Agenda.timing(second, chicago, day) as EventTiming.Timed).start
        assertEquals("1:30 AM CDT", ScheduleClock.clock(firstStart, Locale.US))
        assertEquals("1:30 AM CST", ScheduleClock.clock(secondStart, Locale.US))
        // Sorted by instant, so CDT comes first even though the wall clocks match.
        assertEquals(listOf("first", "second"), Agenda.eventsOn(listOf(second, first), chicago, day).map { it.id })
    }

    @Test
    fun overnightShiftAcrossFallBackShowsOnBothDays() {
        // 22:00 CDT Saturday to 06:00 CST Sunday: nine real hours.
        val event = timed("overnight", "2026-11-01T03:00:00Z", "2026-11-01T12:00:00Z")
        val saturday = LocalDate.of(2026, 10, 31)
        val sunday = LocalDate.of(2026, 11, 1)
        assertEquals(listOf(saturday, sunday), daysOf(event, chicago, saturday, sunday, LocalDate.of(2026, 11, 2)))
        val timing = Agenda.timing(event, chicago, saturday) as EventTiming.Timed
        assertEquals("10:00 PM", ScheduleClock.clock(timing.start, Locale.US))
        assertEquals("6:00 AM", ScheduleClock.clock(requireNotNull(timing.end), Locale.US))
        assertTrue(timing.endsAfterDay)
    }

    @Test
    fun savedZoneWinsOverTheDeviceZone() {
        // 22:00 CST on 2 Nov in Chicago is already 12:00 on 3 Nov in Tokyo.
        val event = timed("call", "2026-11-03T04:00:00Z", "2026-11-03T05:00:00Z")
        assertEquals(
            listOf(LocalDate.of(2026, 11, 2)),
            daysOf(event, chicago, LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 3)),
        )
        assertEquals(
            listOf(LocalDate.of(2026, 11, 3)),
            daysOf(event, tokyo, LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 3)),
        )
        val timing = Agenda.timing(event, chicago, LocalDate.of(2026, 11, 2)) as EventTiming.Timed
        assertEquals("10:00 PM", ScheduleClock.clock(timing.start, Locale.US))
    }

    @Test
    fun southernHemisphereSpringForwardEventIsPlacedInSydney() {
        // 01:30 AEST to 03:30 AEDT on Sunday 4 Oct 2026 (02:00 does not exist there).
        val event = timed("sydney", "2026-10-03T15:30:00Z", "2026-10-03T16:30:00Z")
        val day = LocalDate.of(2026, 10, 4)
        assertEquals(listOf(day), daysOf(event, sydney, LocalDate.of(2026, 10, 3), day))
        // The same instants are still Saturday 3 Oct in Chicago.
        assertEquals(listOf(LocalDate.of(2026, 10, 3)), daysOf(event, chicago, LocalDate.of(2026, 10, 3), day))
        val timing = Agenda.timing(event, sydney, day) as EventTiming.Timed
        assertEquals("1:30 AM", ScheduleClock.clock(timing.start, Locale.US))
        assertEquals("3:30 AM", ScheduleClock.clock(requireNotNull(timing.end), Locale.US))
    }

    @Test
    fun southernHemisphereFallBackRepeatsTheHourOnce() {
        // 5 Apr 2026: 02:30 happens twice in Sydney (15:30Z AEDT, 16:30Z AEST).
        val first = timed("first", "2026-04-04T15:30:00Z", "2026-04-04T15:45:00Z")
        val second = timed("second", "2026-04-04T16:30:00Z", "2026-04-04T16:45:00Z")
        val day = LocalDate.of(2026, 4, 5)
        assertEquals(listOf("first", "second"), Agenda.eventsOn(listOf(second, first), sydney, day).map { it.id })
        assertTrue(ScheduleClock.isRepeatedWallTime(Instant.parse("2026-04-04T15:30:00Z").atZone(sydney)))
        assertFalse(ScheduleClock.isRepeatedWallTime(Instant.parse("2026-04-04T18:30:00Z").atZone(sydney)))
    }

    @Test
    fun zoneLessStartTimeIsReadAsUtc() {
        val event = timed("bare", "2026-11-01T17:00:00", "2026-11-01T18:00:00.482913")
        assertEquals(Instant.parse("2026-11-01T17:00:00Z"), event.startAt)
        assertEquals(
            LocalTime.of(11, 0),
            (Agenda.timing(event, chicago, LocalDate.of(2026, 11, 1)) as EventTiming.Timed).start.toLocalTime(),
        )
    }

    @Test
    fun allDayEventsUseLiteralDatesWithAnExclusiveEnd() {
        val event = ScheduleEvent(
            id = "retreat",
            allDayStart = LocalDate.of(2026, 11, 1),
            allDayEndExclusive = LocalDate.of(2026, 11, 3),
        )
        // The zone never shifts an all-day date, even in Tokyo or across fall back.
        listOf(chicago, tokyo, sydney).forEach { zone ->
            assertEquals(
                listOf(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 2)),
                daysOf(
                    event,
                    zone,
                    LocalDate.of(2026, 10, 31),
                    LocalDate.of(2026, 11, 1),
                    LocalDate.of(2026, 11, 2),
                    LocalDate.of(2026, 11, 3),
                ),
            )
        }
        assertEquals(
            EventTiming.AllDay(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 2)),
            Agenda.timing(event, chicago, LocalDate.of(2026, 11, 1)),
        )
        // All-day events come before timed ones.
        val timedEvent = timed("t", "2026-11-01T14:00:00Z", "2026-11-01T15:00:00Z")
        assertEquals(
            listOf("retreat", "t"),
            Agenda.eventsOn(listOf(timedEvent, event), chicago, LocalDate.of(2026, 11, 1)).map {
                it.id
            },
        )
    }

    @Test
    fun spillDaysOfTheMonthGridGetEventMarkers() {
        val leading = timed("leading", "2026-09-28T15:00:00Z", "2026-09-28T16:00:00Z")
        val trailing = timed("trailing", "2026-11-05T15:00:00Z", "2026-11-05T16:00:00Z")
        val cells = SchedulePeriods.cellsFor(ScheduleView.MONTH, LocalDate.of(2026, 10, 1))
        val milestones = MilestoneIndex(listOf(Milestone(MilestoneType.BIRTHDAY, "u1", month = 11, day = 6)))
        val markers = Agenda.markers(feed(leading, trailing), chicago, milestones, cells)
        assertEquals(1, markers.getValue(LocalDate.of(2026, 9, 28)).events)
        assertEquals(1, markers.getValue(LocalDate.of(2026, 11, 5)).events)
        assertEquals(1, markers.getValue(LocalDate.of(2026, 11, 6)).milestones)
        assertTrue(markers.getValue(LocalDate.of(2026, 10, 15)).isEmpty)
    }

    @Test
    fun multiDayEventIsCountedOnEveryDayItTouches() {
        val overnight = timed("overnight", "2026-11-01T03:00:00Z", "2026-11-01T12:00:00Z")
        val cells = SchedulePeriods.cellsFor(ScheduleView.WEEK, LocalDate.of(2026, 10, 31))
        val markers = Agenda.markers(feed(overnight), chicago, MilestoneIndex.Empty, cells)
        assertEquals(1, markers.getValue(LocalDate.of(2026, 10, 31)).events)
        // 1 Nov starts the next week, so it is outside these cells and not counted here.
        assertEquals(7, markers.size)
    }
}
