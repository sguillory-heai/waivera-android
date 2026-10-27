@file:OptIn(ExperimentalSerializationApi::class)
@file:UseSerializers(UtcInstantSerializer::class)

package com.waivera.waivera.core.network.dto

import com.waivera.waivera.core.network.json.JsonDocSerializer
import com.waivera.waivera.core.network.json.StringOrNumberSerializer
import com.waivera.waivera.core.network.time.UtcInstantSerializer
import java.time.Instant
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNames

/**
 * `dto/Response/UserInfoResponse.cs`, also used for the fat `MessageAuthorResponse` and the EF
 * `User` entity that some endpoints leak as `author` (gaps.md G-04): only these fields are read,
 * and `joinDate`/`organizationId`/`isActive` on a chat author are never trusted.
 */
@Serializable
data class UserInfoResponse(
    @Serializable(with = StringOrNumberSerializer::class) val id: String? = null,
    val firstName: String? = null,
    val lastName: String? = null,
    val email: String? = null,
    val displayName: String? = null,
    val scId: String? = null,
    val isParticipant: Boolean = false,
    val isExternal: Boolean = false,
    val deactivated: Boolean = false,
    @JsonNames("avatarURL") val avatarUrl: String? = null,
    val participantCount: Int = 0,
    val employeeRole: String? = null,
)

/** `dto/Response/ReactionResponse.cs`. `id`/`fullName` are unknown for reactions rebuilt from hub events. */
@Serializable
data class ReactionResponse(
    val id: Long? = null,
    @Serializable(with = StringOrNumberSerializer::class) val userId: String? = null,
    val fullName: String? = null,
    val reactionType: String? = null,
)

/**
 * The union of sprout_api's three message DTOs: `MessageSendResponse` (pagination, thread, send,
 * hub `ReceiveMessage`), `MessageResponse` (conversation `lastMessage`, bookmarks) and
 * `LastMessageDto` (`/api/chat/all`, which has `userId`/`botId` instead of `author` and a
 * string-encoded `messageDoc`). One model keeps one name per field (rest.md §0.3).
 */
@Serializable
data class MessageSendResponse(
    val id: Long = 0,
    val clientMessageId: String? = null,
    val author: UserInfoResponse? = null,
    @Serializable(with = StringOrNumberSerializer::class) val userId: String? = null,
    @Serializable(with = StringOrNumberSerializer::class) val botId: String? = null,
    val conversationId: Long = 0,
    val messageContent: String? = null,
    val createDate: Instant? = null,
    val attachmentUrl: String? = null,
    val attachmentType: String? = null,
    val attachmentOriginalName: String? = null,
    val attachmentContentType: String? = null,
    val attachmentSize: Long? = null,
    val attachmentScanStatus: AttachmentScanStatus? = null,
    val messageType: MessageType = MessageType.USER,
    val reactions: List<ReactionResponse> = emptyList(),
    val callRecordId: Long? = null,
    val isBot: Boolean = false,
    val systemEventType: String? = null,
    val systemEventData: JsonElement? = null,
    val isEdited: Boolean = false,
    val updateDate: Instant? = null,
    val isDeleted: Boolean = false,
    val deletedAt: Instant? = null,
    val isBookmarked: Boolean = false,
    val contentFormat: Int = 0,
    @Serializable(with = JsonDocSerializer::class) val messageDoc: JsonElement? = null,
    val messagePlaintext: String? = null,
    val parentMessageId: Long? = null,
    val threadRootMessageId: Long? = null,
    val threadReplyCount: Int = 0,
)

/** `dto/Response/MessageResponse.cs`; same wire fields as [MessageSendResponse]. */
typealias MessageResponse = MessageSendResponse

/** `dto/Response/ConversationResponse.cs` `LastMessageDto`; same wire fields as [MessageSendResponse]. */
typealias LastMessageDto = MessageSendResponse

/** `dto/Response/Team/TeamMemberDto.cs` ChannelDto. `channelType` is a string over REST, an int on the hub. */
@Serializable
data class ChannelDto(
    @Serializable(with = StringOrNumberSerializer::class) val channelId: String? = null,
    val conversationId: Long = 0,
    val name: String? = null,
    val topic: String? = null,
    val channelType: ChannelType = ChannelType.UNKNOWN,
    val associatedEntityId: String? = null,
    val ownerId: String? = null,
    val isArchived: Boolean = false,
    val canWrite: Boolean = false,
)

/** `dto/Response/videocall/CreateMeetingRoomResponse.cs` ConversationMeetingRoomDto. */
@Serializable
data class ConversationMeetingRoomDto(
    @Serializable(with = StringOrNumberSerializer::class) val id: String? = null,
    val roomId: String? = null,
    val calendarEventId: String? = null,
    val createdAt: Instant? = null,
    val startDate: Instant? = null,
    val title: String? = null,
)

/** OngoingCallDto. `status` is never [CallRecordStatus.END] on REST (filtered server-side). */
@Serializable
data class OngoingCallDto(
    val roomId: String? = null,
    val startTime: Instant? = null,
    val status: CallRecordStatus = CallRecordStatus.UNKNOWN,
    val callRecordId: Long? = null,
)

/**
 * `dto/Response/ConversationResponse.cs`, from `/my-conversations`, `/conversation/{id}` and hub
 * `ConversationCreated`.
 */
