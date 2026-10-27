package com.waivera.waivera.core.realtime

import com.waivera.waivera.core.network.dto.AttentionReason
import com.waivera.waivera.core.network.dto.BookmarkUpdateDto
import com.waivera.waivera.core.network.dto.CallRejectedDto
import com.waivera.waivera.core.network.dto.ChatActivityResponse
import com.waivera.waivera.core.network.dto.ChatReadStateDto
import com.waivera.waivera.core.network.dto.ConversationCallStateDto
import com.waivera.waivera.core.network.dto.ConversationResponse
import com.waivera.waivera.core.network.dto.EventNotificationResponse
import com.waivera.waivera.core.network.dto.MessageDeleteDto
import com.waivera.waivera.core.network.dto.MessageSendResponse
import com.waivera.waivera.core.network.dto.MessageUpdateDto
import com.waivera.waivera.core.network.dto.ReactionAction
import com.waivera.waivera.core.network.dto.ReceiveGroupCallInviteResponse
import com.waivera.waivera.core.network.dto.RemoveAdHocFromConversationDto
import java.time.Instant
import kotlinx.serialization.json.JsonElement

/** A decoded server event from the chat hub (hub.md §3). Cache writes belong in repositories. */
sealed interface HubEvent {
    /** To `conversation_{id}`; the sender gets its own echo. */
    data class MessageReceived(val message: MessageSendResponse) : HubEvent

    /** To every participant except the sender, non-thread messages only: bump the inbox row. */
    data class ConversationListMessage(val message: MessageSendResponse) : HubEvent

    /** The authoritative "alert the user" signal (gaps.md G-09). */
    data class ChatAttention(val message: MessageSendResponse, val reason: AttentionReason) : HubEvent

    data class ChatActivity(val activity: ChatActivityResponse) : HubEvent

    data class ChatActivityRemoved(val activityId: Long) : HubEvent

    /** Multi-device read sync for the caller; `conversationId == 0` means read-all. */
    data class ChatReadState(val state: ChatReadStateDto) : HubEvent

    /** No server expiry: the receiver times typing out locally. */
    data class TypingStatus(val conversationId: Long, val userId: String, val isTyping: Boolean) : HubEvent

    data class ReadReceipt(val conversationId: Long, val userId: String, val lastReadAt: Instant?) : HubEvent

    /** `reactionType` is null exactly when [action] is REMOVED. One reaction per user per message. */
    data class Reaction(
        val conversationId: Long,
        val messageId: Long,
        val userId: String,
        val reactionType: String?,
        val action: ReactionAction,
    ) : HubEvent

    /** Own devices only. Both fields null when the payload shape was not recognised. */
    data class BookmarkUpdate(val update: BookmarkUpdateDto) : HubEvent

    data class MessageUpdated(val update: MessageUpdateDto) : HubEvent

    data class MessageDeleted(val delete: MessageDeleteDto) : HubEvent

    /** The only event carrying ChannelDto, so the one where `channelType` is an integer. */
    data class ConversationCreated(val conversation: ConversationResponse) : HubEvent

    data class CallStateChanged(val state: ConversationCallStateDto) : HubEvent

    data class AdHocRemoved(val removal: RemoveAdHocFromConversationDto) : HubEvent

    /** `ReceiveNotification` ([isWebFeed] false) or `receivenotification_web` ([isWebFeed] true). */
    data class Notification(val notification: EventNotificationResponse, val isWebFeed: Boolean) : HubEvent
}

/** Video hub events, decoded for when the app joins calls natively (not in phase 1). */
sealed interface VideoHubEvent {
    data class GroupCallInvite(val invite: ReceiveGroupCallInviteResponse, val isExternal: Boolean) : VideoHubEvent

    data class CallRejected(val rejection: CallRejectedDto) : VideoHubEvent

    /** Media events whose payloads the chat app does not need typed yet. */
    data class Media(val target: String, val payload: JsonElement) : VideoHubEvent
}
