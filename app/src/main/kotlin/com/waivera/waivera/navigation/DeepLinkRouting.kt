package com.waivera.waivera.navigation

import com.waivera.waivera.core.notifications.deeplink.DeepLinkTarget

/** Maps every deep-link target to the tab it belongs to and the stack that leads to it. */
object DeepLinkRouting {
    fun stackFor(target: DeepLinkTarget): Pair<TopLevelTab, List<AppRoute>> = when (target) {
        DeepLinkTarget.Today -> TopLevelTab.Today to listOf(TodayRoute)
        DeepLinkTarget.Inbox -> TopLevelTab.Chat to listOf(ChatListRoute)
        is DeepLinkTarget.Conversation -> TopLevelTab.Chat to listOf(
            ChatListRoute,
            ConversationRoute(target.conversationId, target.messageId),
        )
        is DeepLinkTarget.Thread -> TopLevelTab.Chat to listOf(
            ChatListRoute,
            ConversationRoute(target.conversationId),
            ThreadRoute(target.conversationId, target.rootMessageId, target.messageId),
        )
        DeepLinkTarget.Schedule -> TopLevelTab.Schedule to listOf(ScheduleRoute)
        is DeepLinkTarget.CalendarEvent ->
            TopLevelTab.Schedule to listOf(ScheduleRoute, CalendarEventRoute(target.eventId))
        DeepLinkTarget.Profile -> TopLevelTab.Today to listOf(TodayRoute, ProfileRoute)
        DeepLinkTarget.People -> TopLevelTab.Today to listOf(TodayRoute, PeopleRoute)
        is DeepLinkTarget.Person -> TopLevelTab.Today to listOf(TodayRoute, PeopleRoute, PersonRoute(target.userId))
        DeepLinkTarget.Bookmarks -> TopLevelTab.Today to listOf(TodayRoute, BookmarksRoute)
        DeepLinkTarget.Settings -> TopLevelTab.Today to listOf(TodayRoute, SettingsRoute)
        DeepLinkTarget.NotificationSettings -> TopLevelTab.Today to listOf(
            TodayRoute,
            SettingsRoute,
            NotificationSettingsRoute,
        )
    }
}
