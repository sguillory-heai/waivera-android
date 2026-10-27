package com.waivera.waivera.core.notifications.deeplink

/**
 * Every place a notification or a `waivera://` link can open. The app maps each target to a
 * back stack; ids are validated here so the app never sees a zero, negative or malformed id.
 */
sealed interface DeepLinkTarget {
    data object Today : DeepLinkTarget

    /** The conversation list. Also the landing place for unknown push types. */
    data object Inbox : DeepLinkTarget

    /** A conversation, optionally scrolled to and highlighting [messageId]. */
    data class Conversation(val conversationId: Long, val messageId: Long? = null) : DeepLinkTarget

    /** A thread in [conversationId], optionally highlighting the reply [messageId]. */
    data class Thread(val conversationId: Long, val rootMessageId: Long, val messageId: Long? = null) : DeepLinkTarget

    data object Schedule : DeepLinkTarget

    /** A calendar event detail. Calendar event ids are opaque strings (for example `evt_8f2b19c4`). */
    data class CalendarEvent(val eventId: String) : DeepLinkTarget

    data object Profile : DeepLinkTarget

    data object People : DeepLinkTarget

    /** A person's profile. [userId] is the backend user id (string or number on the wire). */
    data class Person(val userId: String) : DeepLinkTarget

    data object Bookmarks : DeepLinkTarget

    data object Settings : DeepLinkTarget

    data object NotificationSettings : DeepLinkTarget
}
