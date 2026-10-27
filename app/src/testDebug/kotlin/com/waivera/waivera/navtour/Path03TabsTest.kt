package com.waivera.waivera.navtour

import androidx.compose.ui.test.hasText
import com.waivera.waivera.core.demo.DemoData
import com.waivera.waivera.navigation.CalendarEventRoute
import com.waivera.waivera.navigation.ChatListRoute
import com.waivera.waivera.navigation.ConversationRoute
import com.waivera.waivera.navigation.ScheduleRoute
import com.waivera.waivera.navigation.TodayRoute
import com.waivera.waivera.navigation.TopLevelTab
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Test

/** Path 3: the three tabs, their own back stacks, and back at each root (app-shell.md "Tabs and back"). */
@HiltAndroidTest
class Path03TabsTest : NavTourHarness() {

    @Test
    fun path03_switchTabs_todayChatScheduleToday() {
        launch()
        expectStack(TodayRoute)
        expectScreen("Needs you")
        shot("path_03_tabs_01_today")
        shotDark("path_03_tabs_01_today")

        clickTab("Chat")
        expectSelected(TopLevelTab.Chat)
        expectStack(TodayRoute, ChatListRoute)
        expectScreen("Chats")
        shot("path_03_tabs_02_chat")
        shotDark("path_03_tabs_02_chat")

        clickTab("Schedule")
        expectSelected(TopLevelTab.Schedule)
        expectStack(TodayRoute, ScheduleRoute)
        // The subtitle this used to check was removed by the schedule layout fix; the view switcher is Schedule-only.
        expectScreen("Month")
        shot("path_03_tabs_03_schedule")
        shotDark("path_03_tabs_03_schedule")

        clickTab("Today")
        expectSelected(TopLevelTab.Today)
        expectStack(TodayRoute)
        shot("path_03_tabs_04_today_again")
    }

    @Test
    fun path03_backFromChatRoot_returnsToToday_thenLeaves() {
        launch()
        clickTab("Chat")
        expectStack(TodayRoute, ChatListRoute)

        systemBack()
        expectSelected(TopLevelTab.Today)
        expectStack(TodayRoute)
        expectScreen("Needs you")
        shot("path_03_tabs_05_back_from_chat_root")

        assertBackLeavesApp()
    }

    @Test
    fun path03_backFromScheduleRoot_returnsToToday() {
        launch()
        clickTab("Schedule")
        expectStack(TodayRoute, ScheduleRoute)

        predictiveBack(shotName = "path_03_tabs_06_predictive_back_from_schedule")
        expectSelected(TopLevelTab.Today)
        expectStack(TodayRoute)
        shot("path_03_tabs_07_back_from_schedule_root")
    }

    @Test
    fun path03_cancelledPredictiveBack_staysOnChat() {
        launch()
        clickTab("Chat")
        expectStack(TodayRoute, ChatListRoute)

        cancelledPredictiveBack()
        expectSelected(TopLevelTab.Chat)
        expectStack(TodayRoute, ChatListRoute)
        expectScreen("Chats")
    }

    @Test
    fun path03_reselectingTheTabAtItsRoot_changesNothing() {
        launch()
        clickTab("Chat")
        expectStack(TodayRoute, ChatListRoute)
        clickTab("Chat")
        expectSelected(TopLevelTab.Chat)
        expectStack(TodayRoute, ChatListRoute)
    }

    /**
     * The bottom bar only shows on tab roots, so a tab is left mid-stack by a link. Each tab keeps
     * its own stack: Chat still has the conversation after the person went through Schedule.
     */
    @Test
    fun path03_eachTabKeepsItsOwnStack() {
        launch()
        clickTab("Chat")
        click(hasClickLabel(OPEN_CONVERSATION) and hasText(PRIYA))
        expectStack(TodayRoute, ChatListRoute, ConversationRoute(DemoData.DM_PRIYA))
        shot("path_03_tabs_08_chat_conversation")

        openLink("waivera://calendar/$VISIT")
        expectSelected(TopLevelTab.Schedule)
        expectStack(TodayRoute, ScheduleRoute, CalendarEventRoute(VISIT))
        expectScreen("Event")
        shot("path_03_tabs_09_schedule_event_from_link")

        systemBack()
        expectStack(TodayRoute, ScheduleRoute)
        systemBack()
        expectSelected(TopLevelTab.Today)
        expectStack(TodayRoute)

        clickTab("Chat")
        expectSelected(TopLevelTab.Chat)
        expectStack(TodayRoute, ChatListRoute, ConversationRoute(DemoData.DM_PRIYA))
        expectScreen("Priya Raman")
        shot("path_03_tabs_10_chat_stack_kept")
    }

    private companion object {
        const val OPEN_CONVERSATION = "Open conversation"
        const val PRIYA = "Priya Raman"
        const val VISIT = "demo_visit"
    }
}
