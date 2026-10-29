package com.waivera.waivera.feature.chat.message

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import com.waivera.waivera.core.data.model.Message
import com.waivera.waivera.core.data.model.SendState
import com.waivera.waivera.core.demo.DemoData
import com.waivera.waivera.core.designsystem.theme.WaiveraMotionScheme
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.core.screenshottesting.ScreenshotSdk
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A send waits before it says "Sending…", so a quick ack goes straight to "Sent" in one swap
 * (M1-09); a slow one still says it is sending, and a failure or an offline queue says so at once.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ScreenshotSdk.VALUE], qualifiers = "w360dp-h640dp-notnight-xhdpi")
class MessageFooterMotionTest {
    @get:Rule
    val compose = createComposeRule()

    private fun message(state: SendState) = Message(
        id = -1,
        conversationId = DemoData.DM_PRIYA,
        clientMessageId = CLIENT_ID,
        authorId = DemoData.ME,
        text = "On my way",
        createdAt = DemoData.NOW,
        sendState = state,
    )

    private fun row(state: SendState) = TimelineItem.MessageRow(
        key = CLIENT_ID,
        message = message(state),
        isMine = true,
        isBot = false,
        groupedWithPrevious = false,
        groupedWithNext = false,
        rich = null,
        ticket = null,
        reactions = emptyList(),
        isLiveArrival = true,
    )

    private fun show(initial: SendState): (SendState) -> Unit {
        var setState: (SendState) -> Unit = {}
        compose.setContent {
            WaiveraTheme(darkTheme = false, motion = WaiveraMotionScheme.Standard, hapticsEnabled = false) {
                var state by remember { mutableStateOf(initial) }
                setState = { state = it }
                MessageRowView(
                    row = row(state),
                    authorName = "Me",
                    time = RowTime(DemoData.NOW, DemoData.ZONE),
                    timestampVisible = false,
                    highlighted = false,
                    callbacks = MessageRowCallbacks.None,
                )
            }
        }
        compose.waitForIdle()
        return { state ->
            compose.runOnUiThread {
                setState(state)
                Snapshot.sendApplyNotifications()
            }
        }
    }

    private fun count(text: String): Int =
        compose.onAllNodesWithText(text, substring = true, useUnmergedTree = true).fetchSemanticsNodes().size

    @Test
    fun aSlowSendSaysSendingOnlyAfterTheDelay() {
        compose.mainClock.autoAdvance = false
        show(SendState.SENDING)
        compose.mainClock.advanceTimeBy(WaiveraMotionScheme.SENDING_LABEL_DELAY_MILLIS - FRAME_MILLIS * 2)
        assertEquals("\"Sending…\" showed before the delay", 0, count(SENDING))
        compose.mainClock.advanceTimeBy(SETTLE_MILLIS)
        assertEquals("a slow send never said it was sending", 1, count(SENDING))
    }

    @Test
    fun aQuickAckGoesStraightToSentWithoutSayingSending() {
        compose.mainClock.autoAdvance = false
        val setState = show(SendState.SENDING)
        compose.mainClock.advanceTimeBy(QUICK_ACK_MILLIS)
        setState(SendState.SENT)
        repeat((SETTLE_MILLIS / FRAME_MILLIS).toInt()) {
            compose.mainClock.advanceTimeBy(FRAME_MILLIS)
            assertEquals("\"Sending…\" flashed on a quick ack", 0, count(SENDING))
        }
        assertEquals(1, count(SENT))
    }

    @Test
    fun aFailedSendSaysSoAtOnce() {
        compose.mainClock.autoAdvance = false
        val setState = show(SendState.SENDING)
        setState(SendState.FAILED)
        compose.mainClock.advanceTimeBy(FRAME_MILLIS * 2)
        compose.onAllNodesWithText(NOT_SENT, useUnmergedTree = true).assertCountEquals(1)
    }

    @Test
    fun theFooterPhaseHoldsOnlyAPlainSendQuiet() {
        assertEquals(FooterPhase.Quiet, footerPhase(message(SendState.SENDING), sendingLabelDue = false))
        assertEquals(FooterPhase.Sending, footerPhase(message(SendState.SENDING), sendingLabelDue = true))
        assertEquals(
            "an offline queue has no quick ack to wait for",
            FooterPhase.Sending,
            footerPhase(message(SendState.QUEUED), sendingLabelDue = false),
        )
        assertEquals(FooterPhase.Failed, footerPhase(message(SendState.FAILED), sendingLabelDue = false))
        assertEquals(FooterPhase.Settled, footerPhase(message(SendState.SENT), sendingLabelDue = false))
    }

    private companion object {
        const val CLIENT_ID = "c-1"
        const val FRAME_MILLIS = 16L
        const val SETTLE_MILLIS = 1_000L

        /** A good connection's ack, well inside the delay. */
        const val QUICK_ACK_MILLIS = 150L
        const val SENDING = "Sending…"
        const val SENT = "Sent"
        const val NOT_SENT = "Not sent"
    }
}
