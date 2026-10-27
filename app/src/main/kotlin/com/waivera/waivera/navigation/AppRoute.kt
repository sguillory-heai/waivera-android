package com.waivera.waivera.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Every destination inside the signed-in shell. Routes are type-safe keys for Navigation 3 and
 * are serialized into saved state, so they hold ids only, never loaded data.
 */
@Serializable
sealed interface AppRoute : NavKey

/** The three bottom tabs, in display order. */
@Serializable
enum class TopLevelTab(val root: AppRoute) {
    Today(TodayRoute),
    Chat(ChatListRoute),
    Schedule(ScheduleRoute),
}

@Serializable
data object TodayRoute : AppRoute

@Serializable
data object ChatListRoute : AppRoute

@Serializable
data object ScheduleRoute : AppRoute

/** [messageId] scrolls to and highlights one message (push, search, bookmark). */
@Serializable
data class ConversationRoute(val conversationId: Long, val messageId: Long? = null) : AppRoute

@Serializable
data class ThreadRoute(val conversationId: Long, val rootMessageId: Long, val messageId: Long? = null) : AppRoute

/** Calendar event ids are opaque strings. */
@Serializable
data class CalendarEventRoute(val eventId: String) : AppRoute

@Serializable
data object ProfileRoute : AppRoute

@Serializable
data object PeopleRoute : AppRoute

/** [userId] is the backend user id, the only identity key (never the Firebase uid). */
@Serializable
data class PersonRoute(val userId: String) : AppRoute

@Serializable
data object BookmarksRoute : AppRoute

@Serializable
data object SettingsRoute : AppRoute

@Serializable
data object NotificationSettingsRoute : AppRoute

/** The voluntary password change, opened from Settings → Security. */
@Serializable
data object ChangePasswordRoute : AppRoute

@Serializable
data object AppearanceRoute : AppRoute

/** True for the three tab roots, where the bottom bar shows. */
val AppRoute.isTabRoot: Boolean
    get() = TopLevelTab.entries.any { it.root == this }

/** The conversation and thread on screen when this route is on top, for push suppression. */
val AppRoute.visibleChat: Pair<Long, Long?>?
    get() = when (this) {
        is ConversationRoute -> conversationId to null
        is ThreadRoute -> conversationId to rootMessageId
        else -> null
    }

/** The message a route scrolls to and highlights, if any. */
val AppRoute.highlightedMessageId: Long?
    get() = when (this) {
        is ConversationRoute -> messageId
        is ThreadRoute -> messageId
        else -> null
    }

/**
 * Identity of the screen ignoring the highlight. Used as the Navigation 3 content key so a
 * push that only moves the highlight keeps the screen's saved state (scroll, draft).
 */
val AppRoute.contentKey: String
    get() = when (this) {
        is ConversationRoute -> "conversation:$conversationId"
        is ThreadRoute -> "thread:$conversationId:$rootMessageId"
        is CalendarEventRoute -> "event:$eventId"
        is PersonRoute -> "person:$userId"
        else -> toString()
    }
