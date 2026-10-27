package com.waivera.waivera.navtour

import android.app.Activity
import com.waivera.waivera.navigation.CalendarEventRoute
import com.waivera.waivera.navigation.ConversationRoute
import com.waivera.waivera.navigation.ScheduleRoute
import com.waivera.waivera.navigation.TodayRoute
import com.waivera.waivera.navigation.TopLevelTab
import dagger.hilt.android.testing.HiltAndroidTest
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test
import org.robolectric.Shadows.shadowOf

/**
 * Path 6: the Schedule tab through month, week and day views to an event, the hand-off to the
 * web meeting lobby, the meeting chat, and back.
 */
@HiltAndroidTest
class Path06ScheduleTest : NavTourHarness() {

    @Test
    fun path06_monthWeekDay_eventDetail_join_back() {
        // 13:45 CST in Chicago (the fall-back day): the 14:00 Sprout meeting's join window (30 minutes before) is open.
        world.clock().set(Instant.parse("2026-11-01T19:45:00Z"))
        launch()
        clickTab("Schedule")
        expectStack(TodayRoute, ScheduleRoute)

        click("Month")
        expectStack(TodayRoute, ScheduleRoute)
        shot("path_06_schedule_01_month")
        shotDark("path_06_schedule_01_month")

        click("Week")
        expectStack(TodayRoute, ScheduleRoute)
        shot("path_06_schedule_02_week")

        click("Day")
        expectScreen(SUPERVISION, substring = true)
        expectStack(TodayRoute, ScheduleRoute)
        shot("path_06_schedule_03_day")
        shotDark("path_06_schedule_03_day")

        click(hasClickLabel("Open event") and textOrDescription(SUPERVISION, substring = true))
        expectSelected(TopLevelTab.Schedule)
        expectStack(TodayRoute, ScheduleRoute, CalendarEventRoute(SUPERVISION_ID))
        expectScreen("Event")
        expectScreen("Join meeting")
        shot("path_06_schedule_04_event")
        shotDark("path_06_schedule_04_event")

        click("Join meeting")
        val handedOff = waitForStartedActivity()
        assertEquals(TourCalendarRepository.lobbyUrlFor(SUPERVISION_ID), handedOff)
        // The lobby opens in a Custom Tab over the app; the shell itself does not move.
        expectStack(TodayRoute, ScheduleRoute, CalendarEventRoute(SUPERVISION_ID))
        shot("path_06_schedule_05_after_join_handoff")

        systemBack()
        expectStack(TodayRoute, ScheduleRoute)
        shot("path_06_schedule_06_back_to_schedule")

        systemBack()
        expectSelected(TopLevelTab.Today)
        expectStack(TodayRoute)
    }

    @Test
    fun path06_joinBeforeTheWindow_saysWhenItOpens() {
        launch()
        clickTab("Schedule")
        click("Day")
        click(hasClickLabel("Open event") and textOrDescription(SUPERVISION, substring = true))
        expectStack(TodayRoute, ScheduleRoute, CalendarEventRoute(SUPERVISION_ID))
        expectScreen("Join opens at", substring = true)
        shot("path_06_schedule_07_join_not_yet")
    }

    @Test
    fun path06_eventMeetingChat_pushesConversationOnSchedule() {
        launch()
        clickTab("Schedule")
        click("Day")
        click(hasClickLabel("Open event") and textOrDescription(SUPERVISION, substring = true))
        expectStack(TodayRoute, ScheduleRoute, CalendarEventRoute(SUPERVISION_ID))

        click(textOrDescription("Open meeting chat") or textOrDescription("Message organizer"))
        waitUntil("a conversation over the event") {
            nav.selected == TopLevelTab.Schedule && nav.top is ConversationRoute
        }
        assertEquals(
            listOf(TodayRoute, ScheduleRoute, CalendarEventRoute(SUPERVISION_ID)),
            nav.visibleBackStack.dropLast(1),
        )
        shot("path_06_schedule_08_meeting_chat")

        systemBack()
        expectStack(TodayRoute, ScheduleRoute, CalendarEventRoute(SUPERVISION_ID))
    }

    /** The URL of the newest activity the app started (the Custom Tab or browser). */
    private fun waitForStartedActivity(): String? {
        var url: String? = null
        waitUntil("an activity started for the meeting lobby") {
            url = onActivity { shadowOfActivity(it) }
            url != null
        }
        return url
    }

    private fun shadowOfActivity(activity: Activity): String? = shadowOf(activity).nextStartedActivity?.dataString

    private companion object {
        const val SUPERVISION = "Supervision 1:1"
        const val SUPERVISION_ID = "demo_supervision"
    }
}
