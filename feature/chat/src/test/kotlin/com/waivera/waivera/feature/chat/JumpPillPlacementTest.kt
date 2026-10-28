package com.waivera.waivera.feature.chat

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Scrolled up two screens, "Jump to latest" sits under the message list, not over it: the
 * list's visible area (which clips every bubble) ends above the pill (UI4-03).
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

    @Test
    fun theListEndsAboveTheJumpPill() {
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
        val pill = compose.onNodeWithText("Jump to latest").assertIsDisplayed().getBoundsInRoot()
        val list = compose.onNodeWithTag(MESSAGE_LIST_TAG).getBoundsInRoot()
        assertTrue("the list ends at ${list.bottom}, under the pill's top at ${pill.top}", list.bottom <= pill.top)
    }

    private companion object {
        const val MESSAGE_COUNT = 40
        const val MINUTE_SECONDS = 60L

        /** About two small-phone screens of bubbles above the newest. */
        const val SCROLLED_UP_INDEX = 16
    }
}
