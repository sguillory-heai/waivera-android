package com.waivera.waivera.feature.chat

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.waivera.waivera.core.data.model.Message
import com.waivera.waivera.core.demo.DemoData
import com.waivera.waivera.core.designsystem.theme.WaiveraMotionScheme
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.core.screenshottesting.ScreenshotSdk
import com.waivera.waivera.feature.chat.conversation.ConversationContent
import com.waivera.waivera.feature.chat.conversation.ConversationUiState
import com.waivera.waivera.feature.chat.message.RowTime
import com.waivera.waivera.feature.chat.message.TimelineBuilder
import kotlin.math.abs
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Scrolled up two screens, "Jump to latest" floats over the message list as a compact pill (owner's
 * device review, 2026-10-30): the list runs to the composer behind it, with no band of its own,
 * so messages stay visible and scroll underneath. It replaced the strip under the list (UI4-03).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ScreenshotSdk.VALUE], qualifiers = "w360dp-h640dp-notnight-xhdpi")
class JumpPillPlacementTest {
    @get:Rule
    val compose = createComposeRule()

    private val messages = (1..MESSAGE_COUNT).map { n ->
        Message(
            id = n.toLong(),
            conversationId = DemoData.DM_PRIYA,
            authorId = if (n % 2 == 0) DemoData.ME else DemoData.PRIYA,
            text = "Message $n about the Hughes visit and the Friday handover notes",
            createdAt = DemoData.NOW.minusSeconds((MESSAGE_COUNT - n) * MINUTE_SECONDS),
        )
    }

    private val state = ConversationUiState(
        title = "Priya Raman",
        isDirect = true,
        content = ConversationContent.Messages,
        items = TimelineBuilder.build(
            messages = messages,
            myUserId = DemoData.ME,
            zone = DemoData.ZONE,
            botConversation = false,
        ),
        canWrite = true,
        myUserId = DemoData.ME,
    )

    private fun show() {
        compose.setContent {
            WaiveraTheme(darkTheme = false, motion = WaiveraMotionScheme.Reduced, hapticsEnabled = false) {
                ConversationScreenContent(
                    state = state,
                    draft = "",
                    onDraftChange = {},
                    time = RowTime(DemoData.NOW, DemoData.ZONE),
                    actions = ConversationActions.None,
                    listState = rememberLazyListState(initialFirstVisibleItemIndex = SCROLLED_UP_INDEX),
                    skeletonDelayMillis = 0,
                )
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun thePillFloatsInsideTheListAboveItsBottomEdge() {
        show()
        val pill = compose.onNodeWithText("Jump to latest").assertIsDisplayed().getBoundsInRoot()
        val list = compose.onNodeWithTag(MESSAGE_LIST_TAG).getBoundsInRoot()
        assertTrue(
            "the list ends at ${list.bottom}, above the pill's bottom at ${pill.bottom}",
            list.bottom > pill.bottom,
        )
        assertTrue("the pill $pill is outside the list $list", pill.top >= list.top)
        assertTrue(
            "the pill is not centred on the list",
            abs((pill.left + pill.right).value - (list.left + list.right).value) < 2f,
        )
    }

    @Test
    fun messagesStayVisibleUnderThePill() {
        show()
        val pill = compose.onNodeWithText("Jump to latest").getBoundsInRoot()
        val bubbles = compose.onAllNodesWithText("Message ", substring = true)
        val underneath = bubbles.fetchSemanticsNodes().indices.map { bubbles[it].getBoundsInRoot() }
            .any { it.top < pill.bottom && it.bottom > pill.top }
        assertTrue("no message runs under the pill at $pill", underneath)
    }

    private companion object {
        const val MESSAGE_COUNT = 40
        const val MINUTE_SECONDS = 60L

        /** About two small-phone screens of bubbles above the newest. */
        const val SCROLLED_UP_INDEX = 16
    }
}
