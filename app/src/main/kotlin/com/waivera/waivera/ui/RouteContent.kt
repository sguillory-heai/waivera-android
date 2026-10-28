package com.waivera.waivera.ui

import androidx.compose.runtime.Composable
import com.waivera.waivera.feature.auth.PasswordChangeScreen
import com.waivera.waivera.feature.calendar.CalendarEventScreen
import com.waivera.waivera.feature.calendar.ScheduleScreen
import com.waivera.waivera.feature.chat.ChatListScreen
import com.waivera.waivera.feature.chat.ConversationScreen
import com.waivera.waivera.feature.chat.ThreadScreen
import com.waivera.waivera.feature.profile.BookmarksScreen
import com.waivera.waivera.feature.profile.PeopleScreen
import com.waivera.waivera.feature.profile.PersonDetailScreen
import com.waivera.waivera.feature.profile.ProfileScreen
import com.waivera.waivera.feature.settings.AppearanceScreen
import com.waivera.waivera.feature.settings.NotificationSettingsScreen
import com.waivera.waivera.feature.settings.SettingsScreen
import com.waivera.waivera.feature.today.TodayScreen
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

/**
 * Navigation callbacks shared by every screen. Opening a conversation, person or event pushes on
 * the current tab, so back returns to where the person came from.
 */
class RouteActions(private val shell: ShellActions, val openAccountMenu: () -> Unit) {
    val back: () -> Unit = shell.back
    val openConversation: (Long) -> Unit = { id -> shell.push(ConversationRoute(id)) }
    val openPerson: (String) -> Unit = { id -> shell.push(PersonRoute(id)) }
    val openEvent: (String) -> Unit = { id -> shell.push(CalendarEventRoute(id)) }
    val openSchedule: () -> Unit = { shell.select(TopLevelTab.Schedule) }

    fun openThread(conversationId: Long): (Long) -> Unit = { root -> shell.push(ThreadRoute(conversationId, root)) }

    /**
     * A specific message: thread replies open the thread at the reply (PLAN.md parity fix), others
     * the conversation scrolled to the message. Used by bookmarks and Today's activity rows.
     */
    val openMessage: (Long, Long, Long?) -> Unit = { conversationId, messageId, threadRoot ->
        shell.push(
            if (threadRoot != null) {
                ThreadRoute(conversationId, threadRoot, messageId)
            } else {
                ConversationRoute(conversationId, messageId)
            },
        )
    }

    fun push(route: AppRoute) = shell.push(route)
}

/** Maps a route to its feature's entry composable. */
@Composable
fun RouteContent(route: AppRoute, actions: RouteActions) {
    when (route) {
        TodayRoute -> TodayScreen(
            onAccountClick = actions.openAccountMenu,
            onOpenConversation = actions.openConversation,
            onOpenSchedule = actions.openSchedule,
            onOpenEvent = actions.openEvent,
            onOpenMessage = actions.openMessage,
        )
        ChatListRoute -> ChatListScreen(
            onAccountClick = actions.openAccountMenu,
            onOpenConversation = actions.openConversation,
            onOpenPerson = actions.openPerson,
        )
        ScheduleRoute -> ScheduleScreen(
            onAccountClick = actions.openAccountMenu,
            onOpenEvent = actions.openEvent,
            onOpenConversation = actions.openConversation,
        )
        is ConversationRoute -> ConversationScreen(
            conversationId = route.conversationId,
            highlightMessageId = route.messageId,
            onBack = actions.back,
            onOpenThread = actions.openThread(route.conversationId),
            onOpenPerson = actions.openPerson,
            onOpenEvent = actions.openEvent,
        )
        is ThreadRoute -> ThreadScreen(
            conversationId = route.conversationId,
            rootMessageId = route.rootMessageId,
            highlightMessageId = route.messageId,
            onBack = actions.back,
            onOpenPerson = actions.openPerson,
        )
        is CalendarEventRoute -> CalendarEventScreen(
            eventId = route.eventId,
            onBack = actions.back,
            onOpenConversation = actions.openConversation,
            onOpenPerson = actions.openPerson,
        )
        ProfileRoute -> ProfileScreen(
            onBack = actions.back,
            onOpenPerson = actions.openPerson,
            onOpenSettings = { actions.push(SettingsRoute) },
        )
        PeopleRoute -> PeopleScreen(onBack = actions.back, onOpenPerson = actions.openPerson)
        is PersonRoute -> PersonDetailScreen(
            userId = route.userId,
            onBack = actions.back,
            onOpenConversation = actions.openConversation,
        )
        BookmarksRoute -> BookmarksScreen(onBack = actions.back, onOpenMessage = actions.openMessage)
        SettingsRoute -> SettingsScreen(
            onBack = actions.back,
            onOpenNotificationSettings = { actions.push(NotificationSettingsRoute) },
            onOpenAppearance = { actions.push(AppearanceRoute) },
            onOpenChangePassword = { actions.push(ChangePasswordRoute) },
        )
        NotificationSettingsRoute -> NotificationSettingsScreen(onBack = actions.back)
        AppearanceRoute -> AppearanceScreen(onBack = actions.back)
        // Voluntary flow: back and success both return to Settings; sign-out is not offered here.
        ChangePasswordRoute -> PasswordChangeScreen(onSignOut = {}, onBack = actions.back, onChanged = actions.back)
    }
}
