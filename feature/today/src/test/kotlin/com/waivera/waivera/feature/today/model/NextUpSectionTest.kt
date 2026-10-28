package com.waivera.waivera.feature.today.model

import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NextUpSectionTest {
    private fun item(id: String, start: LocalTime?, time: UpcomingLabel) = NextUpItem(
        eventId = id,
        title = id,
        time = time,
        start = start,
        end = null,
        endsOnLaterDay = false,
        location = null,
        isOnline = false,
    )

    private val dstEnds = item("dst", start = null, time = UpcomingLabel.AllDayToday)
    private val visit = item("visit", start = LocalTime.of(11, 0), time = UpcomingLabel.InMinutes(30))
    private val review = item("review", start = LocalTime.of(9, 0), time = UpcomingLabel.TomorrowAt(LocalTime.of(9, 0)))

    @Test
    fun theCardFeaturesTheFirstTimedItemOverAnAllDayOne() {
        val section = NextUpSection(items = listOf(dstEnds, visit, review), isReady = true)
        assertEquals(visit, section.featured)
        assertEquals(listOf(dstEnds, review), section.others)
    }

    @Test
    fun onlyAllDayItemsFeatureTheFirst() {
        val section = NextUpSection(items = listOf(dstEnds), isReady = true)
        assertEquals(dstEnds, section.featured)
        assertEquals(emptyList(), section.others)
        assertNull(NextUpSection().featured)
    }
}
