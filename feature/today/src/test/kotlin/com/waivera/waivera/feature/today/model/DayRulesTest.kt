package com.waivera.waivera.feature.today.model

import com.waivera.waivera.core.data.model.Conversation
import com.waivera.waivera.core.data.model.DateRange
import com.waivera.waivera.core.data.model.MyInvitation
import com.waivera.waivera.core.data.model.ScheduleEvent
import com.waivera.waivera.core.data.model.ScheduleFeed
import com.waivera.waivera.core.data.model.Synced
import com.waivera.waivera.feature.today.TEST_NOW
import com.waivera.waivera.feature.today.TEST_ZONE
import com.waivera.waivera.feature.today.allDayEvent
import com.waivera.waivera.feature.today.conversation
import com.waivera.waivera.feature.today.timedEvent
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test

/**
 * The "Your day" track and the hero's stats, in the employee's zone (America/Chicago). The test
 * day, November 1, 2026, is the 25-hour day DST ends: 01:00 to 02:00 happens twice.
 */
class DayRulesTest {
    private val today = LocalDate.of(2026, 11, 1)
    private val range = DateRange(start = LocalDate.of(2026, 11, 1), endExclusive = LocalDate.of(2026, 11, 15))

    private fun schedule(vararg events: ScheduleEvent, synced: Instant? = TEST_NOW) =
        Synced(ScheduleFeed(range = range, zoneId = TEST_ZONE.id, events = events.toList()), lastSyncedAt = synced)

    private fun timeline(vararg events: ScheduleEvent, now: Instant = TEST_NOW) =
        checkNotNull(DayRules.timeline(schedule(*events), TEST_ZONE, today, now))

    @Test
    fun `the track runs 8 AM to 6 PM and places an event by its share of the window`() {
        // 11:00 to 12:00 CST.
        val day = timeline(timedEvent("visit", "2026-11-01T17:00:00Z", "2026-11-01T18:00:00Z"))
        assertEquals(LocalTime.of(8, 0), day.windowStart)
        assertEquals(LocalTime.of(18, 0), day.windowEnd)
        val block = day.blocks.single()
        assertEquals(0.3f, block.start, TOLERANCE)
        assertEquals(0.4f, block.end, TOLERANCE)
        assertEquals(9, day.ticks.size, "one tick for each hour inside the window")
        assertEquals(listOf(8, 10, 12, 14, 16, 18), day.labels.map { it.time.hour })
        assertEquals(Duration.ofHours(1), day.scheduled)
    }

    @Test
    fun `now sits on the track while it is inside the window`() {
        // TEST_NOW is 10:30 CST: a quarter of the way through 8 AM to 6 PM.
        assertEquals(0.25f, checkNotNull(timeline().now), TOLERANCE)
        assertNull(timeline(now = Instant.parse("2026-11-01T12:00:00Z")).now, "6 AM is before the window")
    }

    /**
     * An event from 01:30 CDT to 01:30 CST (the repeated hour) is one real hour. The window then
     * starts at the first 1:00 and runs 18 real hours to 6 PM, so 2 AM sits two hours in, not one.
     */
    @Test
    fun `time - on the fall-back day the track measures real hours, not clock hours`() {
        val day = timeline(timedEvent("night", "2026-11-01T06:30:00Z", "2026-11-01T07:30:00Z"))
        assertEquals(LocalTime.of(1, 0), day.windowStart)
        val block = day.blocks.single()
        assertEquals(0.5f / 18f, block.start, TOLERANCE)
        assertEquals(1.5f / 18f, block.end, TOLERANCE)
        assertEquals(2f / 18f, day.ticks.first(), TOLERANCE, "the 2 AM tick comes after the repeated hour")
        assertEquals(Duration.ofHours(1), day.scheduled)
    }

    @Test
    fun `time - the spring-forward day has a 23-hour track day`() {
        val spring = LocalDate.of(2026, 3, 8)
        val feed = Synced(
            ScheduleFeed(
                range = DateRange(start = spring, endExclusive = spring.plusDays(1)),
                zoneId = TEST_ZONE.id,
                // 01:00 CST to 03:30 CDT: 1 hour 30 minutes of real time.
                events = listOf(timedEvent("early", "2026-03-08T07:00:00Z", "2026-03-08T08:30:00Z")),
            ),
            lastSyncedAt = TEST_NOW,
        )
        val day = checkNotNull(DayRules.timeline(feed, TEST_ZONE, spring, Instant.parse("2026-03-08T15:00:00Z")))
        assertEquals(LocalTime.of(1, 0), day.windowStart)
        assertEquals(Duration.ofMinutes(90), day.scheduled)
        // 01:00 CST to 18:00 CDT is 16 real hours: the block ends 1.5 hours in.
        assertEquals(1.5f / 16f, day.blocks.single().end, TOLERANCE)
    }

