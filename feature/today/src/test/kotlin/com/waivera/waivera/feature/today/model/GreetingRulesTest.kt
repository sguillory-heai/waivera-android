package com.waivera.waivera.feature.today.model

import java.time.Instant
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test

class GreetingRulesTest {
    private val chicago = ZoneId.of("America/Chicago")

    @Test
    fun dayPart_boundariesInTheEmployeesZone() {
        // 2026-10-28 is CDT (UTC-5).
        assertEquals(DayPart.EVENING, GreetingRules.dayPart(Instant.parse("2026-10-28T09:59:00Z"), chicago))
        assertEquals(DayPart.MORNING, GreetingRules.dayPart(Instant.parse("2026-10-28T10:00:00Z"), chicago))
        assertEquals(DayPart.MORNING, GreetingRules.dayPart(Instant.parse("2026-10-28T16:59:00Z"), chicago))
        assertEquals(DayPart.AFTERNOON, GreetingRules.dayPart(Instant.parse("2026-10-28T17:00:00Z"), chicago))
        assertEquals(DayPart.AFTERNOON, GreetingRules.dayPart(Instant.parse("2026-10-28T21:59:00Z"), chicago))
        assertEquals(DayPart.EVENING, GreetingRules.dayPart(Instant.parse("2026-10-28T22:00:00Z"), chicago))
    }

    @Test
    fun dayPart_followsTheZoneAcrossTheFallBackChange() {
        // 17:30 UTC is 12:30 CDT on Saturday 31 October but 11:30 CST on Sunday 1 November, after
        // clocks fall back at 02:00. A fixed -05:00 offset would wrongly say "afternoon" on Sunday.
        val instantSaturday = Instant.parse("2026-10-31T17:30:00Z")
        val instantSunday = Instant.parse("2026-11-01T17:30:00Z")
        assertEquals(DayPart.AFTERNOON, GreetingRules.dayPart(instantSaturday, chicago))
        assertEquals(DayPart.MORNING, GreetingRules.dayPart(instantSunday, chicago))
    }

    @Test
    fun dayPart_followsTheZoneAcrossTheSpringForwardChange() {
        // Clocks spring forward at 02:00 CST (08:00 UTC) on Sunday 8 March 2026. 10:30 UTC is then
        // 05:30 CDT (morning), where a fixed -06:00 offset would say 04:30 (evening); the day before,
        // the same 10:30 UTC is 04:30 CST.
        assertEquals(DayPart.EVENING, GreetingRules.dayPart(Instant.parse("2026-03-07T10:30:00Z"), chicago))
        assertEquals(DayPart.MORNING, GreetingRules.dayPart(Instant.parse("2026-03-08T10:30:00Z"), chicago))
        assertEquals(DayPart.EVENING, GreetingRules.dayPart(Instant.parse("2026-03-08T09:59:00Z"), chicago))
    }

    @Test
    fun dayPart_usesTheSavedZoneNotTheDevicesZone() {
        val instant = Instant.parse("2026-10-28T15:00:00Z")
        assertEquals(DayPart.MORNING, GreetingRules.dayPart(instant, chicago))
        assertEquals(DayPart.AFTERNOON, GreetingRules.dayPart(instant, ZoneId.of("Europe/London")))
        assertEquals(DayPart.EVENING, GreetingRules.dayPart(instant, ZoneId.of("Asia/Tokyo")))
    }

    @Test
    fun firstName_prefersTheProfileFirstNameThenTheDisplayName() {
        assertEquals("Alex", GreetingRules.firstName("Alex", "Alexandra Rivera"))
        assertEquals("Alexandra", GreetingRules.firstName("  ", "  Alexandra   Rivera "))
        assertEquals("Priya", GreetingRules.firstName(null, "Priya"))
    }

    @Test
    fun firstName_isNullWhenNothingUsable() {
        assertNull(GreetingRules.firstName(null, null))
        assertNull(GreetingRules.firstName("", "   "))
    }
}
