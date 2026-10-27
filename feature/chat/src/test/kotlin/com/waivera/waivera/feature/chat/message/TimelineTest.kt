package com.waivera.waivera.feature.chat.message

import com.waivera.waivera.core.data.model.Message
import com.waivera.waivera.core.data.model.Reaction
import com.waivera.waivera.core.data.model.SendState
import com.waivera.waivera.core.network.dto.MessageType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.junit.Test

class TimelineTest {
    private val chicago = ZoneId.of("America/Chicago")
    private val base = Instant.parse("2026-10-20T15:00:00Z")

    private fun message(
        id: Long,
        author: String? = "usr_a",
        at: Instant = base,
        type: MessageType = MessageType.USER,
        clientId: String? = null,
    ) = Message(
        id = id,
        conversationId = 1,
        authorId = author,
        text = "m$id",
        createdAt = at,
        type = type,
        clientMessageId = clientId,
    )

    // region Grouping (03 §4)

    @Test
    fun sameAuthorWithinFiveMinutesGroups() {
        val first = message(1)
        assertTrue(MessageGrouping.canGroup(first, message(2, at = base.plusSeconds(299)), chicago))
    }

    @Test
    fun justOverFiveMinutesDoesNotGroup() {
        assertFalse(MessageGrouping.canGroup(message(1), message(2, at = base.plusSeconds(301)), chicago))
    }

    @Test
    fun differentAuthorsDoNotGroup() {
        assertFalse(
            MessageGrouping.canGroup(message(1), message(2, author = "usr_b", at = base.plusSeconds(10)), chicago),
        )
    }

    @Test
    fun localMidnightSplitsGroups() {
        // 23:58 and 00:01 Chicago time, three minutes apart.
        val before = message(1, at = Instant.parse("2026-10-21T04:58:00Z"))
        val after = message(2, at = Instant.parse("2026-10-21T05:01:00Z"))
        assertFalse(MessageGrouping.canGroup(before, after, chicago))
        assertTrue(MessageGrouping.canGroup(before, after, ZoneId.of("UTC")))
    }

    @Test
    fun aSystemMessageBetweenBreaksTheGroup() {
        val items = TimelineBuilder.build(
            listOf(
                message(1),
                message(2, author = null, at = base.plusSeconds(30), type = MessageType.SYSTEM),
                message(3, at = base.plusSeconds(60)),
            ),
            myUserId = "usr_me",
            zone = chicago,
            botConversation = false,
        )
        val rows = items.filterIsInstance<TimelineItem.MessageRow>()
        assertEquals(2, rows.size)
        assertFalse(rows[0].groupedWithNext)
        assertFalse(rows[1].groupedWithPrevious)
        assertEquals(1, items.filterIsInstance<TimelineItem.SystemRow>().size)
    }

    @Test
    fun aThreadReplyNeverGroupsWithItsRoot() {
        val root = message(1)
        val reply = message(2, at = base.plusSeconds(20)).copy(parentMessageId = 1, threadRootMessageId = 1)
        val sibling = message(3, at = base.plusSeconds(40)).copy(parentMessageId = 1, threadRootMessageId = 1)
        assertFalse(MessageGrouping.canGroup(root, reply, chicago))
        assertTrue(MessageGrouping.canGroup(reply, sibling, chicago))
    }

    @Test
    fun deletedMessagesDoNotGroup() {
        assertFalse(
            MessageGrouping.canGroup(message(1).copy(isDeleted = true), message(2, at = base.plusSeconds(5)), chicago),
        )
    }

    @Test
    fun theRepeatedFallBackHourStaysOneLocalDayAndGroups() {
        // 01:58 CDT and 01:02 CST on 2026-11-01 are four minutes apart on the same local day.
        val beforeChange = message(1, at = Instant.parse("2026-11-01T06:58:00Z"))
        val afterChange = message(2, at = Instant.parse("2026-11-01T07:02:00Z"))
        assertTrue(MessageGrouping.canGroup(beforeChange, afterChange, chicago))
    }

    @Test
    fun threeRepliesBySameAuthorShareOneAvatarAndName() {
        val rows = TimelineBuilder.build(
            listOf(message(1), message(2, at = base.plusSeconds(60)), message(3, at = base.plusSeconds(120))),
            myUserId = "usr_me",
            zone = chicago,
            botConversation = false,
        ).filterIsInstance<TimelineItem.MessageRow>()
        assertEquals(listOf(false, true, true), rows.map { it.groupedWithPrevious })
        assertEquals(listOf(true, true, false), rows.map { it.groupedWithNext })
    }

