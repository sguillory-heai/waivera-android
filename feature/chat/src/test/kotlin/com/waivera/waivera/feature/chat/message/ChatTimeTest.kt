package com.waivera.waivera.feature.chat.message

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlin.test.assertEquals
import org.junit.Test

class ChatTimeTest {
    private val chicago = ZoneId.of("America/Chicago")
    private val now = Instant.parse("2026-11-01T16:30:00Z")

    private fun ago(seconds: Long) = ChatTime.relative(now.minusSeconds(seconds), now, chicago)

    @Test
    fun relativeBuckets() {
        assertEquals(RelativeTime.JustNow, ago(0))
        assertEquals(RelativeTime.JustNow, ago(9))
        assertEquals(RelativeTime.Seconds(45), ago(45))
        assertEquals(RelativeTime.Minutes(2), ago(150))
        assertEquals(RelativeTime.Hours(3), ago(3 * 3_600 + 59))
        assertEquals(RelativeTime.Days(6), ago(6 * 86_400))
    }

    @Test
    fun futureTimestampsReadAsJustNow() {
        assertEquals(RelativeTime.JustNow, ChatTime.relative(now.plusSeconds(30), now, chicago))
    }

    @Test
    fun aWeekOrMoreShowsTheDateWithoutTheYearInTheSameYear() {
        assertEquals(RelativeTime.OnDate(LocalDate.of(2026, 10, 25), showYear = false), ago(7 * 86_400))
    }

    @Test
    fun anotherYearShowsTheYear() {
        val old = Instant.parse("2025-12-31T18:00:00Z")
        assertEquals(
            RelativeTime.OnDate(LocalDate.of(2025, 12, 31), showYear = true),
            ChatTime.relative(old, now, chicago),
        )
    }

    @Test
    fun elapsedDaysCountRealTimeAcrossTheFallBackChange() {
        // 2026-10-31 11:30 CDT to 2026-11-01 10:30 CST is exactly 24 elapsed hours: one day.
        val dayBefore = Instant.parse("2026-10-31T16:30:00Z")
        assertEquals(RelativeTime.Days(1), ChatTime.relative(dayBefore, now, chicago))
        assertEquals(LocalDate.of(2026, 10, 31), ChatTime.day(dayBefore, chicago))
    }

    @Test
    fun clockUsesTheGivenZoneOnBothSidesOfTheChange() {
        assertEquals("1:30 AM", ChatTime.clock(Instant.parse("2026-11-01T06:30:00Z"), chicago, Locale.US))
        assertEquals("1:30 AM", ChatTime.clock(Instant.parse("2026-11-01T07:30:00Z"), chicago, Locale.US))
    }

    @Test
    fun dayLabelIsTodayYesterdayOrTheDateWithTheYearOnlyInAnotherYear() {
        val today = LocalDate.of(2026, 11, 1)
        assertEquals(DayLabel.Today, DayLabel.of(today, today))
        assertEquals(DayLabel.Yesterday, DayLabel.of(LocalDate.of(2026, 10, 31), today))
        assertEquals(
            DayLabel.OnDate(LocalDate.of(2026, 10, 20), showYear = false),
            DayLabel.of(LocalDate.of(2026, 10, 20), today),
        )
        assertEquals(
            DayLabel.OnDate(LocalDate.of(2025, 12, 30), showYear = true),
            DayLabel.of(LocalDate.of(2025, 12, 30), today),
        )
    }

    @Test
    fun dayLabelsCountSavedZoneDaysAcrossTheFallBack() {
        // 1 November 2026 is 25 hours long in Chicago. Late that evening, its 00:30 CDT is still
        // Today and 31 October 01:00 CDT is Yesterday, though more than 24 hours have passed.
        val evening = Instant.parse("2026-11-02T05:30:00Z")
        val today = ChatTime.day(evening, chicago)
        assertEquals(DayLabel.Today, DayLabel.of(ChatTime.day(Instant.parse("2026-11-01T05:30:00Z"), chicago), today))
        assertEquals(
            DayLabel.Yesterday,
            DayLabel.of(ChatTime.day(Instant.parse("2026-10-31T06:00:00Z"), chicago), today),
        )
    }

    @Test
    fun shortDateFormats() {
        assertEquals("Oct 20", ChatTime.shortDate(LocalDate.of(2026, 10, 20), showYear = false, Locale.US))
        assertEquals("Oct 20, 2025", ChatTime.shortDate(LocalDate.of(2025, 10, 20), showYear = true, Locale.US))
    }

    @Test
    fun theChatClockReadsTheZoneOnEveryUse() {
        var zone = chicago
        val clock = ChatClock(java.time.Clock.fixed(now, ZoneId.of("UTC"))) { zone }
        assertEquals(chicago, clock.zone)
        zone = ZoneId.of("Europe/London")
        assertEquals(ZoneId.of("Europe/London"), clock.zone)
        assertEquals(now, clock.now())
    }
}
