package com.waivera.waivera.feature.chat.message

import com.waivera.waivera.core.data.model.Message
import com.waivera.waivera.core.network.dto.MessageType
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

class SystemMessagesTest {
    private val base = Instant.parse("2026-10-20T15:00:00Z")

    private fun system(id: Long, text: String, at: Instant = base, call: Long? = 3) = Message(
        id = id,
        conversationId = 1,
        type = MessageType.SYSTEM,
        text = text,
        createdAt = at,
        callRecordId = call,
    )

    @Test
    fun timestampTokenIsRemovedAndReadAsUtc() {
        val parsed = SystemMessages.parse("Priya joined the channel - {[TIMESTAMP- 10/20/2026 14:05:09]}")
        assertEquals("Priya joined the channel", parsed.text)
        assertEquals(Instant.parse("2026-10-20T14:05:09Z"), parsed.embeddedTimestamp)
    }

    @Test
    fun invalidTokenDatesAreIgnored() {
        assertNull(SystemMessages.parse("x {[TIMESTAMP- 13/45/2026 25:00:00]}").embeddedTimestamp)
    }

    @Test
    fun textWithoutATokenIsCollapsedAndTrimmed() {
        assertEquals(ParsedSystemMessage("a b", null), SystemMessages.parse("  a \n b  "))
        assertEquals(ParsedSystemMessage("", null), SystemMessages.parse(null))
    }

    @Test
    fun systemAndChannelEventsAreSystemMessages() {
        assertTrue(SystemMessages.isSystem(system(1, "x")))
        assertTrue(SystemMessages.isSystem(system(1, "x").copy(type = MessageType.CHANNEL_EVENT)))
        assertFalse(SystemMessages.isSystem(system(1, "x").copy(type = MessageType.USER)))
    }

    @Test
    fun onlyPositiveCallIdsOnSystemMessagesCount() {
        assertEquals(3L, SystemMessages.callRecordId(system(1, "x")))
        assertNull(SystemMessages.callRecordId(system(1, "x", call = 0)))
        assertNull(SystemMessages.callRecordId(system(1, "x").copy(type = MessageType.USER)))
    }

    @Test
    fun callOutcomes() {
        assertEquals(CallOutcome.Declined, CallLogs.summarize(listOf(system(1, "Call declined"))).outcome)
        assertEquals(CallOutcome.Declined, CallLogs.summarize(listOf(system(1, "Call rejected"))).outcome)
        assertEquals(CallOutcome.Missed, CallLogs.summarize(listOf(system(1, "Missed video call"))).outcome)
        assertEquals(CallOutcome.Ended, CallLogs.summarize(listOf(system(1, "Video call ended"))).outcome)
        assertEquals(CallOutcome.InProgress, CallLogs.summarize(listOf(system(1, "Video call started"))).outcome)
        assertTrue(CallLogs.summarize(listOf(system(1, "Missed video call"))).isMissed)
    }

    @Test
    fun durationIsTakenVerbatimFromTheEndedText() {
        val messages =
            listOf(
                system(1, "Video call started"),
                system(2, "Video call ended in 12m 4s - {[TIMESTAMP- 10/20/2026 15:12:04]}"),
            )
        assertEquals("12m 4s", CallLogs.duration(messages))
        assertEquals(Instant.parse("2026-10-20T15:12:04Z"), CallLogs.summarize(messages).time)
    }

    @Test
    fun durationFallsBackToTheSpanBetweenMessages() {
        fun span(seconds: Long) =
            CallLogs.duration(listOf(system(1, "started"), system(2, "joined", at = base.plusSeconds(seconds))))
        assertEquals("45s", span(45))
        assertEquals("12m 4s", span(724))
        assertEquals("3 min", span(180))
        assertNull(CallLogs.duration(listOf(system(1, "started"))))
    }

    @Test
    fun callTimeFallsBackToTheNewestCreatedAt() {
        val messages = listOf(system(1, "started"), system(2, "ended", at = base.plusSeconds(60)))
        assertEquals(base.plusSeconds(60), CallLogs.summarize(messages).time)
    }
}
