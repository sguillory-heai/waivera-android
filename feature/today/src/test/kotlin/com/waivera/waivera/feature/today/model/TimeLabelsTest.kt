package com.waivera.waivera.feature.today.model

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.TimeZone
import kotlin.test.assertEquals
import org.junit.Test

class TimeLabelsTest {
    private val chicago = ZoneId.of("America/Chicago")
    private val now = Instant.parse("2026-11-01T16:30:00Z") // 10:30 CST, the day DST ends

    private fun past(at: String) = TimeLabels.past(Instant.parse(at), now, chicago)

    @Test
    fun `starts within the hour reads in N min, rounded up`() {
        assertEquals(UpcomingLabel.InMinutes(25), TimeLabels.upcoming(now.plusSeconds(25 * 60), null, now, chicago))
        assertEquals(UpcomingLabel.InMinutes(1), TimeLabels.upcoming(now.plusSeconds(30), null, now, chicago))
        assertEquals(UpcomingLabel.InMinutes(59), TimeLabels.upcoming(now.plusSeconds(58 * 60 + 1), null, now, chicago))
    }

    @Test
    fun `starts within six hours reads in hours and minutes`() {
        assertEquals(UpcomingLabel.InHours(1, 0), TimeLabels.upcoming(now.plusSeconds(3_600), null, now, chicago))
        assertEquals(UpcomingLabel.InHours(5, 59), TimeLabels.upcoming(now.plusSeconds(359 * 60), null, now, chicago))
    }

    @Test
    fun `later today, tomorrow and other days use the clock in the saved zone`() {
        val evening = Instant.parse("2026-11-02T01:00:00Z") // 19:00 CST on 1 Nov
        assertEquals(UpcomingLabel.TodayAt(LocalTime.of(19, 0)), TimeLabels.upcoming(evening, null, now, chicago))
        val tomorrow = Instant.parse("2026-11-02T15:00:00Z") // 09:00 CST on 2 Nov
        assertEquals(UpcomingLabel.TomorrowAt(LocalTime.of(9, 0)), TimeLabels.upcoming(tomorrow, null, now, chicago))
        val later = Instant.parse("2026-11-04T15:00:00Z")
        assertEquals(
            UpcomingLabel.OnDayAt(LocalDate.of(2026, 11, 4), LocalTime.of(9, 0)),
            TimeLabels.upcoming(later, null, now, chicago),
        )
    }

    @Test
    fun `an item in progress reads Now until its local end`() {
        val label = TimeLabels.upcoming(now.minusSeconds(600), Instant.parse("2026-11-01T18:00:00Z"), now, chicago)
        assertEquals(UpcomingLabel.Now(LocalTime.of(12, 0)), label)
        assertEquals(UpcomingLabel.Now(null), TimeLabels.upcoming(now, null, now, chicago))
    }

    @Test
    fun `fall back - an hour and a half of real time across the repeated hour`() {
        // 01:30 CDT (first pass) to 02:00 CST: the wall clock moves 30 minutes, real time 90.
        val before = Instant.parse("2026-11-01T06:30:00Z")
        val start = Instant.parse("2026-11-01T08:00:00Z")
        assertEquals(UpcomingLabel.InHours(1, 30), TimeLabels.upcoming(start, null, before, chicago))
    }

    @Test
    fun `spring forward - one hour of real time across the skipped hour`() {
        // 01:30 CST to 03:30 CDT on 8 March 2026: the wall clock moves two hours, real time one.
        val before = Instant.parse("2026-03-08T07:30:00Z")
        val start = Instant.parse("2026-03-08T08:30:00Z")
        assertEquals(UpcomingLabel.InHours(1, 0), TimeLabels.upcoming(start, null, before, chicago))
        val evening = Instant.parse("2026-03-09T01:00:00Z") // 20:00 CDT on 8 March
        assertEquals(UpcomingLabel.TodayAt(LocalTime.of(20, 0)), TimeLabels.upcoming(evening, null, before, chicago))
    }

    @Test
    fun `the device zone never changes the labels`() {
        val original = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"))
            val evening = Instant.parse("2026-11-02T01:00:00Z")
            assertEquals(UpcomingLabel.TodayAt(LocalTime.of(19, 0)), TimeLabels.upcoming(evening, null, now, chicago))
            assertEquals(PastLabel.TodayAt(LocalTime.of(0, 30)), past("2026-11-01T05:30:00Z"))
        } finally {
            TimeZone.setDefault(original)
        }
    }

    @Test
    fun `all-day items read today, tomorrow or their date`() {
        val today = LocalDate.of(2026, 11, 1)
        assertEquals(UpcomingLabel.AllDayToday, TimeLabels.allDay(today, today.plusDays(1), today))
        assertEquals(UpcomingLabel.AllDayToday, TimeLabels.allDay(today.minusDays(2), today.plusDays(1), today))
        assertEquals(UpcomingLabel.AllDayTomorrow, TimeLabels.allDay(today.plusDays(1), null, today))
        assertEquals(UpcomingLabel.AllDayOn(today.plusDays(3)), TimeLabels.allDay(today.plusDays(3), null, today))
    }

    @Test
    fun `past labels follow the chat list rules`() {
        assertEquals(PastLabel.JustNow, TimeLabels.past(now.minusSeconds(59), now, chicago))
        assertEquals(PastLabel.JustNow, TimeLabels.past(now.plusSeconds(30), now, chicago))
        assertEquals(PastLabel.MinutesAgo(5), TimeLabels.past(now.minusSeconds(5 * 60 + 20), now, chicago))
        assertEquals(PastLabel.TodayAt(LocalTime.of(8, 0)), past("2026-11-01T14:00:00Z"))
        assertEquals(PastLabel.Yesterday, past("2026-10-31T20:00:00Z"))
        assertEquals(
            PastLabel.OnDate(LocalDate.of(2026, 10, 20)),
            past("2026-10-20T13:02:44Z"),
        )
    }

    @Test
    fun `past labels place the repeated fall-back hour on the right day`() {
        // 00:30 CDT on 1 Nov is today; 23:30 CDT on 31 Oct is yesterday, even though both are within 25 hours.
        assertEquals(PastLabel.TodayAt(LocalTime.of(0, 30)), past("2026-11-01T05:30:00Z"))
        assertEquals(PastLabel.Yesterday, past("2026-11-01T04:30:00Z"))
    }
}
