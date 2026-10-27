package com.waivera.waivera.feature.calendar.data

import com.waivera.waivera.core.data.calendar.CalendarMath
import com.waivera.waivera.core.data.model.ScheduleEvent
import com.waivera.waivera.core.data.model.ScheduleFeed
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ScheduleEventStoreTest {
    private val october = CalendarMath.monthGrid(YearMonth.of(2026, 10))
    private val november = CalendarMath.monthGrid(YearMonth.of(2026, 11))

    @Test
    fun remembersTheLatestRangeAnEventWasSeenIn() {
        val store = ScheduleEventStore()
        store.remember(ScheduleFeed(october, "America/Chicago", listOf(ScheduleEvent(id = "e1", title = "Old"))))
        store.remember(ScheduleFeed(november, "America/Chicago", listOf(ScheduleEvent(id = "e1", title = "New"))))
        val entry = assertNotNull(store.entry("e1"))
        assertEquals(november, entry.range)
        assertEquals("New", entry.event.title)
        assertNull(store.entry("missing"))
    }

    @Test
    fun evictsTheLeastRecentlyUsedEntriesPastTheBound() {
        val store = ScheduleEventStore()
        store.remember(ScheduleFeed(october, "America/Chicago", listOf(ScheduleEvent(id = "keep"))))
        store.remember(ScheduleFeed(october, "America/Chicago", (0 until 499).map { ScheduleEvent(id = "e$it") }))
        assertNotNull(store.entry("keep")) // touching it makes e0 the eldest
        store.remember(ScheduleFeed(october, "America/Chicago", listOf(ScheduleEvent(id = "overflow"))))
        assertNotNull(store.entry("keep"))
        assertNull(store.entry("e0"))
        assertNotNull(store.entry("overflow"))
    }
}
