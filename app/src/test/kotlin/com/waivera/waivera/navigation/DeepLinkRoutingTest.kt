package com.waivera.waivera.navigation

import com.waivera.waivera.core.notifications.deeplink.DeepLinkTarget
import com.waivera.waivera.core.notifications.deeplink.DeepLinkUris
import org.junit.Assert.assertEquals
import org.junit.Test

class DeepLinkRoutingTest {
    private fun visible(target: DeepLinkTarget) = NavState().open(target).visibleBackStack

    @Test
    fun everyTargetHasAStack() {
        val expected = mapOf(
            DeepLinkTarget.Today to listOf<AppRoute>(TodayRoute),
            DeepLinkTarget.Inbox to listOf(TodayRoute, ChatListRoute),
            DeepLinkTarget.Conversation(42, 9) to listOf(TodayRoute, ChatListRoute, ConversationRoute(42, 9)),
            DeepLinkTarget.Thread(42, 7, 9) to listOf(
                TodayRoute,
                ChatListRoute,
                ConversationRoute(42),
                ThreadRoute(42, 7, 9),
            ),
            DeepLinkTarget.Schedule to listOf(TodayRoute, ScheduleRoute),
            DeepLinkTarget.CalendarEvent("evt_1") to listOf(TodayRoute, ScheduleRoute, CalendarEventRoute("evt_1")),
            DeepLinkTarget.Profile to listOf(TodayRoute, ProfileRoute),
            DeepLinkTarget.People to listOf(TodayRoute, PeopleRoute),
            DeepLinkTarget.Person("u-1") to listOf(TodayRoute, PeopleRoute, PersonRoute("u-1")),
            DeepLinkTarget.Bookmarks to listOf(TodayRoute, BookmarksRoute),
            DeepLinkTarget.Settings to listOf(TodayRoute, SettingsRoute),
            DeepLinkTarget.NotificationSettings to listOf(TodayRoute, SettingsRoute, NotificationSettingsRoute),
        )
        expected.forEach { (target, stack) -> assertEquals(target.toString(), stack, visible(target)) }
    }

    @Test
    fun uriRoundTripsLandOnTheSameStack() {
        val targets = listOf(
            DeepLinkTarget.Conversation(42, 9),
            DeepLinkTarget.Thread(42, 7),
            DeepLinkTarget.CalendarEvent("evt_8f2b19c4"),
            DeepLinkTarget.Person("u-1"),
            DeepLinkTarget.NotificationSettings,
        )
        targets.forEach { target ->
            val parsed = requireNotNull(DeepLinkUris.parse(DeepLinkUris.toUri(target))) { target.toString() }
            assertEquals(visible(target), visible(parsed))
        }
    }
}
