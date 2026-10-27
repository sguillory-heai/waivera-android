package com.waivera.waivera.core.network

import com.waivera.waivera.core.network.time.WaiveraTime
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WaiveraTimeTest {
    @Test
    fun zoneLessTimestampsAreUtcWithAnyFractionLength() {
        val expected = Instant.parse("2026-10-20T14:32:11Z")
        assertEquals(expected, WaiveraTime.parseInstant("2026-10-20T14:32:11"))
        assertEquals(
            expected.plusMillis(482).plusNanos(913_000),
            WaiveraTime.parseInstant("2026-10-20T14:32:11.482913"),
        )
        assertEquals(expected.plusMillis(500), WaiveraTime.parseInstant("2026-10-20T14:32:11.5"))
        assertEquals(expected.plusNanos(123_456_700), WaiveraTime.parseInstant("2026-10-20T14:32:11.1234567"))
    }

    @Test
    fun explicitOffsetsAreNeverConvertedAgain() {
        val expected = Instant.parse("2026-10-20T14:40:02.118374Z")
        assertEquals(expected, WaiveraTime.parseInstant("2026-10-20T14:40:02.118374Z"))
        assertEquals(expected, WaiveraTime.parseInstant("2026-10-20T09:40:02.118374-05:00"))
        assertEquals(expected, WaiveraTime.parseInstant("2026-10-20T09:40:02.118374-0500"))
        assertEquals(expected, WaiveraTime.parseInstant("2026-10-20T14:40:02.1183740z"))
    }

    @Test
    fun bareAndZonedFormsOfTheSameInstantAreEqual() {
        assertEquals(
            WaiveraTime.parseInstant("2026-11-01T06:30:00"),
            WaiveraTime.parseInstant("2026-11-01T01:30:00-05:00"),
        )
        // 01:30 occurs twice in Chicago on 1 Nov 2026; the explicit offset picks the second one.
        assertEquals(
            WaiveraTime.parseInstant("2026-11-01T07:30:00"),
            WaiveraTime.parseInstant("2026-11-01T01:30:00-06:00"),
        )
    }

    @Test
    fun blankAndGarbageAreNull() {
        assertNull(WaiveraTime.parseInstant(null))
        assertNull(WaiveraTime.parseInstant("  "))
        assertNull(WaiveraTime.parseInstant("yesterday"))
    }

    @Test
    fun dateOnlyValuesKeepTheirCalendarDate() {
        assertEquals(LocalDate.of(1991, 6, 14), WaiveraTime.parseLocalDate("1991-06-14T00:00:00"))
        assertEquals(LocalDate.of(2026, 2, 28), WaiveraTime.parseLocalDate("2026-02-28"))
        assertEquals(LocalDate.of(2028, 2, 29), WaiveraTime.parseLocalDate("2028-02-29"))
        assertNull(WaiveraTime.parseLocalDate("2026-02-30"))
    }

    @Test
    fun employeeZoneFallsBackToChicagoNeverTheDevice() {
        assertEquals(ZoneId.of("Asia/Kolkata"), WaiveraTime.employeeZone("Asia/Kolkata"))
        assertEquals(WaiveraTime.FallbackZone, WaiveraTime.employeeZone(null))
        assertEquals(WaiveraTime.FallbackZone, WaiveraTime.employeeZone("Mars/Olympus"))
        assertEquals(WaiveraTime.FallbackZone, WaiveraTime.employeeZone("+05:30"))
    }

    @Test
    fun queryInstantsAreUtcWithZ() {
        assertEquals("2026-03-08T06:00:00Z", WaiveraTime.formatQueryInstant(Instant.parse("2026-03-08T06:00:00Z")))
    }
}