    @Test
    fun `overlapping events count their shared time once and take turns on the colours`() {
        val day = timeline(
            timedEvent("a", "2026-11-01T15:00:00Z", "2026-11-01T16:00:00Z"),
            timedEvent("b", "2026-11-01T15:30:00Z", "2026-11-01T17:00:00Z"),
            timedEvent("c", "2026-11-01T20:00:00Z", "2026-11-01T20:30:00Z"),
            timedEvent("d", "2026-11-01T21:00:00Z", "2026-11-01T21:30:00Z"),
        )
        // 9:00 to 11:00 (a and b together), then two half hours.
        assertEquals(Duration.ofMinutes(180), day.scheduled)
        assertEquals(listOf(0, 1, 2, 0), day.blocks.map { it.tone })
        assertEquals(4, day.eventCount)
    }

    @Test
    fun `an overnight shift that began yesterday starts the track at midnight`() {
        // 22:00 CDT on October 31 to 07:00 CST on November 1.
        val day = timeline(timedEvent("night", "2026-11-01T03:00:00Z", "2026-11-01T13:00:00Z"))
        assertEquals(LocalTime.MIDNIGHT, day.windowStart)
        assertEquals(0f, day.blocks.single().start)
    }

    @Test
    fun `all-day items, unanswered invitations and declined events stay off the track`() {
        val day = timeline(
            allDayEvent("holiday", today),
            timedEvent("pending", "2026-11-01T17:00:00Z", "2026-11-01T18:00:00Z", MyInvitation.PENDING),
            timedEvent("declined", "2026-11-01T19:00:00Z", "2026-11-01T20:00:00Z", MyInvitation.DECLINED),
            timedEvent("tomorrow", "2026-11-02T17:00:00Z", "2026-11-02T18:00:00Z"),
        )
        assertEquals(0, day.eventCount)
        assertEquals(Duration.ZERO, day.scheduled)
    }

    @Test
    fun `no track until the schedule has loaded`() {
        assertNull(DayRules.timeline(null, TEST_ZONE, today, TEST_NOW))
        assertNull(DayRules.timeline(schedule(synced = null), TEST_ZONE, today, TEST_NOW))
    }

    @Test
    fun `stats count unread chats and the events today and this Sunday-to-Saturday week`() {
        val events = schedule(
            timedEvent("today", "2026-11-01T17:00:00Z", "2026-11-01T18:00:00Z"),
            allDayEvent("trip", LocalDate.of(2026, 10, 30), LocalDate.of(2026, 11, 2)),
            timedEvent("saturday", "2026-11-07T23:00:00Z", "2026-11-08T00:00:00Z"),
            // 00:30 CST on Sunday the 8th: next week, though it is still the 7th in UTC.
            timedEvent("next-sunday", "2026-11-08T06:30:00Z", "2026-11-08T07:30:00Z"),
            timedEvent("pending", "2026-11-03T17:00:00Z", "2026-11-03T18:00:00Z", MyInvitation.PENDING),
        )
        val chats = Synced(
            listOf(conversation(1, unread = 3), conversation(2), conversation(3, unread = 1)),
            lastSyncedAt = TEST_NOW,
        )
        assertEquals(
            TodayStats(eventsToday = 2, unreadChats = 2, eventsThisWeek = 3),
            DayRules.stats(chats, events, TEST_ZONE, today),
        )
    }

    @Test
    fun `a stat whose source has not loaded stays empty rather than zero`() {
        val neverLoaded = Synced(emptyList<Conversation>(), lastSyncedAt = null)
        assertEquals(TodayStats(), DayRules.stats(neverLoaded, null, TEST_ZONE, today))
        val cached = Synced(listOf(conversation(1, unread = 3)), lastSyncedAt = null)
        assertEquals(1, DayRules.stats(cached, null, TEST_ZONE, today).unreadChats, "a cached list still counts")
    }

    private companion object {
        const val TOLERANCE = 0.0005f
    }
}
