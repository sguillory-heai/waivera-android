package com.waivera.waivera.feature.chat

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.waivera.waivera.core.data.model.Message
import com.waivera.waivera.core.data.model.SendState
import com.waivera.waivera.core.demo.DemoData
import com.waivera.waivera.core.designsystem.theme.WaiveraMotionScheme
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.core.screenshottesting.ScreenshotSdk
import com.waivera.waivera.feature.chat.conversation.ConversationContent
import com.waivera.waivera.feature.chat.conversation.ConversationUiState
import com.waivera.waivera.feature.chat.message.ChatSheetCallbacks
import com.waivera.waivera.feature.chat.message.MessageRowCallbacks
import com.waivera.waivera.feature.chat.message.RowTime
import com.waivera.waivera.feature.chat.message.TimelineBuilder
import com.waivera.waivera.feature.chat.message.TimelineItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The conversation list while a send lands and while a time line opens (M2-01, M2-05): the
 * history is pushed up as the new rows grow instead of jumping by their height in one frame, and
 * a time line that opens never draws over the newer bubble below it, because the placement
 * animation is held off while the list follows the growth.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ScreenshotSdk.VALUE], qualifiers = "w360dp-h640dp-notnight-xhdpi")
class ConversationListMotionTest {
    @get:Rule
    val compose = createComposeRule()

    private fun message(id: Long, text: String, minutesAgo: Long, author: String = DemoData.PRIYA) = Message(
        id = id,
        conversationId = DemoData.DM_PRIYA,
        authorId = author,
        text = text,
        createdAt = DemoData.NOW.minusSeconds(minutesAgo * MINUTE_SECONDS),
    )

    private fun state(
        messages: List<Message>,
        liveArrivals: Set<Long> = emptySet(),
        visible: Set<String> = emptySet(),
    ) = ConversationUiState(
        title = "Priya Raman",
        isDirect = true,
        content = ConversationContent.Messages,
        items = TimelineBuilder.build(
            messages = messages,
            myUserId = DemoData.ME,
            zone = DemoData.ZONE,
            botConversation = false,
            liveArrivals = liveArrivals,
        ),
        visibleTimestamps = visible,
        canWrite = true,
        myUserId = DemoData.ME,
    )

    /** What the screen shows; a write followed by the apply notification reaches the paused recomposer. */
    private val shown = mutableStateOf(ConversationUiState())

    private fun setState(next: ConversationUiState) {
        shown.value = next
        Snapshot.sendApplyNotifications()
    }

