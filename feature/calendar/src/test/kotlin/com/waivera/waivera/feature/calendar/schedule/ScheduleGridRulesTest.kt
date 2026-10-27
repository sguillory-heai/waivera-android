package com.waivera.waivera.feature.calendar.schedule

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** When the grid switches to one-letter weekdays and Month gives way to the week strip. */
class ScheduleGridRulesTest {
    @Test
    fun `default and slightly larger font keep the full grid`() {
        assertFalse(usesCompactCalendar(1f))
        assertFalse(usesCompactCalendar(1.3f))
    }

    @Test
    fun `150 percent font and above go compact`() {
        assertTrue(usesCompactCalendar(1.5f))
        assertTrue(usesCompactCalendar(2f))
    }
}
