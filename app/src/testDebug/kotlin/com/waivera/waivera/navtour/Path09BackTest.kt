package com.waivera.waivera.navtour

import androidx.activity.ComponentDialog
import com.waivera.waivera.core.demo.DemoData
import com.waivera.waivera.navigation.AppRoute
import com.waivera.waivera.navigation.AppearanceRoute
import com.waivera.waivera.navigation.BookmarksRoute
import com.waivera.waivera.navigation.CalendarEventRoute
import com.waivera.waivera.navigation.ChangePasswordRoute
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
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Test
import org.robolectric.shadows.ShadowDialog

/**
 * Path 9: a predictive back swipe on every screen, captured halfway, lands where app-shell.md
 * says: the parent inside a tab, Today from another tab's root, and out of the app from Today's
 * root. Sheets close before the screen under them moves.
 */
@HiltAndroidTest
@Suppress("TooManyFunctions")
class Path09BackTest : NavTourHarness() {

    @Test
    fun path09_conversation_backToChatList() = backCase(
        name = "path_09_back_01_conversation",
        start = { launch("waivera://chats/${DemoData.DM_PRIYA}") },
        from = listOf(TodayRoute, ChatListRoute, ConversationRoute(DemoData.DM_PRIYA)),
        to = listOf(TodayRoute, ChatListRoute),
    )

    @Test
    fun path09_thread_backToConversation() = backCase(
        name = "path_09_back_02_thread",
        start = { launch("waivera://chats/${DemoData.DM_PRIYA}/thread/${DemoData.THREAD_ROOT}") },
        from = listOf(TodayRoute, ChatListRoute, ConversationRoute(DemoData.DM_PRIYA), thread()),
        to = listOf(TodayRoute, ChatListRoute, ConversationRoute(DemoData.DM_PRIYA)),
    )

    @Test
    fun path09_chatRoot_backToToday() = backCase(
        name = "path_09_back_03_chat_root",
        start = {
            launch()
            clickTab("Chat")
        },
        from = listOf(TodayRoute, ChatListRoute),
        to = listOf(TodayRoute),
        selectedAfter = TopLevelTab.Today,
    )

    @Test
    fun path09_scheduleRoot_backToToday() = backCase(
        name = "path_09_back_04_schedule_root",
        start = {
            launch()
            clickTab("Schedule")
        },
        from = listOf(TodayRoute, ScheduleRoute),
        to = listOf(TodayRoute),
        selectedAfter = TopLevelTab.Today,
    )

    @Test
    fun path09_event_backToSchedule() = backCase(
        name = "path_09_back_05_event",
        start = { launch("waivera://calendar/$VISIT") },
        from = listOf(TodayRoute, ScheduleRoute, CalendarEventRoute(VISIT)),
        to = listOf(TodayRoute, ScheduleRoute),
    )

    @Test
    fun path09_eventFromToday_backToToday() = backCase(
        name = "path_09_back_06_event_from_today",
        start = {
            launch()
            click(hasClickLabel("Open event") and textOrDescription("Client home visit", substring = true))
        },
        from = listOf(TodayRoute, CalendarEventRoute(VISIT)),
        to = listOf(TodayRoute),
    )

    @Test
    fun path09_profile_backToToday() = backCase(
        name = "path_09_back_07_profile",
        start = { launch("waivera://profile") },
        from = listOf(TodayRoute, ProfileRoute),
        to = listOf(TodayRoute),
    )

    @Test
    fun path09_people_backToToday() = backCase(
        name = "path_09_back_08_people",
        start = { launch("waivera://people") },
        from = listOf(TodayRoute, PeopleRoute),
        to = listOf(TodayRoute),
    )

    @Test
    fun path09_person_backToPeople() = backCase(
        name = "path_09_back_09_person",
        start = { launch("waivera://people/${DemoData.PRIYA}") },
        from = listOf(TodayRoute, PeopleRoute, PersonRoute(DemoData.PRIYA)),
        to = listOf(TodayRoute, PeopleRoute),
    )

