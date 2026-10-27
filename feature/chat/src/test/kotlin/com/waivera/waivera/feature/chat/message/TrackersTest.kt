package com.waivera.waivera.feature.chat.message

import com.waivera.waivera.core.data.model.Message
import com.waivera.waivera.core.data.model.SendState
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class TrackersTest {
    private val base = Instant.parse("2026-10-20T15:00:00Z")

    private fun message(
        id: Long,
        clientId: String? = null,
        state: SendState = SendState.SENT,
        at: Instant = base.plusSeconds(id),
    ) = Message(id = id, conversationId = 1, clientMessageId = clientId, sendState = state, createdAt = at, text = "m")

    @Test
    fun failuresAlreadyThereOnOpenDoNotRaiseTheBanner() {
        val tracker = SendTracker()
        assertEquals(0, tracker.update(listOf(message(-1, "c1", SendState.FAILED))).newFailures)
    }

    @Test
    fun aSendThatFailsWhileOpenIsReportedOnce() {
        val tracker = SendTracker()
        tracker.update(emptyList())
        tracker.update(listOf(message(-1, "c1", SendState.SENDING)))
        assertEquals(1, tracker.update(listOf(message(-1, "c1", SendState.FAILED))).newFailures)
        assertEquals(0, tracker.update(listOf(message(-1, "c1", SendState.FAILED))).newFailures)
    }

    @Test
    fun aRetriedMessageThatLandsReportsSuccessOnce() {
        val tracker = SendTracker()
        tracker.update(listOf(message(-1, "c1", SendState.FAILED)))
        tracker.markRetried("c1")
        assertFalse(tracker.update(listOf(message(-1, "c1", SendState.QUEUED))).retrySucceeded)
        assertTrue(tracker.update(listOf(message(40, "c1", SendState.SENT))).retrySucceeded)
        assertFalse(tracker.update(listOf(message(40, "c1", SendState.SENT))).retrySucceeded)
    }

    @Test
    fun aRetryThatFailsAgainIsReportedAsAFailureNotASuccess() {
        val tracker = SendTracker()
        tracker.update(listOf(message(-1, "c1", SendState.FAILED)))
        tracker.markRetried("c1")
        tracker.update(listOf(message(-1, "c1", SendState.SENDING)))
        val events = tracker.update(listOf(message(-1, "c1", SendState.FAILED)))
        assertEquals(1, events.newFailures)
        assertFalse(events.retrySucceeded)
    }

    @Test
    fun theFirstLoadAndOlderPagesDoNotAnimate() {
        val tracker = ArrivalTracker()
        assertEquals(emptySet(), tracker.update(listOf(message(10), message(11))))
        assertEquals(
            emptySet(),
            tracker.update(listOf(message(1, at = base.minusSeconds(600)), message(10), message(11))),
        )
    }

    @Test
    fun aNewerMessageAnimatesAndItsServerCopyDoesNotAnimateAgain() {
        val tracker = ArrivalTracker()
        tracker.update(listOf(message(10)))
        assertEquals(
            setOf(-5L),
            tracker.update(listOf(message(10), message(-5, "c5", SendState.SENDING, at = base.plusSeconds(60)))),
        )
        assertEquals(emptySet(), tracker.update(listOf(message(10), message(50, "c5", at = base.plusSeconds(60)))))
        assertEquals(
            setOf(51L),
            tracker.update(
                listOf(
                    message(10),
                    message(50, "c5", at = base.plusSeconds(60)),
                    message(51, at = base.plusSeconds(120)),
                ),
            ),
        )
    }

    @Test
    fun anOwnSendIntoAnEmptyConversationAnimates() {
        val tracker = ArrivalTracker()
        tracker.update(emptyList())
        assertEquals(setOf(-1L), tracker.update(listOf(message(-1, "c1", SendState.SENDING))))
    }
}
