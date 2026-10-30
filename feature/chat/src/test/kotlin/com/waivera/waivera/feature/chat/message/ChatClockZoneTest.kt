package com.waivera.waivera.feature.chat.message

import com.waivera.waivera.core.data.model.CurrentUser
import com.waivera.waivera.core.data.model.Message
import com.waivera.waivera.core.data.model.SessionState
import com.waivera.waivera.core.data.model.currentUser
import com.waivera.waivera.core.network.time.WaiveraTime
import com.waivera.waivera.core.testing.TestSessionRepository
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale
import java.util.TimeZone
import kotlin.test.assertEquals
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Conversation and thread times follow the employee's saved zone with the inbox's fallback, never
 * the device zone. The device zone is moved to Tokyo for every test so a leak would show.
 */
class ChatClockZoneTest {
    private val chicago = ZoneId.of("America/Chicago")
    private val tokyo = ZoneId.of("Asia/Tokyo")

    // Sunday 1 November 2026, 10:30 CST: the morning Chicago falls back from CDT to CST.
    private val now = Instant.parse("2026-11-01T16:30:00Z")
    private val fixed = Clock.fixed(now, ZoneOffset.UTC)
    private var deviceZone: TimeZone? = null

    @Before
    fun moveTheDeviceToTokyo() {
        deviceZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone(tokyo))
    }

    @After
    fun restoreTheDeviceZone() {
        TimeZone.setDefault(deviceZone)
    }

    private fun signedIn(zoneId: String?) = SessionState.SignedIn(CurrentUser(userId = "usr_me", timeZoneId = zoneId))

    private fun clockFor(state: SessionState) = ChatClock.employee(fixed, TestSessionRepository(state))

    private fun message(id: Long, at: String) =
        Message(id = id, conversationId = 1, authorId = "usr_a", text = "m$id", createdAt = Instant.parse(at))

    @Test
    fun theSavedZoneWinsOverTheDeviceZone() {
        assertEquals(tokyo, ZoneId.systemDefault(), "the device zone is Tokyo for this test")
        val clock = clockFor(signedIn("Europe/London"))
        assertEquals(ZoneId.of("Europe/London"), clock.zone)
        assertEquals("4:30 PM", ChatTime.clock(clock.now(), clock.zone, Locale.US), "not 1:30 AM Tokyo")
        assertEquals(LocalDate.of(2026, 11, 1), ChatTime.day(clock.now(), clock.zone), "not 2 November")
    }

    @Test
    fun theFallbackIsTheInboxsNeverTheDevice() {
        // The second check is the inbox's own expression (ChatListStateHolder.rowsFor).
        for (state in listOf(SessionState.Initializing, SessionState.SignedOut(), signedIn(null), signedIn("+05:30"))) {
            assertEquals(WaiveraTime.FallbackZone, clockFor(state).zone, "$state")
            assertEquals(state.currentUser?.zone ?: WaiveraTime.employeeZone(null), clockFor(state).zone, "$state")
        }
    }

    @Test
    fun aSessionChangeAppliesOnTheNextRead() {
        val session = TestSessionRepository(signedIn("America/Chicago"))
        val clock = ChatClock.employee(fixed, session)
        assertEquals(chicago, clock.zone)
        session.mutableState.value = signedIn("Europe/London")
        assertEquals(ZoneId.of("Europe/London"), clock.zone)
    }

    @Test
    fun fallBackNightSplitsDaysAtTheSavedZonesMidnight() {
        val messages = listOf(
            message(1, "2026-11-01T04:30:00Z"), // 31 Oct 23:30 CDT
            message(2, "2026-11-01T05:30:00Z"), // 1 Nov 00:30 CDT
            message(3, "2026-11-01T06:30:00Z"), // 1 Nov 01:30 CDT
            message(4, "2026-11-01T07:30:00Z"), // 1 Nov 01:30 CST, the repeated hour
        )
        val clock = clockFor(signedIn("America/Chicago"))
        val items = TimelineBuilder.build(messages, "usr_me", clock.zone, botConversation = false)
        val days = items.filterIsInstance<TimelineItem.DaySeparator>()
        assertEquals(listOf(LocalDate.of(2026, 10, 31), LocalDate.of(2026, 11, 1)), days.map { it.day })
        assertEquals("message-2", items[items.indexOf(days[1]) + 1].key, "the new day starts at 00:30 CDT")

        val today = ChatTime.day(clock.now(), clock.zone)
        assertEquals(
            listOf<DayLabel>(DayLabel.Yesterday, DayLabel.Today),
            days.map { DayLabel.of(it.day, today) },
        )
        assertEquals(
            listOf("11:30 PM", "12:30 AM", "1:30 AM", "1:30 AM"),
            messages.mapNotNull { it.createdAt }.map { ChatTime.clock(it, clock.zone, Locale.US) },
        )

        // The same night in a UTC employee's zone is one day, and in Tokyo (the device) it would be too.
        val utc = clockFor(signedIn("Etc/UTC"))
        assertEquals(
            listOf(LocalDate.of(2026, 11, 1)),
            TimelineBuilder.build(messages, "usr_me", utc.zone, botConversation = false)
                .filterIsInstance<TimelineItem.DaySeparator>()
                .map { it.day },
        )
    }
}
