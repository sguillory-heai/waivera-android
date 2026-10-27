@file:UseSerializers(UtcInstantSerializer::class)

package com.waivera.waivera.core.data.model

import com.waivera.waivera.core.network.dto.AttachmentScanStatus
import com.waivera.waivera.core.network.dto.CallRecordStatus
import com.waivera.waivera.core.network.dto.ChannelType
import com.waivera.waivera.core.network.dto.MessageType
import com.waivera.waivera.core.network.error.WaiveraError
import com.waivera.waivera.core.network.time.UtcInstantSerializer
import java.time.Instant
import kotlin.reflect.KClass
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.json.JsonElement

/** A chat author or directory entry. Only these six fields are trusted on chat payloads (G-04). */
@Serializable
data class Person(
    val id: String,
    val firstName: String? = null,
    val lastName: String? = null,
    val displayName: String? = null,
    val email: String? = null,
    val avatarUrl: String? = null,
) {
    /** Display name, else first + last, else email; empty only when the server sent nothing. */
    val name: String
        get() = displayName?.takeIf(String::isNotBlank)
            ?: listOfNotNull(firstName, lastName).joinToString(" ").trim().takeIf(String::isNotEmpty)
            ?: email.orEmpty()
}

enum class ConversationKind { DIRECT, GROUP, CHANNEL, BOT }

@Serializable
data class ChannelInfo(
    val name: String? = null,
    val topic: String? = null,
    val type: ChannelType = ChannelType.UNKNOWN,
    val ownerId: String? = null,
    val isArchived: Boolean = false,
    val canWrite: Boolean = true,
)

@Serializable
data class MeetingInfo(
    val roomId: String? = null,
    val calendarEventId: String? = null,
    val title: String? = null,
    val startAt: Instant? = null,
)

@Serializable
data class OngoingCall(
    val roomId: String? = null,
    val startedAt: Instant? = null,
    val status: CallRecordStatus = CallRecordStatus.UNKNOWN,
    val callRecordId: Long? = null,
) {
    val isLive: Boolean get() = status.isLive
}

/** The inbox row's last message, merged across endpoints so richer fields are never lost (G-05). */
@Serializable
data class MessagePreview(
    val messageId: Long,
    val authorId: String? = null,
    val authorName: String? = null,
    /** `messageContent`: plain text, or for rich messages whatever the sender stored (TipTap JSON, HTML). */
    val text: String? = null,
    val attachmentName: String? = null,
    val attachmentContentType: String? = null,
    val hasAttachment: Boolean = false,
    val isDeleted: Boolean = false,
    val messageType: MessageType = MessageType.USER,
    val createdAt: Instant? = null,
    /** [Message.CONTENT_FORMAT_PLAIN] or [Message.CONTENT_FORMAT_RICH] (rest.md `contentFormat`). */
    val contentFormat: Int = Message.CONTENT_FORMAT_PLAIN,
    /** The server's plain-text rendering of a rich message (`messagePlaintext`); prefer it over [text]. */
    val plainText: String? = null,
    /** Sent by a bot: the DTO's `isBot`, a `botId`, or a `bot:` author id. */
    val isBot: Boolean = false,
    val botId: String? = null,
    val systemEventType: String? = null,
    /** Set when [systemEventType] is `ticket.update` and the payload is valid. */
    val ticket: TicketUpdate? = null,
)

