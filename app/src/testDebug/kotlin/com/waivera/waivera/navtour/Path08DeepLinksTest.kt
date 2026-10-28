package com.waivera.waivera.navtour

import androidx.compose.ui.test.hasText
import com.waivera.waivera.R
import com.waivera.waivera.core.data.model.SessionState
import com.waivera.waivera.core.demo.DemoData
import com.waivera.waivera.core.notifications.deeplink.DeepLinkTarget
import com.waivera.waivera.core.notifications.presentation.PresentationDecision
import com.waivera.waivera.navigation.AppGate
import com.waivera.waivera.navigation.CalendarEventRoute
import com.waivera.waivera.navigation.ChatListRoute
import com.waivera.waivera.navigation.ConversationRoute
import com.waivera.waivera.navigation.NotificationSettingsRoute
import com.waivera.waivera.navigation.PeopleRoute
import com.waivera.waivera.navigation.PersonRoute
import com.waivera.waivera.navigation.ProfileRoute
import com.waivera.waivera.navigation.ScheduleRoute
import com.waivera.waivera.navigation.SettingsRoute
import com.waivera.waivera.navigation.ThreadRoute
import com.waivera.waivera.navigation.TodayRoute
import com.waivera.waivera.navigation.TopLevelTab
import com.waivera.waivera.navigation.contentKey
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Path 8: `waivera://` links and notification taps, cold and warm. Each target opens on its tab
 * with the full parent stack from `DeepLinkRouting` (app-shell.md "Deep links"), so back walks up
 * through the parents; unknown links change nothing.
 */
@HiltAndroidTest
@Suppress("TooManyFunctions")
class Path08DeepLinksTest : NavTourHarness() {

    @Test
    fun path08_coldLink_conversation_backWalksUp() {
        launch("waivera://chats/${DemoData.DM_PRIYA}?messageId=$CHAT_MESSAGE")
        expectSelected(TopLevelTab.Chat)
        expectStack(TodayRoute, ChatListRoute, ConversationRoute(DemoData.DM_PRIYA))
        expectTop(ConversationRoute(DemoData.DM_PRIYA, messageId = CHAT_MESSAGE))
        expectScreen("Priya Raman")
        shot("path_08_links_01_conversation")

        systemBack()
        expectStack(TodayRoute, ChatListRoute)
        systemBack()
        expectStack(TodayRoute)
        assertBackLeavesApp()
    }

    @Test
    fun path08_coldLink_thread() {
        launch("waivera://chats/${DemoData.DM_PRIYA}/thread/${DemoData.THREAD_ROOT}?messageId=$THREAD_REPLY")
        expectStack(
            TodayRoute,
            ChatListRoute,
            ConversationRoute(DemoData.DM_PRIYA),
            ThreadRoute(DemoData.DM_PRIYA, DemoData.THREAD_ROOT),
        )
        expectTop(ThreadRoute(DemoData.DM_PRIYA, DemoData.THREAD_ROOT, messageId = THREAD_REPLY))
        expectScreen("Thread")
        shot("path_08_links_02_thread")

        systemBack()
        expectStack(TodayRoute, ChatListRoute, ConversationRoute(DemoData.DM_PRIYA))
        systemBack()
        expectStack(TodayRoute, ChatListRoute)
        systemBack()
        expectStack(TodayRoute)
    }

    @Test
    fun path08_coldLink_event() {
        launch("waivera://calendar/$VISIT")
        expectSelected(TopLevelTab.Schedule)
        expectStack(TodayRoute, ScheduleRoute, CalendarEventRoute(VISIT))
        expectScreen("Client home visit", substring = true)
        shot("path_08_links_03_event")

        systemBack()
        expectStack(TodayRoute, ScheduleRoute)
        systemBack()
        expectStack(TodayRoute)
    }

    @Test
    fun path08_coldLink_profile() {
        launch("waivera://profile")
        expectSelected(TopLevelTab.Today)
        expectStack(TodayRoute, ProfileRoute)
        expectScreen("Profile")
        shot("path_08_links_04_profile")

        systemBack()
        expectStack(TodayRoute)
    }