    // endregion

    // region Order and keys

    @Test
    fun ordersByTimeThenIdWithPendingLast() {
        val pending = message(-1, at = base.minusSeconds(60)).copy(sendState = SendState.QUEUED)
        val tieLow = message(5)
        val tieHigh = message(9)
        val sorted = listOf(pending, tieHigh, tieLow).sortedWith(MessageOrder)
        assertEquals(listOf(5L, 9L, -1L), sorted.map { it.id })
    }

    @Test
    fun alignmentFollowsTheBackendUserId() {
        val rows = TimelineBuilder.build(
            listOf(message(1, author = "usr_me"), message(2, author = "usr_b", at = base.plusSeconds(10))),
            myUserId = "usr_me",
            zone = chicago,
            botConversation = false,
        ).filterIsInstance<TimelineItem.MessageRow>()
        assertEquals(listOf(true, false), rows.map { it.isMine })
    }

    @Test
    fun anOptimisticRowKeepsItsKeyWhenTheServerCopyLands() {
        val pending = message(-7, at = base, clientId = "c-1").copy(sendState = SendState.SENDING)
        val sent = pending.copy(id = 77, sendState = SendState.SENT)
        val before = TimelineBuilder.build(listOf(pending), "usr_a", chicago, false).last().key
        val after = TimelineBuilder.build(listOf(sent), "usr_a", chicago, false).last().key
        assertEquals(before, after)
    }

    @Test
    fun anOverlappingOutboxRowIsDroppedForItsServerCopy() {
        val pending = message(-7, at = base, clientId = "c-1").copy(sendState = SendState.SENDING)
        val sent = pending.copy(id = 77, sendState = SendState.SENT)
        val rows = TimelineBuilder.build(
            listOf(pending, sent),
            "usr_a",
            chicago,
            false,
        ).filterIsInstance<TimelineItem.MessageRow>()
        assertEquals(listOf(77L), rows.map { it.message.id })
    }

    @Test
    fun serverMessagesSharingAClientIdAllRender() {
        val rows = TimelineBuilder.build(
            listOf(message(1, clientId = "dup"), message(2, at = base.plusSeconds(5), clientId = "dup")),
            "usr_a",
            chicago,
            false,
        ).filterIsInstance<TimelineItem.MessageRow>()
        assertEquals(listOf("message-1", "message-2"), rows.map { it.key })
    }

    // endregion

    // region Day separators and call logs

    @Test
    fun aDaySeparatorPrecedesEachLocalDay() {
        val items = TimelineBuilder.build(
            listOf(
                message(1, at = Instant.parse("2026-10-20T15:00:00Z")),
                message(2, at = Instant.parse("2026-10-21T15:00:00Z")),
            ),
            "usr_a",
            chicago,
            false,
        )
        assertEquals(
            listOf(LocalDate.of(2026, 10, 20), LocalDate.of(2026, 10, 21)),
            items.filterIsInstance<TimelineItem.DaySeparator>().map { it.day },
        )
        assertIs<TimelineItem.DaySeparator>(items.first())
    }

    @Test
    fun anHourPauseInsideADayGetsATimeLine() {
        val items = TimelineBuilder.build(
            listOf(
                message(1),
                message(2, at = base.plusSeconds(59 * 60)),
                message(3, at = base.plusSeconds(2 * 3600)),
            ),
            "usr_a",
            chicago,
            false,
        )
        val times = items.filterIsInstance<TimelineItem.TimeSeparator>()
        assertEquals(listOf(base.plusSeconds(2 * 3600)), times.map { it.at }, "59 minutes is not a pause")
        assertEquals("message-3", items[items.indexOf(times.single()) + 1].key)
    }

    private fun rows(items: List<TimelineItem>) = items.filterIsInstance<TimelineItem.MessageRow>()

    @Test
    fun aRunUnderAClockLineInTheSameMinuteDropsItsFooterTime() {
        val items = TimelineBuilder.build(
            listOf(message(1), message(2, at = base.plusSeconds(3600)), message(3, at = base.plusSeconds(3630))),
            "usr_a",
            chicago,
            false,
        )
        val rows = rows(items)
        assertFalse(rows[0].timeShownAbove, "no clock line above the first run")
        assertFalse(rows[1].timeShownAbove, "only the last bubble of a run carries a footer")
        assertTrue(rows[2].timeShownAbove, "12:00 PM line, run ending at 12:00 PM")
    }