    /** Shows [initial]; the returned setter changes the state the way a ViewModel emission would. */
    private fun show(
        initial: ConversationUiState,
        onToggleTimestamp: (String) -> Unit = {},
    ): (ConversationUiState) -> Unit {
        shown.value = initial
        val rows = MessageRowCallbacks.None.withToggleTimestamp(onToggleTimestamp)
        val actions = ConversationActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {}, rows, ChatSheetCallbacks.None)
        compose.setContent {
            WaiveraTheme(darkTheme = false, motion = WaiveraMotionScheme.Standard, hapticsEnabled = false) {
                val state by shown
                ConversationScreenContent(
                    state = state,
                    draft = "",
                    onDraftChange = {},
                    time = RowTime(DemoData.NOW, DemoData.ZONE),
                    actions = actions,
                    listState = rememberLazyListState(),
                    skeletonDelayMillis = 0,
                )
            }
        }
        compose.mainClock.advanceTimeBy(SETTLE_MILLIS)
        return { next -> compose.runOnUiThread { setState(next) } }
    }

    private fun topOf(text: String): Float =
        compose.onNodeWithText(text, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.top

    @Test
    fun aSendPushesTheConversationUpInsteadOfCuttingIt() {
        compose.mainClock.autoAdvance = false
        // Yesterday's messages, so the send also inserts a "Today" line above itself.
        val history = listOf(
            message(id = 1, text = "Earlier message", minutesAgo = DAY_MINUTES + 2),
            message(id = 2, text = OLDER, minutesAgo = DAY_MINUTES),
        )
        val update = show(state(history))
        val before = topOf(OLDER)
        val send = Message(
            id = SEND_ID,
            conversationId = DemoData.DM_PRIYA,
            clientMessageId = "client-landing",
            authorId = DemoData.ME,
            text = "On my way over now",
            createdAt = DemoData.NOW,
            sendState = SendState.SENDING,
        )
        val sent = state(history + send, liveArrivals = setOf(SEND_ID))
        val today = DemoData.NOW.atZone(DemoData.ZONE).toLocalDate()
        assertTrue(
            "the send did not insert a day line, so this does not cover its growth",
            sent.items.any { it is TimelineItem.DaySeparator && it.day == today },
        )
        update(sent)
        compose.mainClock.advanceTimeBy(FRAME_MILLIS)
        val firstFrame = before - topOf(OLDER)
        compose.mainClock.advanceTimeBy(LANDED_MILLIS - FRAME_MILLIS)
        val landed = before - topOf(OLDER)
        compose.mainClock.advanceTimeBy(SETTLE_MILLIS)
        val shift = before - topOf(OLDER)
        assertTrue("the send did not move the conversation: $shift px", shift > MIN_SHIFT_PX)
        assertTrue(
            "the conversation jumped $firstFrame of $shift px in the send's first frame",
            firstFrame < shift * MAX_FIRST_FRAME_SHARE,
        )
        assertEquals("the conversation had not landed after $LANDED_MILLIS ms", shift, landed, LANDED_TOLERANCE_PX)
    }

    @Test
    fun aSendLandsInViewWhenTheConversationFillsTheScreen() {
        compose.mainClock.autoAdvance = false
        // More history than the screen holds, so the list is scrolled to its newest row, as in
        // any real conversation: a row that starts at no height must not fall out of view.
        val history = (1L..FULL_HISTORY_ROWS).map { id ->
            message(id = id, text = "Yesterday $id", minutesAgo = DAY_MINUTES + FULL_HISTORY_ROWS - id)
        }
        val update = show(state(history))
        val send = message(id = SEND_ID, text = SENT_TEXT, minutesAgo = 0, author = DemoData.ME)
            .copy(clientMessageId = "client-landing", sendState = SendState.SENDING)
        update(state(history + send, liveArrivals = setOf(SEND_ID)))
        compose.mainClock.advanceTimeBy(SETTLE_MILLIS)
        // Below the fold, behind "Jump to latest", the send would not be displayed.
        compose.onNodeWithText(SENT_TEXT, useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun anOpeningTimeLineNeverDrawsOverTheNewerBubble() {
        compose.mainClock.autoAdvance = false
        // One run from Priya: the middle bubble shows its time on tap, inside the run.
        val messages = listOf(
            message(id = 1, text = "First in the run", minutesAgo = 3),
            message(id = 2, text = TAPPED, minutesAgo = 2),
            message(id = 3, text = NEWER, minutesAgo = 1),
        )
        var visible = emptySet<String>()
        // The tap arrives on the UI thread, as a ViewModel's state would.
        show(state(messages)) { key ->
            visible = visible + key
            setState(state(messages, visible = visible))
        }
        assertFalse("the time line showed before the tap", lineShown())
        compose.onNodeWithText(TAPPED, useUnmergedTree = true).performClick()
        var sawLine = false
        repeat(TOGGLE_FRAMES) { frame ->
            compose.mainClock.advanceTimeBy(FRAME_MILLIS)
            val line = compose.onAllNodesWithText(TIME_LINE, useUnmergedTree = true).fetchSemanticsNodes()
            if (line.isEmpty()) return@repeat
            sawLine = true
            val lineBottom = line.single().boundsInRoot.bottom
            val newerTop = topOf(NEWER)
            assertTrue(
                "frame $frame: the time line's bottom ($lineBottom) is over the newer bubble ($newerTop)",
                lineBottom <= newerTop + OVERLAP_TOLERANCE_PX,
            )
        }
        assertTrue("the tap never opened the time line", sawLine)
    }

    @Test
    fun theFooterToggleHoldsPlacementOffOnlyWhileTheLineOpens() {
        compose.mainClock.autoAdvance = false
        val items = state(listOf(message(id = 1, text = TAPPED, minutesAgo = 2))).items
        var toggled = emptyList<String>()
        var motion: ListMotion? = null
        compose.setContent {
            WaiveraTheme(darkTheme = false, motion = WaiveraMotionScheme.Standard, hapticsEnabled = false) {
                val rows = remember { MessageRowCallbacks.None.withToggleTimestamp { toggled = toggled + it } }
                motion = rememberListMotion(items, rows)
            }
        }
        compose.mainClock.advanceTimeBy(FRAME_MILLIS)
        assertFalse("placement is held before any toggle", requireNotNull(motion).holdsPlacement)
        compose.runOnUiThread {
            requireNotNull(motion).rows.onToggleTimestamp("message-1")
            Snapshot.sendApplyNotifications()
        }
        compose.mainClock.advanceTimeBy(FRAME_MILLIS)
        assertEquals("the toggle did not reach the screen", listOf("message-1"), toggled)
        assertTrue("placement still animates while the line opens", requireNotNull(motion).holdsPlacement)
        compose.mainClock.advanceTimeBy(LIST_SETTLE_MILLIS + FRAME_MILLIS)
        assertFalse("placement stays held after the line settled", requireNotNull(motion).holdsPlacement)
    }

    @Test
    fun aSendLandsWithTheDayLineItOpensButNotWithOlderRows() {
        val older = message(id = 1, text = OLDER, minutesAgo = DAY_MINUTES)
        val send = message(id = SEND_ID, text = "On my way", minutesAgo = 0, author = DemoData.ME)
        val newestFirst = state(listOf(older, send), liveArrivals = setOf(SEND_ID)).items.asReversed()
        val landing = landingRows(newestFirst)
        assertEquals(setOf(newestFirst[0].key, newestFirst[1].key), landing)
        assertTrue(newestFirst[1] is TimelineItem.DaySeparator)
        assertTrue(newestFirst[0].isOwnLiveSend())
        val sameDay = state(listOf(message(id = 1, text = OLDER, minutesAgo = 1), send), setOf(SEND_ID)).items
        val onlySend = landingRows(sameDay.asReversed())
        assertEquals("only the send lands when no line opens", setOf(sameDay.last().key), onlySend)
    }

    private fun lineShown(): Boolean =
        compose.onAllNodesWithText(TIME_LINE, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private companion object {
        const val FRAME_MILLIS = 16L
        const val SETTLE_MILLIS = 1_000L
        const val LANDED_MILLIS = 250L
        const val MINUTE_SECONDS = 60L
        const val DAY_MINUTES = 24 * 60L
        const val SEND_ID = -1L
        const val OLDER = "Newest message from yesterday"
        const val TAPPED = "Middle of the run"
        const val NEWER = "Last of the run"
        const val TIME_LINE = "2 minutes ago"
        const val TOGGLE_FRAMES = 20
        const val MIN_SHIFT_PX = 40f
        const val MAX_FIRST_FRAME_SHARE = 0.25f
        const val LANDED_TOLERANCE_PX = 2f
        const val OVERLAP_TOLERANCE_PX = 0.5f
        const val FULL_HISTORY_ROWS = 30L
        const val SENT_TEXT = "On my way over now"
    }
}