    @Test
    fun path08_coldLink_person() {
        launch("waivera://people/${DemoData.PRIYA}")
        expectStack(TodayRoute, PeopleRoute, PersonRoute(DemoData.PRIYA))
        expectScreen("Priya Raman")
        shot("path_08_links_05_person")

        systemBack()
        expectStack(TodayRoute, PeopleRoute)
        systemBack()
        expectStack(TodayRoute)
    }

    @Test
    fun path08_coldLink_notificationSettings() {
        launch("waivera://settings/notifications")
        expectStack(TodayRoute, SettingsRoute, NotificationSettingsRoute)
        expectScreen("Push notifications")
        shot("path_08_links_06_notification_settings")

        systemBack()
        expectStack(TodayRoute, SettingsRoute)
        systemBack()
        expectStack(TodayRoute)
    }

    @Test
    fun path08_unknownAndMalformedLinks_changeNothing() {
        launch("waivera://nowhere/7")
        expectStack(TodayRoute)
        expectScreen("Needs you")
        // The stack stays, but the tap is acknowledged (UI2-22).
        expectScreen(stringOf(R.string.shell_link_unopenable))
        shot("path_08_links_07_unknown")

        openLink("waivera://chats/not-a-number")
        expectStack(TodayRoute)
        openLink("waivera://chats/-7")
        expectStack(TodayRoute)
        openLink("https://app.example.test/chats/7")
        expectStack(TodayRoute)
        openLink("waivera://people/${"x".repeat(TOO_LONG_ID)}")
        expectStack(TodayRoute)
    }

    @Test
    fun path08_warmLink_fromAnotherTab_opensWithAFreshStack() {
        launch()
        clickTab("Schedule")
        expectStack(TodayRoute, ScheduleRoute)

        openLink("waivera://chats/${DemoData.DM_PRIYA}")
        expectSelected(TopLevelTab.Chat)
        expectStack(TodayRoute, ChatListRoute, ConversationRoute(DemoData.DM_PRIYA))
        shot("path_08_links_08_warm_conversation")

        systemBack()
        expectStack(TodayRoute, ChatListRoute)
        systemBack()
        expectStack(TodayRoute)
    }

    @Test
    fun path08_pushTap_chatMessage_opensTheConversation() {
        launch()
        val decision = receivePush(pushFixture("chat-message.json"))
        assertTrue("Expected a notification, got $decision", decision is PresentationDecision.Show)

        val tap = checkNotNull(latestNotificationTapIntent()) { "No notification was posted" }
        assertEquals("waivera://chats/${DemoData.DM_PRIYA}?messageId=$CHAT_MESSAGE", tap.dataString)
        deliver(tap)
        expectSelected(TopLevelTab.Chat)
        expectStack(TodayRoute, ChatListRoute, ConversationRoute(DemoData.DM_PRIYA))
        expectTop(ConversationRoute(DemoData.DM_PRIYA, messageId = CHAT_MESSAGE))
        shot("path_08_push_01_chat_message")
    }

    @Test
    fun path08_pushTap_threadReply_opensTheThread() {
        launch()
        val fixture = pushFixture("chat-message-thread-reply.json")
        val root = checkNotNull(fixture.data["threadRootMessageId"]).toLong()
        val reply = checkNotNull(fixture.data["messageId"]).toLong()
        receivePush(fixture)

        val tap = checkNotNull(latestNotificationTapIntent()) { "No notification was posted" }
        deliver(tap)
        expectStack(
            TodayRoute,
            ChatListRoute,
            ConversationRoute(DemoData.DM_PRIYA),
            ThreadRoute(DemoData.DM_PRIYA, root),
        )
        expectTop(ThreadRoute(DemoData.DM_PRIYA, root, messageId = reply))
        // The reply the notification was about is in the thread it opened (UI2-08).
        expectScreen(checkNotNull(fixture.data["body"]))
        expectGone("No replies yet")
        shot("path_08_push_02_thread_reply")

        systemBack()
        expectStack(TodayRoute, ChatListRoute, ConversationRoute(DemoData.DM_PRIYA))
    }

    @Test
    fun path08_pushTap_legacyMeeting_opensTheEvent() {
        val fixture = pushFixture("legacy-meeting.json")
        assertEquals("waivera://calendar/$LEGACY_EVENT", tapUriFor(fixture))

        launch(tapUriFor(fixture))
        expectSelected(TopLevelTab.Schedule)
        expectStack(TodayRoute, ScheduleRoute, CalendarEventRoute(LEGACY_EVENT))
        shot("path_08_push_03_legacy_meeting")

        systemBack()
        expectStack(TodayRoute, ScheduleRoute)
    }