@Serializable
data class ConversationResponse(
    val id: Long = 0,
    val isBotDirect: Boolean = false,
    val participants: List<UserInfoResponse> = emptyList(),
    val lastMessage: MessageResponse? = null,
    val lastReadAt: Instant? = null,
    val unreadCount: Int = 0,
    val channel: ChannelDto? = null,
    val activeMeeting: ConversationMeetingRoomDto? = null,
    val ongoingCall: OngoingCallDto? = null,
)

/** `dto/Response/ChatResponeDto/UnifiedConversationResponse.cs`, from `/api/chat/all`. Keyed by `conversationId`. */
@Serializable
data class UnifiedConversationResponse(
    val conversationId: Long = 0,
    val isBotDirect: Boolean = false,
    val participants: List<UserInfoResponse> = emptyList(),
    val lastReadAt: Instant? = null,
    val unreadCount: Int = 0,
    val lastMessage: LastMessageDto? = null,
    val isChannel: Boolean = false,
    val channel: ChannelDto? = null,
    val activeMeeting: ConversationMeetingRoomDto? = null,
    val ongoingCall: OngoingCallDto? = null,
)

/** `SendMessageRequest`. Either [conversationId] > 0 or [recipientId]. [clientMessageId] is the idempotency key. */
@Serializable
data class SendMessageRequest(
    val conversationId: Long? = null,
    val clientMessageId: String? = null,
    val recipientId: String? = null,
    val text: String? = null,
    val attachmentUrl: String? = null,
    val doc: JsonElement? = null,
    val parentMessageId: Long? = null,
)

/** Body of `POST /api/chat/create-conversation`; the caller is added by the server. */
@Serializable
data class CreateConversationRequest(val participantIds: List<String>)

/** `ChatReadThroughRequest`. */
@Serializable
data class ChatReadThroughRequest(val lastVisibleMessageId: Long, val threadRootMessageId: Long? = null)

/** Read-through response and hub `ReceiveChatReadState`. `conversationId == 0` means read-all. */
@Serializable
data class ChatReadStateDto(
    val conversationId: Long = 0,
    val lastVisibleMessageId: Long = 0,
    val threadRootMessageId: Long? = null,
    val readActivityIds: List<Long> = emptyList(),
    val readAt: Instant? = null,
)

/** `dto/Response/ChatActivityResponse.cs`; also the hub `ReceiveChatActivity` payload. */
@Serializable
data class ChatActivityResponse(
    val id: Long = 0,
    val activityType: ChatActivityType = ChatActivityType.UNKNOWN,
    @Serializable(with = StringOrNumberSerializer::class) val actorUserId: String? = null,
    val actorDisplayName: String? = null,
    val conversationId: Long = 0,
    val messageId: Long = 0,
    val threadRootMessageId: Long? = null,
    val reactionType: String? = null,
    val summary: String? = null,
    val createdAt: Instant? = null,
    val read: Boolean = false,
)

/** `EditMessageRequest`. */
@Serializable
data class EditMessageRequest(val newContent: String? = null, val doc: JsonElement? = null)

/** Response of `POST /api/chat/upload-attachment`. [documentUrl] is a storage key to pass back on send. */
@Serializable
data class UploadAttachmentResponse(
    val documentUrl: String? = null,
    val uploadId: String? = null,
    val originalName: String? = null,
    val contentType: String? = null,
    val size: Long? = null,
    val scanStatus: AttachmentScanStatus? = null,
    val sha256: String? = null,
)

/** Hub `ReceiveMessageUpdate`. Note `newContent`, not `messageContent`. */
@Serializable
data class MessageUpdateDto(
    val messageId: Long = 0,
    val conversationId: Long = 0,
    val newContent: String? = null,
    val isEdited: Boolean = true,
    val updateDate: Instant? = null,
    val contentFormat: Int = 0,
    @Serializable(with = JsonDocSerializer::class) val messageDoc: JsonElement? = null,
    val messagePlaintext: String? = null,
)

/** Hub `ReceiveMessageDelete`. */
@Serializable
data class MessageDeleteDto(
    val messageId: Long = 0,
    val conversationId: Long = 0,
    val deletedAt: Instant? = null,
    val isDeleted: Boolean = true,
)

/** Hub `ReceiveBookmarkUpdate`; both fields are null when the payload shape is not recognised. */
@Serializable
data class BookmarkUpdateDto(val messageId: Long? = null, val isBookmarked: Boolean? = null)

/** Hub `ConversationCallStateChanged` (chat hub). */
@Serializable
data class ConversationCallStateDto(
    val conversationId: Long = 0,
    val roomId: String? = null,
    val status: CallRecordStatus = CallRecordStatus.UNKNOWN,
    val startTime: Instant? = null,
)

/** `dto/Request/VideoDtoObjects.cs` RemoveAdHocFromConversationDto. */
@Serializable
data class RemoveAdHocFromConversationDto(val conversationId: Long = 0, val roomId: String? = null)

/** `EventNotificationResponse` from hub `ReceiveNotification` and `receivenotification_web`. */
@Serializable
data class EventNotificationResponse(
    val id: Long = 0,
    val type: String? = null,
    val status: String? = null,
    val title: String? = null,
    val message: String? = null,
    val notifies: UserInfoResponse? = null,
    val allowApproval: Boolean? = null,
    val actionLabel: String? = null,
    val actionUrl: String? = null,
    val createdAt: Instant? = null,
    val read: Boolean = false,
    val createdByUser: JsonElement? = null,
    val notificationResources: JsonElement? = null,
)
