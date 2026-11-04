package com.waivera.waivera.core.notifications.payload

import com.waivera.waivera.core.notifications.deeplink.DeepLinkUris
import java.time.Instant

/**
 * Decodes FCM data maps per `spec/contract/push.md`.
 *
 * Rules: branch on `type` first (modern, lowercase keys); fall back to `NotificationType`
 * (legacy, PascalCase). Keys are matched case-insensitively and never assumed camelCase. All
 * values are strings; ids are decimal strings and must be positive. Title and body come from
 * the data map (Android chat push) or, for legacy pushes, from the `notification` block.
 */
object PushPayloadParser {

    /**
     * @return the message, or null when the payload is a chat push (or a read sync) without a
     *   usable id (nothing to show and nowhere to go).
     */
    fun parse(
        data: Map<
            String,
            String,
            >,
        notificationTitle: String? = null,
        notificationBody: String? = null,
    ): PushMessage? {
        val fields = CaseInsensitiveFields(data)
        val title = fields["title"] ?: notificationTitle?.takeIf(String::isNotBlank)
        val body = fields["body"] ?: notificationBody?.takeIf(String::isNotBlank)
        val type = fields["type"]
        if (type != null) {
            return when (type.lowercase()) {
                TYPE_CHAT -> parseChat(fields, title, body)
                TYPE_NOTIFICATION -> parseNotice(fields, title, body)
                TYPE_CHAT_READ -> DeepLinkUris.positiveId(fields["conversationId"])?.let { id ->
                    PushMessage.ChatRead(
                        conversationId = id,
                        threadRootMessageId = DeepLinkUris.positiveId(fields["threadRootMessageId"]),
                        readAt = fields["readAt"]?.let { runCatching { Instant.parse(it) }.getOrNull() },
                    )
                }
                TYPE_NOTIFICATION_READ -> parseNoticeRead(fields)
                else -> PushMessage.Unknown(type = type, title = title, body = body)
            }
        }
        return parseLegacy(fields, title, body)
    }

    private fun parseLegacy(fields: CaseInsensitiveFields, title: String?, body: String?): PushMessage? {
        val legacyType = fields["NotificationType"]
        return when (legacyType?.lowercase()) {
            LEGACY_MEETING -> PushMessage.Meeting(
                eventId = fields["CalenderEventId"]?.takeIf(OpaqueId::matches),
                title = title ?: fields["MainTitle"],
                body = body,
            )
            LEGACY_CALL -> PushMessage.IncomingCall(
                roomId = fields["roomId"]?.takeIf(OpaqueId::matches),
                conversationId = DeepLinkUris.positiveId(fields["conversationId"]),
                callerName = fields["callerDisplayName"],
                title = title ?: fields["MainTitle"],
                body = body,
            )
            LEGACY_SURVEY -> PushMessage.Survey(
                instanceId = fields["InstanceId"]?.takeIf(OpaqueId::matches),
                title = title ?: fields["MainTitle"],
                body = body,
            )
            // No type at all but a conversation to open: show it as a chat rather than drop it.
            null -> if (DeepLinkUris.positiveId(fields["conversationId"]) != null) {
                parseChat(fields, title, body)
            } else {
                PushMessage.Unknown(type = null, title = title, body = body)
            }
            else -> PushMessage.Unknown(type = legacyType, title = title, body = body)
        }
    }

    private fun parseChat(fields: CaseInsensitiveFields, title: String?, body: String?): PushMessage.Chat? {
        // data.url ("/chats/7") is only a fallback; the id fields are the contract.
        val conversationId = DeepLinkUris.positiveId(fields["conversationId"])
            ?: conversationIdFromUrl(fields["url"])
            ?: return null
        return PushMessage.Chat(
            conversationId = conversationId,
            messageId = DeepLinkUris.positiveId(fields["messageId"]),
            threadRootMessageId = DeepLinkUris.positiveId(fields["threadRootMessageId"]),
            title = title,
            body = body,
            senderId = fields["senderId"]?.takeIf(OpaqueId::matches),
        )
    }

    private fun parseNotice(fields: CaseInsensitiveFields, title: String?, body: String?): PushMessage {
        val id = fields["notificationId"]?.takeIf(OpaqueId::matches)
            ?: return PushMessage.Unknown(type = TYPE_NOTIFICATION, title = title, body = body)
        val taskId = fields["resourceId"]
            ?.takeIf { fields["resourceType"].equals("task", ignoreCase = true) }
            ?.let(DeepLinkUris::positiveId)
            ?.toString()
        return PushMessage.Notice(notificationId = id, taskId = taskId, title = title, body = body)
    }

    /** One notification by id, or all of them with `all: "true"`; anything else is unusable. */
    private fun parseNoticeRead(fields: CaseInsensitiveFields): PushMessage.NoticeRead? {
        val id = fields["notificationId"]?.takeIf(OpaqueId::matches)
        return when {
            id != null -> PushMessage.NoticeRead(id)
            fields["all"].equals("true", ignoreCase = true) -> PushMessage.NoticeRead(null)
            else -> null
        }
    }

    private fun conversationIdFromUrl(url: String?): Long? =
        url?.let(ChatUrl::matchEntire)?.groupValues?.get(1)?.let(DeepLinkUris::positiveId)

    /** Legacy payloads send empty strings for absent values; treat blank as absent. */
    private class CaseInsensitiveFields(data: Map<String, String>) {
        // Read each key and value while iterating: FCM hands over an android.util.ArrayMap whose
        // entry iterator reuses one entry object, so keeping entries leaves only the last field.
        private val values: Map<String, String> = buildMap {
            data.forEach { (key, value) -> if (value.isNotBlank()) put(key.lowercase(), value.trim()) }
        }

        operator fun get(key: String): String? = values[key.lowercase()]
    }

    private const val TYPE_CHAT = "chat"
    private const val TYPE_NOTIFICATION = "notification"
    private const val TYPE_CHAT_READ = "chat.read"
    private const val TYPE_NOTIFICATION_READ = "notification.read"
    private const val LEGACY_MEETING = "meeting"
    private const val LEGACY_CALL = "call"
    private const val LEGACY_SURVEY = "survey"
    private val ChatUrl = Regex("^/chats/(\\d+)/?$")
    private val OpaqueId = Regex("^[A-Za-z0-9_.:-]{1,128}$")
}