    @Test
    fun path08_pushTap_unknownType_opensTheInbox() {
        launch()
        receivePush(pushFixture("unknown-type.json"))
        val tap = checkNotNull(latestNotificationTapIntent()) { "No notification was posted" }
        assertEquals("waivera://chats", tap.dataString)

        deliver(tap)
        expectSelected(TopLevelTab.Chat)
        expectStack(TodayRoute, ChatListRoute)
        shot("path_08_push_04_unknown_type")
    }

    /** app-shell.md: "If the target is already on top, nothing changes; a newer messageId only moves the highlight." */
    @Test
    fun path08_pushForTheOpenConversation_isSuppressedAndTapKeepsTheScreen() {
        launch()
        clickTab("Chat")
        click(hasClickLabel("Open conversation") and hasText("Priya Raman"))
        expectStack(TodayRoute, ChatListRoute, ConversationRoute(DemoData.DM_PRIYA))
        val before = postedNotificationCount()

        val fixture = pushFixture("chat-message.json")
        val decision = receivePush(fixture)
        assertEquals(PresentationDecision.Suppress(PresentationDecision.SuppressReason.ChatOnScreen), decision)
        assertEquals("No notification for the chat on screen", before, postedNotificationCount())

        openLink(tapUriFor(fixture))
        expectSelected(TopLevelTab.Chat)
        expectStack(TodayRoute, ChatListRoute, ConversationRoute(DemoData.DM_PRIYA))
        expectTop(ConversationRoute(DemoData.DM_PRIYA, messageId = CHAT_MESSAGE))
        shot("path_08_push_05_already_open")
    }

    /**
     * The same rule when the conversation was opened from Today's "Needs you": it is on screen, so
     * a tap for it leaves it there, on the Today tab, instead of rebuilding the Chat tab (NAV-01).
     */
    @Test
    fun path08_pushTapForConversationAlreadyOpenFromToday_staysOnSameScreen() {
        TourSeeds.priyaAsks(world.backend())
        launch()
        click(hasClickLabel("Open conversation") and textOrDescription("Priya Raman", substring = true))
        expectStack(TodayRoute, ConversationRoute(DemoData.DM_PRIYA))

        openLink("waivera://chats/${DemoData.DM_PRIYA}?messageId=$CHAT_MESSAGE")
        settle()
        assertEquals("tab", TopLevelTab.Today, nav.selected)
        assertEquals(
            "stack",
            listOf(TodayRoute.contentKey, "conversation:${DemoData.DM_PRIYA}"),
            nav.visibleBackStack.map { it.contentKey },
        )
    }

    @Test
    fun path08_linkWhileSignedOut_waitsForSignIn() {
        world.session().force(SessionState.SignedOut())
        launch("waivera://chats/${DemoData.DM_PRIYA}")
        expectGate(AppGate.SignIn())
        assertEquals(
            DeepLinkTarget.Conversation(DemoData.DM_PRIYA),
            shell.pendingDeepLink,
        )

        typeInto("Email", "alex.rivera@example.com")
        typeInto("Password", "demo")
        click("Sign in")
        expectGate(AppGate.Main)
        expectStack(TodayRoute, ChatListRoute, ConversationRoute(DemoData.DM_PRIYA))
        shot("path_08_links_09_after_sign_in")
    }

    @Test
    fun path08_signOutDiscardsAWaitingLink() {
        world.session().force(SessionState.PasswordChangeRequired(DemoData.currentUser()))
        launch("waivera://chats/${DemoData.DM_PRIYA}")
        expectGate(AppGate.PasswordChange)

        click("Sign out")
        expectGate(AppGate.SignIn())
        typeInto("Email", "alex.rivera@example.com")
        typeInto("Password", "demo")
        click("Sign in")
        expectGate(AppGate.Main)
        expectStack(TodayRoute)
    }

    private companion object {
        const val CHAT_MESSAGE = 9312L
        const val THREAD_REPLY = 9332L
        const val VISIT = "demo_visit"
        const val LEGACY_EVENT = "evt_8f2b19c4"
        const val TOO_LONG_ID = 200
    }
}