@Serializable
data class Conversation(
    val id: Long,
    val kind: ConversationKind,
    val participants: List<Person> = emptyList(),
    val channel: ChannelInfo? = null,
    val lastMessage: MessagePreview? = null,
    val lastReadAt: Instant? = null,
    val unreadCount: Int = 0,
    val activeMeeting: MeetingInfo? = null,
    val ongoingCall: OngoingCall? = null,
) {
    val canWrite: Boolean get() = channel?.let { it.canWrite && !it.isArchived } ?: true

    /**
     * Sort key for the inbox: newest activity first. Spec 02 falls back to `lastActivityAt`,
     * `modifiedDate` and `createDate`, which sprout_api's conversation DTOs do not send; the
     * timestamps it does send stand in, and the list breaks ties by id (newer ids are newer chats).
     */
    val activityAt: Instant?
        get() = lastMessage?.createdAt ?: ongoingCall?.startedAt ?: activeMeeting?.startAt

    /** Everyone except [myUserId]; for DM titles and avatars. */
    fun others(myUserId: String?): List<Person> = participants.filter { it.id != myUserId }

    /** Channel name, else the other participants' names; empty when the server sent neither. */
    fun title(myUserId: String?): String =
        channel?.name?.takeIf(String::isNotBlank) ?: others(myUserId).joinToString(", ") { it.name }
}

@Serializable
data class Attachment(
    /** Relative API path; load it through the authenticated image loader (G-15). */
    val url: String,
    val originalName: String? = null,
    val contentType: String? = null,
    val size: Long? = null,
    val scanStatus: AttachmentScanStatus? = null,
    /** Open vocabulary (`image`, `file`, `pdf`, ...); branch on [contentType] instead. */
    val type: String? = null,
) {
    val isImage: Boolean get() = contentType?.startsWith("image/") == true || type == "image"
    val isBlocked: Boolean get() = scanStatus == AttachmentScanStatus.REJECTED
    val isPending: Boolean get() = scanStatus == AttachmentScanStatus.PENDING
}

@Serializable
data class Reaction(val userId: String, val emoji: String)

enum class SendState { SENT, QUEUED, SENDING, FAILED }

/**
 * Why an outbox send failed for good, stored with the outbox row (an exception cannot be
 * persisted or compared). [toError] gives the [WaiveraError] the UI already knows how to explain.
 */
enum class SendFailure(private val type: KClass<out WaiveraError>?, private val error: () -> WaiveraError) {
    NETWORK(WaiveraError.Network::class, { WaiveraError.Network() }),
    TIMEOUT(WaiveraError.Timeout::class, { WaiveraError.Timeout() }),
    SERVER(WaiveraError.Server::class, { WaiveraError.Server(WaiveraError.HTTP_SERVER_ERROR) }),
    RATE_LIMITED(WaiveraError.RateLimited::class, { WaiveraError.RateLimited() }),
    CHANNEL_ARCHIVED(WaiveraError.ChannelArchived::class, { WaiveraError.ChannelArchived() }),
    CHANNEL_READ_ONLY(WaiveraError.ChannelReadOnly::class, { WaiveraError.ChannelReadOnly() }),
    FORBIDDEN(WaiveraError.Forbidden::class, { WaiveraError.Forbidden() }),
    NOT_FOUND(WaiveraError.NotFound::class, { WaiveraError.NotFound() }),
    VALIDATION(WaiveraError.Validation::class, { WaiveraError.Validation() }),
    ATTACHMENT_EXPIRED(WaiveraError.AttachmentExpired::class, { WaiveraError.AttachmentExpired() }),
    ATTACHMENT_REJECTED(WaiveraError.AttachmentRejected::class, { WaiveraError.AttachmentRejected() }),
    ATTACHMENT_TOO_LARGE(WaiveraError.AttachmentTooLarge::class, { WaiveraError.AttachmentTooLarge() }),
    SESSION_EXPIRED(WaiveraError.SessionExpired::class, { WaiveraError.SessionExpired() }),
    NOT_SIGNED_IN(WaiveraError.NotSignedIn::class, { WaiveraError.NotSignedIn() }),
    REALTIME_UNAVAILABLE(WaiveraError.RealtimeUnavailable::class, { WaiveraError.RealtimeUnavailable() }),
    OTHER(null, { WaiveraError.Unexpected() }),
    ;

    fun toError(): WaiveraError = error()

    /** Whether the same send can succeed later without the person changing anything. */
    val isTransient: Boolean get() = toError().isTransient

