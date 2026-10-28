package com.waivera.waivera.navtour

import com.waivera.waivera.core.data.model.SessionState
import com.waivera.waivera.core.demo.DemoData
import com.waivera.waivera.core.network.error.WaiveraError
import com.waivera.waivera.feature.auth.R as AuthR
import com.waivera.waivera.navigation.AppGate
import com.waivera.waivera.navigation.CalendarEventRoute
import com.waivera.waivera.navigation.ChatListRoute
import com.waivera.waivera.navigation.ConversationRoute
import com.waivera.waivera.navigation.ScheduleRoute
import com.waivera.waivera.navigation.SettingsRoute
import com.waivera.waivera.navigation.ThreadRoute
import com.waivera.waivera.navigation.TodayRoute
import dagger.hilt.android.testing.HiltAndroidTest
import java.time.Instant
import org.junit.Before
import org.junit.Test
import org.robolectric.RuntimeEnvironment

/**
 * Key steps of the tour again at 200% font (`<step>_font200`), the largest size the app supports
 * (AGENTS.md). Same destinations and assertions as the paths; only the font scale differs.
 */
@HiltAndroidTest
class NavigationPathVariantsTest : NavTourHarness() {

    @Before
    fun largestFont() {
        RuntimeEnvironment.setFontScale(LARGEST_FONT_SCALE)
    }

    @Test
    fun font200_signIn() {
        world.session().force(SessionState.SignedOut())
        launch()
        expectGate(AppGate.SignIn())
        expectScreen(stringOf(AuthR.string.feature_auth_sign_in_title))
        shot("path_01_signin_01_empty_font200")
        click("Sign in")
        expectScreen("Enter your email.")
        shot("path_01_signin_02_validation_font200")
    }

    @Test
    fun font200_passwordGate() {
        world.session().force(SessionState.PasswordChangeRequired(DemoData.currentUser()))
        launch()
        expectGate(AppGate.PasswordChange)
        shot("path_02_password_01_required_font200")
    }

    @Test
    fun font200_bootstrapGate() {
        world.session().force(SessionState.BootstrapFailed(WaiveraError.Server(SERVICE_UNAVAILABLE)))
        launch()
        expectGate(AppGate.BootstrapFailed)
        shot("path_02_bootstrap_01_failed_font200")
    }

    @Test
    fun font200_tabs() {
        launch()
        expectStack(TodayRoute)
        shot("path_03_tabs_01_today_font200")
        clickTab("Chat")
        expectStack(TodayRoute, ChatListRoute)
        shot("path_03_tabs_02_chat_font200")
        clickTab("Schedule")
        expectStack(TodayRoute, ScheduleRoute)
        shot("path_03_tabs_03_schedule_font200")
    }

    @Test
    fun font200_conversationAndThread() {
        launch("waivera://chats/${DemoData.DM_PRIYA}")
        expectStack(TodayRoute, ChatListRoute, ConversationRoute(DemoData.DM_PRIYA))
        shot("path_04_chat_02_conversation_font200")
        longPress(textOrDescription("schedule published", substring = true))
        expectScreen("MESSAGE PREVIEW")
        shot("path_04_chat_03_message_actions_font200")
        click("Close message actions")
        expectGone("MESSAGE PREVIEW")

        systemBack()
        systemBack()
        expectStack(TodayRoute)
        openLink("waivera://chats/${DemoData.DM_PRIYA}/thread/${DemoData.THREAD_ROOT}")
        expectStack(
            TodayRoute,
            ChatListRoute,
            ConversationRoute(DemoData.DM_PRIYA),
            ThreadRoute(DemoData.DM_PRIYA, DemoData.THREAD_ROOT),
        )
        shot("path_04_chat_05_thread_font200")
    }

    @Test
    fun font200_scheduleEvent() {
        // Same moment as Path06's default shot, so the 200% image shows the open Join button too (UI3-16).
        world.clock().set(Instant.parse("2026-11-01T19:45:00Z"))
        launch("waivera://calendar/demo_supervision")
        expectStack(TodayRoute, ScheduleRoute, CalendarEventRoute("demo_supervision"))
        expectScreen("Event")
        shot("path_06_schedule_04_event_font200")
    }

    @Test
    fun font200_accountMenuAndSettings() {
        launch()
        openAccountMenu()
        shot("path_07_account_01_menu_font200")
        click("Settings", last = true)
        expectStack(TodayRoute, SettingsRoute)
        shot("path_07_account_08_settings_font200")
        click("Sign out")
        expectScreen("Sign out of Waivera?")
        shot("path_07_account_14_sign_out_confirm_font200")
    }

    private companion object {
        const val LARGEST_FONT_SCALE = 2f
        const val SERVICE_UNAVAILABLE = 503
    }
}
