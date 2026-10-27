package com.waivera.waivera.navtour

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performTouchInput
import com.waivera.waivera.core.demo.DemoData
import com.waivera.waivera.navigation.AppRoute
import com.waivera.waivera.navigation.ChatListRoute
import com.waivera.waivera.navigation.ConversationRoute
import com.waivera.waivera.navigation.ThreadRoute
import com.waivera.waivera.navigation.TodayRoute
import com.waivera.waivera.navigation.TopLevelTab
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Path 4: the chat tab. List, conversation, the message action sheet, the reactions sheet, a
 * thread and back out; the new-chat sheet; a failed send and its retry.
 */
@HiltAndroidTest
class Path04ChatTest : NavTourHarness() {

    @Test
    fun path04_listConversationSheetsThreadAndBack() {
        launch()
        clickTab("Chat")
        expectStack(TodayRoute, ChatListRoute)
        shot("path_04_chat_01_list")

        openPriya()
        shot("path_04_chat_02_conversation")
        shotDark("path_04_chat_02_conversation")

        longPress(textOrDescription(REACTED_MESSAGE, substring = true))
        expectScreen("MESSAGE PREVIEW")
        expectStack(TodayRoute, ChatListRoute, ConversationRoute(DemoData.DM_PRIYA))
        shot("path_04_chat_03_message_actions")
        shotDark("path_04_chat_03_message_actions")

        click("Close message actions")
        expectGone("MESSAGE PREVIEW")

        // Long-press the 🎉 pill by touch: see path04_reactionPill_offersItsActionsToTalkBack.
        node(hasContentDescription(PARTY_PILL)).performTouchInput { longClick() }
        settle()
        expectScreen("Reactions")
        expectScreen("Jordan Lee", substring = true)
        shot("path_04_chat_04_reactions")
        shotDark("path_04_chat_04_reactions")

        click("Close reactions")
        expectGone("Close reactions")

        longPress(textOrDescription(THREAD_ROOT_TEXT, substring = true))
        expectScreen("Reply in thread")
        click("Reply in thread")
        expectStack(TodayRoute, ChatListRoute, ConversationRoute(DemoData.DM_PRIYA), thread())
        expectScreen("Thread")
        expectScreen("Sunday is mine", substring = true)
        shot("path_04_chat_05_thread")
        shotDark("path_04_chat_05_thread")

        systemBack()
        expectStack(TodayRoute, ChatListRoute, ConversationRoute(DemoData.DM_PRIYA))
        expectScreen(PRIYA)
        shot("path_04_chat_06_back_to_conversation")

        systemBack()
        expectStack(TodayRoute, ChatListRoute)
        expectScreen("Chats")
        shot("path_04_chat_07_back_to_list")

        systemBack()
        expectSelected(TopLevelTab.Today)
        expectStack(TodayRoute)
    }

    @Test
    fun path04_threadChip_opensTheThread_backButtonReturns() {
        launch()
        clickTab("Chat")
        openPriya()

        click(textOrDescription("2 replies", substring = true))
        expectStack(TodayRoute, ChatListRoute, ConversationRoute(DemoData.DM_PRIYA), thread())
        shot("path_04_chat_08_thread_from_chip")

        // The top bar's back arrow, not the system back.
        click(hasContentDescription(GO_BACK))
        expectStack(TodayRoute, ChatListRoute, ConversationRoute(DemoData.DM_PRIYA))
        click(hasContentDescription(GO_BACK))
        expectStack(TodayRoute, ChatListRoute)
    }

    @Test
    fun path04_newChatSheet_pickPerson_opensConversation() {
        launch()
        clickTab("Chat")
        expectStack(TodayRoute, ChatListRoute)

        click(hasContentDescription("Start a new chat"))
        expectScreen("New chat")
        expectScreen("Mei Chen")
        shot("path_04_chat_09_new_chat")
        shotDark("path_04_chat_09_new_chat")

        click(hasText("Mei Chen", substring = true) and toggleable())
        expectScreen("Open chat")
        shot("path_04_chat_10_new_chat_selected")

        click("Open chat")
        expectGone("New chat")
        waitUntil("a conversation on the Chat tab") {
            nav.selected == TopLevelTab.Chat && nav.top is ConversationRoute && nav.visibleBackStack.size == STACK_TO_CONVERSATION
        }
        val opened = nav.visibleBackStack
        assertEquals(listOf<AppRoute>(TodayRoute, ChatListRoute), opened.take(2))
        expectScreen("Mei Chen", substring = true)
        shot("path_04_chat_11_new_chat_conversation")

        systemBack()
        expectStack(TodayRoute, ChatListRoute)
    }

    @Test
    fun path04_failedSend_retry_staysInConversation() {
        launch()
        clickTab("Chat")
        openPriya()

        reveal(textOrDescription(FAILED_TEXT, substring = true))
        expectScreen("Failed")
        shot("path_04_chat_12_failed_send")

        click(hasText("Retry"))
        expectGone("Failed")
        expectScreen(FAILED_TEXT, substring = true)
        expectStack(TodayRoute, ChatListRoute, ConversationRoute(DemoData.DM_PRIYA))
        shot("path_04_chat_13_retried")
    }

    /**
     * Every control has to work with TalkBack (AGENTS.md). The reaction pill clears its own
     * semantics after setting the label, which also drops the toggle and "Show who reacted"
     * actions that WaiveraPressable adds further in, so TalkBack can read the pill but not use it.
     */
    @Test
    fun path04_reactionPill_offersItsActionsToTalkBack() {
        launch()
        clickTab("Chat")
        openPriya()
        reveal(hasContentDescription(PARTY_PILL))
        val pill = compose.onAllNodes(hasContentDescription(PARTY_PILL)).fetchSemanticsNodes().first()
        assertTrue("Reaction pill has no click action", SemanticsActions.OnClick in pill.config)
        assertTrue("Reaction pill has no long-click action", SemanticsActions.OnLongClick in pill.config)
    }

    private fun openPriya() {
        click(hasClickLabel("Open conversation") and textOrDescription(PRIYA, substring = true))
        expectStack(TodayRoute, ChatListRoute, ConversationRoute(DemoData.DM_PRIYA))
        expectScreen(PRIYA)
    }

    private fun thread() = ThreadRoute(DemoData.DM_PRIYA, DemoData.THREAD_ROOT)

    private fun toggleable(): SemanticsMatcher = SemanticsMatcher.keyIsDefined(SemanticsProperties.ToggleableState)

    private companion object {
        const val PRIYA = "Priya Raman"
        const val REACTED_MESSAGE = "schedule published"
        const val THREAD_ROOT_TEXT = "who is on call this weekend?"
        const val FAILED_TEXT = "Sending the updated schedule now"
        const val PARTY_PILL = "🎉, 1"
        const val STACK_TO_CONVERSATION = 3
    }
}
