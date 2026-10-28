package com.waivera.waivera.navtour

import androidx.compose.ui.test.hasContentDescription
import com.waivera.waivera.core.demo.DemoData
import com.waivera.waivera.feature.calendar.R as CalendarR
import com.waivera.waivera.feature.today.R as TodayR
import com.waivera.waivera.navigation.CalendarEventRoute
import com.waivera.waivera.navigation.ConversationRoute
import com.waivera.waivera.navigation.ScheduleRoute
import com.waivera.waivera.navigation.ThreadRoute
import com.waivera.waivera.navigation.TodayRoute
import com.waivera.waivera.navigation.TopLevelTab
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Test

/**
 * Path 5: every kind of Today row and where it leads. Rows push onto the Today tab, so back
 * returns to Today (app-shell.md "Tabs and back"); "See schedule" switches to the Schedule tab.
 */
@HiltAndroidTest
class Path05TodayTest : NavTourHarness() {

    @Test
    fun path05_directMessageRow_opensConversationOnToday() {
        TourSeeds.priyaAsks(world.backend())
        launch()
        showAllNeedsYou()
        expectScreen("Needs you")
        shot("path_05_today_01_overview")
        shotDark("path_05_today_01_overview")

        click(hasClickLabel("Open conversation") and textOrDescription(PRIYA, substring = true))
        expectSelected(TopLevelTab.Today)
        expectStack(TodayRoute, ConversationRoute(DemoData.DM_PRIYA))
        expectScreen(PRIYA)
        shot("path_05_today_02_dm_conversation")

        systemBack()
        expectStack(TodayRoute)
        expectScreen("Needs you")
    }

    @Test
    fun path05_mentionRow_opensConversationAtTheMessage() {
        launch()
        showAllNeedsYou()
        click(hasClickLabel("Open message") and textOrDescription("the Hughes visit moved to 10", substring = true))
        expectTop(ConversationRoute(DemoData.GROUP, messageId = MENTION_MESSAGE))
        expectStack(TodayRoute, ConversationRoute(DemoData.GROUP))
        expectScreen("the Hughes visit moved to 10", substring = true)
        shot("path_05_today_03_mention_conversation")

        systemBack()
        expectStack(TodayRoute)
    }

    @Test
    fun path05_threadReplyActivity_opensTheThreadAtTheReply() {
        launch()
        click(hasClickLabel("Open message") and textOrDescription("Sunday is mine", substring = true))
        expectTop(ThreadRoute(DemoData.DM_PRIYA, DemoData.THREAD_ROOT, messageId = THREAD_REPLY))
        expectStack(TodayRoute, ThreadRoute(DemoData.DM_PRIYA, DemoData.THREAD_ROOT))
        expectScreen("Thread")
        shot("path_05_today_04_thread_reply")

        systemBack()
        expectStack(TodayRoute)
    }

    @Test
    fun path05_nextUpRow_opensTheEvent() {
        launch()
        click(hasClickLabel("Open event") and textOrDescription("Client home visit", substring = true))
        expectStack(TodayRoute, CalendarEventRoute(VISIT))
        expectScreen("Event")
        expectScreen("Client home visit", substring = true)
        shot("path_05_today_05_event")

        systemBack()
        expectStack(TodayRoute)
    }

    @Test
    fun path05_seeSchedule_switchesToTheScheduleTab() {
        launch()
        click(hasClickLabel("Open schedule") or textOrDescription("See schedule"))
        expectSelected(TopLevelTab.Schedule)
        expectStack(TodayRoute, ScheduleRoute)
        shot("path_05_today_06_see_schedule")

        systemBack()
        expectSelected(TopLevelTab.Today)
        expectStack(TodayRoute)
    }

    @Test
    fun path05_invitationAccept_onToday() {
        launch()
        showAllNeedsYou()
        click(hasContentDescription(ACCEPT_INVITE))
        expectScreen(stringOf(TodayR.string.feature_today_invitation_accepted))
        expectStack(TodayRoute)
        shot("path_05_today_07_invitation_accepted")
    }

    @Test
    fun path05_invitationAccept_onToday_undoBringsItBack() {
        launch()
        showAllNeedsYou()
        click(hasContentDescription(ACCEPT_INVITE))
        expectScreen(stringOf(TodayR.string.feature_today_invitation_accepted))
        click("Undo")
        expectScreen(ACCEPT_INVITE)
        expectStack(TodayRoute)
    }

    @Test
    fun path05_invitationRow_opensEvent_acceptThenUndo() {
        launch()
        showAllNeedsYou()
        click(hasClickLabel("Open event") and textOrDescription(INVITE_TITLE, substring = true))
        expectStack(TodayRoute, CalendarEventRoute(INVITE))
        expectScreen("You haven't responded yet.")
        shot("path_05_today_08_invitation_event")

        // The RSVP choice reads "Going" / "Not going" since the event screen fix.
        click(textOrDescription("Going"))
        expectScreen(stringOf(CalendarR.string.feature_calendar_undo_accepted))
        shot("path_05_today_09_invitation_accepted_undo")

        click("Undo")
        expectScreen("You haven't responded yet.")
        expectStack(TodayRoute, CalendarEventRoute(INVITE))
        shot("path_05_today_10_invitation_undone")

        systemBack()
        expectStack(TodayRoute)
    }

    /**
     * Needs you folds after three rows, as in concept A; opens the rest (when there are more) so
     * every row kind can be reached.
     */
    private fun showAllNeedsYou() {
        val showAll = textOrDescription(stringOf(TodayR.string.feature_today_brand_show_all))
        if (exists(showAll and clickable())) click(showAll)
    }

    private companion object {
        const val PRIYA = "Priya Raman"
        const val MENTION_MESSAGE = 9500L
        const val THREAD_REPLY = 9332L
        const val VISIT = "demo_visit"
        const val INVITE = "demo_invite"
        const val INVITE_TITLE = "Care plan review"
        const val ACCEPT_INVITE = "Accept invitation to $INVITE_TITLE"
    }
}