    @Test
    fun aRunThatEndsInALaterMinuteKeepsItsFooterTime() {
        val items = TimelineBuilder.build(
            listOf(
                message(1),
                message(2, at = base.plusSeconds(3600)),
                message(3, at = base.plusSeconds(3600 + 180)),
            ),
            "usr_a",
            chicago,
            false,
        )
        assertFalse(rows(items).last().timeShownAbove, "12:00 PM line, run ending at 12:03 PM")
    }

    @Test
    fun aNewRunAfterTheClockLineRunDoesNotInheritIt() {
        val items = TimelineBuilder.build(
            listOf(
                message(1),
                message(2, at = base.plusSeconds(3600)),
                message(3, author = "usr_b", at = base.plusSeconds(3610)),
            ),
            "usr_a",
            chicago,
            false,
        )
        val rows = rows(items)
        assertTrue(rows[1].timeShownAbove)
        assertFalse(rows[2].timeShownAbove, "another author's run has no clock line of its own")
    }

    @Test
    fun theClockLineMinuteIsReadInTheZoneAcrossTheFallBack() {
        // 01:30 CDT, then 01:30 CST an hour later: same wall clock, and the line and footer agree.
        val first = message(1, at = Instant.parse("2026-11-01T06:30:00Z"))
        val second = message(2, at = Instant.parse("2026-11-01T07:30:20Z"))
        val items = TimelineBuilder.build(listOf(first, second), "usr_a", chicago, false)
        assertTrue(rows(items).last().timeShownAbove)
        assertFalse(rows(items).first().timeShownAbove)
    }

    @Test
    fun theFallBackHourIsAPauseEvenThoughTheWallClockRepeats() {
        // 1 November 2026 in Chicago: 01:30 CDT (06:30Z), then 01:30 CST (07:30Z) an hour later.
        val first = message(1, at = Instant.parse("2026-11-01T06:30:00Z"))
        val second = message(2, at = Instant.parse("2026-11-01T07:30:00Z"))
        val items = TimelineBuilder.build(listOf(first, second), "usr_a", chicago, false)
        assertEquals(1, items.count { it is TimelineItem.DaySeparator }, "one local day")
        val pause = items.filterIsInstance<TimelineItem.TimeSeparator>().single()
        assertEquals(Instant.parse("2026-11-01T07:30:00Z"), pause.at)
    }

    @Test
    fun daySeparatorsCanBeTurnedOff() {
        val items = TimelineBuilder.build(listOf(message(1)), "usr_a", chicago, false, daySeparators = false)
        assertTrue(items.none { it is TimelineItem.DaySeparator })
    }

    @Test
    fun callMessagesCollapseIntoOneCardAtTheNewest() {
        val started = message(
            10,
            author = null,
            type = MessageType.SYSTEM,
        ).copy(text = "Video call started", callRecordId = 5)
        val between = message(11, at = base.plusSeconds(60))
        val ended = message(12, author = null, at = base.plusSeconds(120), type = MessageType.SYSTEM)
            .copy(text = "Video call ended in 2m", callRecordId = 5)
        val items = TimelineBuilder.build(
            listOf(started, between, ended),
            "usr_a",
            chicago,
            false,
            daySeparators = false,
        )
        assertEquals(listOf("message-11", "call-5"), items.map { it.key })
        val card = items.last() as TimelineItem.CallLog
        assertEquals(listOf(10L, 12L), card.messages.map { it.id })
        assertEquals("2m", card.summary.duration)
    }

    // endregion

    @Test
    fun reactionsSummariseByEmojiInFirstSeenOrder() {
        val summary = Reactions.summarize(
            listOf(Reaction("usr_a", "👍"), Reaction("usr_me", "❤️"), Reaction("usr_b", " 👍 "), Reaction("usr_c", "")),
            myUserId = "usr_me",
        )
        assertEquals(listOf("👍" to 2, "❤️" to 1), summary.map { it.emoji to it.count })
        assertEquals(listOf(false, true), summary.map { it.mine })
        assertEquals(listOf("usr_a", "usr_b"), summary.first().userIds)
    }

    @Test
    fun quickReactionsAreTheFixedSix() {
        assertEquals(listOf("👍", "❤️", "😂", "😮", "😢", "😠"), Reactions.QUICK)
    }
}
