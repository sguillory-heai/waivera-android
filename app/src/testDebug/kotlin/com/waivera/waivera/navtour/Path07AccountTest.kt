package com.waivera.waivera.navtour

import com.waivera.waivera.core.data.model.ThemePreference
import com.waivera.waivera.core.demo.DemoData
import com.waivera.waivera.feature.auth.R as AuthR
import com.waivera.waivera.navigation.AppGate
import com.waivera.waivera.navigation.AppearanceRoute
import com.waivera.waivera.navigation.BookmarksRoute
import com.waivera.waivera.navigation.ChangePasswordRoute
import com.waivera.waivera.navigation.ChatListRoute
import com.waivera.waivera.navigation.ConversationRoute
import com.waivera.waivera.navigation.NavState
import com.waivera.waivera.navigation.NotificationSettingsRoute
import com.waivera.waivera.navigation.PeopleRoute
import com.waivera.waivera.navigation.PersonRoute
import com.waivera.waivera.navigation.ProfileRoute
import com.waivera.waivera.navigation.SettingsRoute
import com.waivera.waivera.navigation.TodayRoute
import com.waivera.waivera.navigation.TopLevelTab
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Path 7: the account menu (avatar in every tab's top bar) to Profile, People, Bookmarks and
 * Settings, and Settings down to sign-out. Account-menu screens push onto the current tab;
 * Settings sits over Profile (app-shell.md, 08 §6).
 */
@HiltAndroidTest
class Path07AccountTest : NavTourHarness() {

    @Test
    fun path07_accountMenu_viewProfile_back() {
        launch()
        openAccountMenu()
        expectStack(TodayRoute)
        shot("path_07_account_01_menu")
        shotDark("path_07_account_01_menu")

        click(VIEW_PROFILE, last = true)
        expectStack(TodayRoute, ProfileRoute)
        expectScreen("Profile")
        shot("path_07_account_02_profile")
        shotDark("path_07_account_02_profile")

        systemBack()
        expectStack(TodayRoute)
    }

    @Test
    fun path07_people_person_message_conversation() {
        launch()
        openAccountMenu()
        click("People", last = true)
        expectStack(TodayRoute, PeopleRoute)
        expectScreen("People")
        shot("path_07_account_03_people")

        click(hasClickLabel("open profile") and textOrDescription(PRIYA, substring = true))
        expectStack(TodayRoute, PeopleRoute, PersonRoute(DemoData.PRIYA))
        expectScreen(PRIYA)
        shot("path_07_account_04_person")
        shotDark("path_07_account_04_person")

        click("Message")
        waitUntil("the conversation over the person") { nav.top is ConversationRoute }
        assertEquals(listOf(TodayRoute, PeopleRoute, PersonRoute(DemoData.PRIYA)), nav.visibleBackStack.dropLast(1))
        expectSelected(TopLevelTab.Today)
        shot("path_07_account_05_person_conversation")

        systemBack()
        expectStack(TodayRoute, PeopleRoute, PersonRoute(DemoData.PRIYA))
        systemBack()
        expectStack(TodayRoute, PeopleRoute)
        systemBack()
        expectStack(TodayRoute)
    }

    @Test
    fun path07_bookmarks_bookmark_opensTheSavedMessage() {
        launch()
        openAccountMenu()
        click("Bookmarks", last = true)
        expectStack(TodayRoute, BookmarksRoute)
        expectScreen("Bookmarks")
        shot("path_07_account_06_bookmarks")
        shotDark("path_07_account_06_bookmarks")

        // The saved thread root opens its conversation scrolled to it (not a reply, so not the thread).
        click(hasClickLabel("Open saved message from $PRIYA"))
        expectTop(ConversationRoute(DemoData.DM_PRIYA, messageId = DemoData.THREAD_ROOT))
        expectStack(TodayRoute, BookmarksRoute, ConversationRoute(DemoData.DM_PRIYA))
        expectScreen("who is on call this weekend?", substring = true)
        shot("path_07_account_07_bookmark_thread_root")

        systemBack()
        expectStack(TodayRoute, BookmarksRoute)
        systemBack()
        expectStack(TodayRoute)
    }

    @Test
    fun path07_settings_appearance_darkTheme() {
        launch()
        openAccountMenu()
        click("Settings", last = true)
        expectStack(TodayRoute, SettingsRoute)
        expectScreen("Preferences")
        shot("path_07_account_08_settings")
        shotDark("path_07_account_08_settings")

        click("Appearance")
        expectStack(TodayRoute, SettingsRoute, AppearanceRoute)
        expectScreen("Theme")
        shot("path_07_account_09_appearance")

        click("Dark")
        waitUntil("the dark theme saved") { world.backend().settings.value.theme == ThemePreference.DARK }
        shot("path_07_account_10_appearance_dark")

        systemBack()
        expectStack(TodayRoute, SettingsRoute)
        shot("path_07_account_11_settings_in_dark")
    }

    @Test
    fun path07_settings_notifications_changePassword_signOut() {
        launch()
        openAccountMenu()
        click("Settings", last = true)
        expectStack(TodayRoute, SettingsRoute)

        click("Notifications")
        expectStack(TodayRoute, SettingsRoute, NotificationSettingsRoute)
        expectScreen("Push notifications")
        shot("path_07_account_12_notifications")
        shotDark("path_07_account_12_notifications")
        systemBack()
        expectStack(TodayRoute, SettingsRoute)

        click("Change password")
        expectStack(TodayRoute, SettingsRoute, ChangePasswordRoute)
        expectScreen("Change your password")
        shot("path_07_account_13_change_password")
        click(hasContentDescriptionGoBack())
        expectStack(TodayRoute, SettingsRoute)

        click("Sign out")
        expectScreen("Sign out of Waivera?")
        expectStack(TodayRoute, SettingsRoute)
        shot("path_07_account_14_sign_out_confirm")
        shotDark("path_07_account_14_sign_out_confirm")

        click("Cancel", last = true)
        expectGone("Sign out of Waivera?")
        expectStack(TodayRoute, SettingsRoute)

        click("Sign out")
        expectScreen("Sign out of Waivera?")
        click("Sign out", last = true)
        expectGate(AppGate.SignIn())
        expectScreen(stringOf(AuthR.string.feature_auth_sign_in_title))
        expectScreen("You're signed out")
        // Signing out discards every back stack (app-shell.md "Auth gate").
        assertEquals(NavState(), nav)
        shot("path_07_account_15_signed_out")
    }

    @Test
    fun path07_backFromSettings_returnsWhereTheMenuWasOpened() {
        launch()
        openAccountMenu()
        click("Settings", last = true)
        expectStack(TodayRoute, SettingsRoute)

        systemBack()
        expectStack(TodayRoute)
        expectScreen("Needs you")
        shot("path_07_account_16_back_from_settings")

        // Profile's own Settings row still stacks Settings over Profile, and back returns there.
        openAccountMenu()
        click(VIEW_PROFILE, last = true)
        click(textOrDescription("Notifications, appearance and account", substring = true))
        expectStack(TodayRoute, ProfileRoute, SettingsRoute)
        systemBack()
        expectStack(TodayRoute, ProfileRoute)
    }

    @Test
    fun path07_accountMenuFromChat_pushesOnTheChatTab() {
        launch()
        clickTab("Chat")
        openAccountMenu()
        click("People", last = true)
        expectSelected(TopLevelTab.Chat)
        expectStack(TodayRoute, ChatListRoute, PeopleRoute)
        shot("path_07_account_17_people_over_chat")

        systemBack()
        expectStack(TodayRoute, ChatListRoute)
    }

    private fun hasContentDescriptionGoBack() = textOrDescription(GO_BACK)

    private companion object {
        const val PRIYA = "Priya Raman"
    }
}