    @Test
    fun path09_bookmarks_backToToday() = backCase(
        name = "path_09_back_10_bookmarks",
        start = { launch("waivera://bookmarks") },
        from = listOf(TodayRoute, BookmarksRoute),
        to = listOf(TodayRoute),
    )

    @Test
    fun path09_settingsFromAccountMenu_backToProfile() = backCase(
        name = "path_09_back_11_settings",
        start = {
            launch()
            openAccountMenu()
            click("Settings", last = true)
        },
        from = listOf(TodayRoute, ProfileRoute, SettingsRoute),
        to = listOf(TodayRoute, ProfileRoute),
    )

    @Test
    fun path09_notificationSettings_backToSettings() = backCase(
        name = "path_09_back_12_notification_settings",
        start = { launch("waivera://settings/notifications") },
        from = listOf(TodayRoute, SettingsRoute, NotificationSettingsRoute),
        to = listOf(TodayRoute, SettingsRoute),
    )

    @Test
    fun path09_appearance_backToSettings() = backCase(
        name = "path_09_back_13_appearance",
        start = {
            launch("waivera://settings")
            click("Appearance")
        },
        from = listOf(TodayRoute, SettingsRoute, AppearanceRoute),
        to = listOf(TodayRoute, SettingsRoute),
    )

    @Test
    fun path09_changePassword_backToSettings() = backCase(
        name = "path_09_back_14_change_password",
        start = {
            launch("waivera://settings")
            click("Change password")
        },
        from = listOf(TodayRoute, SettingsRoute, ChangePasswordRoute),
        to = listOf(TodayRoute, SettingsRoute),
    )

    @Test
    fun path09_todayRoot_leavesTheApp() {
        launch()
        expectStack(TodayRoute)
        shot("path_09_back_15_today_root")
        assertBackLeavesApp()
    }

    @Test
    fun path09_accountMenuSheet_backClosesOnlyTheSheet() {
        launch()
        clickTab("Chat")
        openAccountMenu()
        shot("path_09_back_16_account_menu_open")

        sheetBack()
        expectGone(VIEW_PROFILE)
        expectSelected(TopLevelTab.Chat)
        expectStack(TodayRoute, ChatListRoute)
        shot("path_09_back_17_account_menu_closed")
    }

    @Test
    fun path09_messageActionsSheet_backClosesOnlyTheSheet() {
        launch("waivera://chats/${DemoData.DM_PRIYA}")
        longPress(textOrDescription("schedule published", substring = true))
        expectScreen("MESSAGE PREVIEW")

        sheetBack()
        expectGone("MESSAGE PREVIEW")
        expectStack(TodayRoute, ChatListRoute, ConversationRoute(DemoData.DM_PRIYA))
    }

    @Test
    fun path09_signOutConfirmation_backClosesOnlyTheSheet() {
        launch("waivera://settings")
        click("Sign out")
        expectScreen("Sign out of Waivera?")

        sheetBack()
        expectGone("Sign out of Waivera?")
        expectStack(TodayRoute, SettingsRoute)
    }

    /**
     * Opens [from], swipes back with a predictive gesture (captured halfway as `<name>_swipe`)
     * and expects [to]. Plain system back on the same screens is covered by paths 3 to 8.
     */
    private fun backCase(
        name: String,
        start: () -> Unit,
        from: List<AppRoute>,
        to: List<AppRoute>,
        selectedAfter: TopLevelTab? = null,
    ) {
        start()
        expectStack(*from.toTypedArray())
        shot("${name}_before")

        predictiveBack(shotName = "${name}_swipe")
        expectStack(*to.toTypedArray())
        selectedAfter?.let(::expectSelected)
        shot("${name}_after")
    }

    /** Back delivered to the newest dialog window, where a Material bottom sheet lives. */
    private fun sheetBack() {
        val dialog = ShadowDialog.getLatestDialog()
        if (dialog is ComponentDialog && dialog.isShowing) {
            dialog.onBackPressedDispatcher.onBackPressed()
        } else {
            systemBack()
        }
        settle()
    }

    private fun thread() = ThreadRoute(DemoData.DM_PRIYA, DemoData.THREAD_ROOT)

    private companion object {
        const val VISIT = "demo_visit"
    }
}
