package com.waivera.waivera.feature.calendar.model

import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScheduleTextTest {
    private val chicago = ZoneId.of("America/Chicago")
    private val tokyo = ZoneId.of("Asia/Tokyo")

    @Test
    fun timeOffPartsMatchTheSpecSubtitle() {
        assertEquals("Approved", ScheduleLabels.sentenceCase("APPROVED"))
        assertEquals("half day am", ScheduleLabels.requestTypeLabel("HALF_DAY_AM"))
        assertEquals("full day", ScheduleLabels.requestTypeLabel(null))
        assertEquals("Cancellation pending", ScheduleLabels.sentenceCase("CANCELLATION_PENDING"))
        assertNull(ScheduleLabels.sentenceCase("  "))
    }

    @Test
    fun ownerColourParsesServerHexAndRejectsAnythingElse() {
        assertEquals(0xFF7E57C2, ScheduleLabels.parseOwnerColor("#7E57C2"))
        assertEquals(0xFF7E57C2, ScheduleLabels.parseOwnerColor("7e57c2"))
        assertEquals(0x807E57C2, ScheduleLabels.parseOwnerColor("#807E57C2"))
        assertNull(ScheduleLabels.parseOwnerColor("purple"))
        assertNull(ScheduleLabels.parseOwnerColor("#7E57"))
        assertNull(ScheduleLabels.parseOwnerColor(null))
    }

    @Test
    fun weekdayHeadersStartOnSunday() {
        assertEquals(listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"), ScheduleText.weekdayHeaders(Locale.US))
    }

    @Test
    fun narrowWeekdaysAreOneLetter() {
        assertEquals(listOf("S", "M", "T", "W", "T", "F", "S"), ScheduleText.weekdayHeaders(Locale.US, narrow = true))
        assertEquals("T", ScheduleText.weekdayNarrow(LocalDate.of(2026, 10, 27), Locale.US))
    }

    @Test
    fun periodPiecesFormatInEnglish() {
        val date = LocalDate.of(2026, 10, 27)
        assertEquals("October 2026", ScheduleText.monthYear(date, Locale.US))
        assertEquals("Tue, October\u00A027", ScheduleText.weekdayMonthDay(date, Locale.US))
        assertEquals("Tuesday, October\u00A027", ScheduleText.fullDay(date, Locale.US))
        assertEquals("Tue, Oct\u00A027", ScheduleText.shortDay(date, 2026, Locale.US))
        assertEquals("Wed, Jan\u00A06, 2027", ScheduleText.shortDay(LocalDate.of(2027, 1, 6), 2026, Locale.US))
    }

    @Test
    fun zoneNoticeShowsWhenTheSavedZoneDiffersFromTheDevice() {
        val notice = ZoneNotice.of(chicago, "America/Chicago", tokyo)
        assertNotNull(notice)
        assertFalse(notice.isFallback)
        assertEquals("Central Time", ScheduleClock.zoneName(notice.zone, Locale.US))
        assertNull(ZoneNotice.of(chicago, "America/Chicago", chicago))
    }

    @Test
    fun zoneNoticeSaysWhenTheFallbackZoneIsInEffect() {
        // Missing, fixed-offset and unknown saved zones all fall back to Chicago, and the UI says so.
        listOf(null, "", "-05:00", "Mars/Olympus").forEach { saved ->
            val notice = ZoneNotice.of(chicago, saved, chicago)
            assertNotNull(notice, "for $saved")
            assertTrue(notice.isFallback)
        }
    }
}