    companion object {
        /** The stored kind for [error]; anything without its own kind is [OTHER]. */
        fun of(error: WaiveraError): SendFailure = entries.firstOrNull { it.type?.isInstance(error) == true } ?: OTHER
    }
}

@Serializable
data class Message(
    /** Server id, or a negative placeholder for an unsent outbox message. */
    val id: Long,
    val conversationId: Long,
    val clientMessageId: String? = null,
    val author: Person? = null,
    val authorId: String? = null,
    val botId: String? = null,
    /** Null when deleted: render a localised placeholder from [isDeleted] instead (G-11). */
    val text: String? = null,
    val doc: JsonElement? = null,
    val contentFormat: Int = 0,
    val createdAt: Instant? = null,
    val updatedAt: Instant? = null,
    val isEdited: Boolean = false,
    val isDeleted: Boolean = false,
    val isBookmarked: Boolean = false,
    val type: MessageType = MessageType.USER,
    val attachment: Attachment? = null,
    val reactions: List<Reaction> = emptyList(),
    val callRecordId: Long? = null,
    val systemEventType: String? = null,
    val systemEventData: JsonElement? = null,
    val parentMessageId: Long? = null,
    val threadRootMessageId: Long? = null,
    val threadReplyCount: Int = 0,
    val sendState: SendState = SendState.SENT,
    /** The server's plain-text rendering of a rich message (`messagePlaintext`). */
    val plainText: String? = null,
    /** Why the outbox gave up on this message; set only while [sendState] is FAILED. */
    val sendFailure: SendFailure? = null,
    /** 0..1 while the attachment uploads; null when there is nothing uploading. */
    @Transient val uploadProgress: Float? = null,
) {
    /** [sendFailure] as the typed error the UI maps to copy. */
    val sendError: WaiveraError? get() = sendFailure?.toError()

    val isThreadReply: Boolean get() = threadRootMessageId != null && threadRootMessageId != id

    val isPending: Boolean get() = sendState != SendState.SENT

    fun isMine(myUserId: String?): Boolean = myUserId != null && authorId == myUserId

    /** Thread root for navigation: replies open their thread, roots open themselves. */
    val navigationRootId: Long? get() = threadRootMessageId ?: if (threadReplyCount > 0) id else null

    companion object {
        const val CONTENT_FORMAT_PLAIN = 0
        const val CONTENT_FORMAT_RICH = 1
    }
}

/** Messages newest last, plus whether older pages exist (G-06 stop rules). */
data class MessageTimeline(
    val messages: List<Message> = emptyList(),
    val hasOlder: Boolean = true,
    val isLoadingOlder: Boolean = false,
)

/** A thread: [root] (null until loaded) and its replies, oldest first. */
data class MessageThread(val root: Message? = null, val replies: List<Message> = emptyList())

@Serializable
data class ActivityItem(
    val id: Long,
    val type: com.waivera.waivera.core.network.dto.ChatActivityType,
    val actorUserId: String? = null,
    val actorName: String? = null,
    val conversationId: Long,
    val messageId: Long,
    val threadRootMessageId: Long? = null,
    val reactionType: String? = null,
    val summary: String? = null,
    val createdAt: Instant? = null,
    val read: Boolean = false,
)

data class ActivityFeed(val items: List<ActivityItem> = emptyList(), val hasMore: Boolean = true) {
    val unreadCount: Int get() = items.count { !it.read }
}

/** A bookmarked message plus where tapping it should go (threads open at their root). */
data class Bookmark(val message: Message) {
    val conversationId: Long get() = message.conversationId
    val messageId: Long get() = message.id
    val threadRootMessageId: Long? get() = message.threadRootMessageId?.takeIf { it != message.id }
}

/** An attachment picked for sending; [open] is called again on retry, so it must be re-readable. */
class AttachmentSource(
    val fileName: String,
    val contentType: String,
    val size: Long?,
    val open: () -> java.io.InputStream,
)
