package com.waivera.waivera.core.notifications.deeplink

import java.net.URI
import java.net.URISyntaxException
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * The `waivera://` URI scheme. Notifications build their tap intents with [toUri], and the app
 * parses incoming VIEW intents with [parse]. Anything that does not match a known shape exactly
 * parses to null, so external links cannot reach arbitrary screens or pass unchecked ids.
 *
 * ```
 * waivera://today
 * waivera://chats                                   inbox
 * waivera://chats/{conversationId}?messageId={id}
 * waivera://chats/{conversationId}/thread/{rootMessageId}?messageId={id}
 * waivera://schedule                                (waivera://calendar is accepted too)
 * waivera://calendar/{eventId}
 * waivera://profile
 * waivera://people
 * waivera://people/{userId}
 * waivera://bookmarks
 * waivera://settings
 * waivera://settings/notifications
 * ```
 */
// One codec for every route; splitting it would scatter the URI grammar across files.
@Suppress("TooManyFunctions")
object DeepLinkUris {
    const val SCHEME = "waivera"
    private const val QUERY_MESSAGE_ID = "messageId"
    private const val MAX_URI_LENGTH = 512
    private val OpaqueId = Regex("^[A-Za-z0-9_.:-]{1,128}$")

    fun toUri(target: DeepLinkTarget): String = when (target) {
        DeepLinkTarget.Today -> "$SCHEME://today"
        DeepLinkTarget.Inbox -> "$SCHEME://chats"
        is DeepLinkTarget.Conversation -> "$SCHEME://chats/${target.conversationId}" + messageQuery(target.messageId)
        is DeepLinkTarget.Thread ->
            "$SCHEME://chats/${target.conversationId}/thread/${target.rootMessageId}" + messageQuery(target.messageId)
        DeepLinkTarget.Schedule -> "$SCHEME://schedule"
        is DeepLinkTarget.CalendarEvent -> "$SCHEME://calendar/${encode(target.eventId)}"
        DeepLinkTarget.Profile -> "$SCHEME://profile"
        DeepLinkTarget.People -> "$SCHEME://people"
        is DeepLinkTarget.Person -> "$SCHEME://people/${encode(target.userId)}"
        DeepLinkTarget.Bookmarks -> "$SCHEME://bookmarks"
        DeepLinkTarget.Settings -> "$SCHEME://settings"
        DeepLinkTarget.NotificationSettings -> "$SCHEME://settings/notifications"
    }

    fun parse(uri: String?): DeepLinkTarget? {
        val parsed = uri?.takeIf { it.isNotBlank() && it.length <= MAX_URI_LENGTH }
            ?.let(::uriOrNull)
            ?.takeIf { SCHEME.equals(it.scheme, ignoreCase = true) }
            ?: return null
        val segments = parsed.rawPath.orEmpty().split('/').filter(String::isNotEmpty).map(::decode)
        val messageId = queryParameter(parsed.rawQuery, QUERY_MESSAGE_ID)?.let(::positiveId)
        return when (parsed.host?.lowercase()) {
            "today" -> DeepLinkTarget.Today.takeIf { segments.isEmpty() }
            "chats" -> parseChats(segments, messageId)
            "schedule" -> DeepLinkTarget.Schedule.takeIf { segments.isEmpty() }
            "calendar" -> parseCalendar(segments)
            "profile" -> DeepLinkTarget.Profile.takeIf { segments.isEmpty() }
            "people" -> parsePeople(segments)
            "bookmarks" -> DeepLinkTarget.Bookmarks.takeIf { segments.isEmpty() }
            "settings" -> parseSettings(segments)
            else -> null
        }
    }

    private fun uriOrNull(raw: String): URI? = try {
        URI(raw)
    } catch (ignored: URISyntaxException) {
        null
    }

    /** A positive decimal id, as the API sends ids in push data and as the app writes them in URIs. */
    fun positiveId(raw: String?): Long? = raw?.trim()?.toLongOrNull()?.takeIf { it > 0 }

    private fun parseChats(segments: List<String>, messageId: Long?): DeepLinkTarget? = when {
        segments.isEmpty() -> DeepLinkTarget.Inbox
        segments.size == 1 -> positiveId(segments[0])?.let { DeepLinkTarget.Conversation(it, messageId) }
        segments.size == THREAD_SEGMENTS && segments[1] == "thread" -> {
            val conversationId = positiveId(segments[0])
            val rootId = positiveId(segments[2])
            if (conversationId != null && rootId != null) {
                DeepLinkTarget.Thread(conversationId, rootId, messageId)
            } else {
                null
            }
        }
        else -> null
    }

    private fun parseCalendar(segments: List<String>): DeepLinkTarget? = when (segments.size) {
        0 -> DeepLinkTarget.Schedule
        1 -> segments[0].takeIf(OpaqueId::matches)?.let(DeepLinkTarget::CalendarEvent)
        else -> null
    }

    private fun parsePeople(segments: List<String>): DeepLinkTarget? = when (segments.size) {
        0 -> DeepLinkTarget.People
        1 -> segments[0].takeIf(OpaqueId::matches)?.let(DeepLinkTarget::Person)
        else -> null
    }

    private fun parseSettings(segments: List<String>): DeepLinkTarget? = when {
        segments.isEmpty() -> DeepLinkTarget.Settings
        segments == listOf("notifications") -> DeepLinkTarget.NotificationSettings
        else -> null
    }

    private fun queryParameter(rawQuery: String?, name: String): String? =
        rawQuery?.split('&')?.firstNotNullOfOrNull { pair ->
            val key = pair.substringBefore('=')
            if (decode(key) == name) decode(pair.substringAfter('=', missingDelimiterValue = "")) else null
        }

    private fun messageQuery(messageId: Long?): String = if (messageId != null) "?$QUERY_MESSAGE_ID=$messageId" else ""

    private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())

    private fun decode(value: String): String = try {
        URLDecoder.decode(value, Charsets.UTF_8.name())
    } catch (ignored: IllegalArgumentException) {
        ""
    }

    private const val THREAD_SEGMENTS = 3
}
