package com.waivera.waivera.core.realtime

import com.waivera.waivera.core.network.WaiveraJson
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
import com.waivera.waivera.core.network.json.withCamelCaseKeys
import com.waivera.waivera.core.network.time.WaiveraTime
import com.waivera.waivera.core.realtime.ChatHubContract.Events
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Turns a hub invocation (target + positional arguments) into a [HubEvent]. Payloads are
 * decoded case-insensitively with int-or-string enums (gaps.md G-03). Returns null for unknown
 * targets and for payloads missing the ids an event cannot work without (for example a message
 * with no id or conversation id), so a malformed event never reaches the cache.
 *
 * Suppressed: One decoder per hub event.
 */
@Suppress("TooManyFunctions")
object HubEventDecoder {
    fun decode(target: String, arguments: JsonArray): HubEvent? = try {
        decodeOrThrow(target, arguments.map { it.withCamelCaseKeys() })
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

    @Suppress("CyclomaticComplexMethod")
    private fun decodeOrThrow(target: String, args: List<JsonElement>): HubEvent? = when (target) {
        Events.RECEIVE_MESSAGE -> message(args)?.let(HubEvent::MessageReceived)
        Events.RECEIVE_CONVERSATION_LIST_MESSAGE -> message(args)?.let(HubEvent::ConversationListMessage)
        Events.RECEIVE_CHAT_ATTENTION -> message(args)?.let { HubEvent.ChatAttention(it, reason(args.getOrNull(1))) }
        Events.RECEIVE_CHAT_ACTIVITY -> obj(args, ChatActivityResponse.serializer())
            ?.takeIf { it.id > 0 && it.conversationId > 0 }
            ?.let(HubEvent::ChatActivity)
        Events.REMOVE_CHAT_ACTIVITY -> activityId(args.firstOrNull())?.let(HubEvent::ChatActivityRemoved)
        Events.RECEIVE_CHAT_READ_STATE -> obj(args, ChatReadStateDto.serializer())?.let(HubEvent::ChatReadState)
        Events.RECEIVE_TYPING_STATUS_FOR_USER -> typing(args)
        Events.RECEIVE_READ_RECEIPT -> readReceipt(args)
        Events.RECEIVE_REACTION -> reaction(args)
        Events.RECEIVE_BOOKMARK_UPDATE -> HubEvent.BookmarkUpdate(
            obj(args, BookmarkUpdateDto.serializer()) ?: BookmarkUpdateDto(),
        )
        Events.RECEIVE_MESSAGE_UPDATE -> obj(args, MessageUpdateDto.serializer())
            ?.takeIf { it.messageId > 0 }
            ?.let(HubEvent::MessageUpdated)
        Events.RECEIVE_MESSAGE_DELETE -> obj(args, MessageDeleteDto.serializer())
            ?.takeIf { it.messageId > 0 }
            ?.let(HubEvent::MessageDeleted)
        Events.CONVERSATION_CREATED -> obj(args, ConversationResponse.serializer())
            ?.takeIf { it.id > 0 }
            ?.let(HubEvent::ConversationCreated)
        Events.CONVERSATION_CALL_STATE_CHANGED -> obj(args, ConversationCallStateDto.serializer())
            ?.takeIf { it.conversationId > 0 }
            ?.let(HubEvent::CallStateChanged)
        Events.REMOVE_AD_HOC_FROM_CONVERSATION -> adHoc(args.firstOrNull())?.let(HubEvent::AdHocRemoved)
        Events.RECEIVE_NOTIFICATION -> obj(args, EventNotificationResponse.serializer())
            ?.let { HubEvent.Notification(it, isWebFeed = false) }
        Events.RECEIVE_NOTIFICATION_WEB -> obj(args, EventNotificationResponse.serializer())
            ?.let { HubEvent.Notification(it, isWebFeed = true) }
        else -> null
    }

    /** Video hub invocations; unknown targets return null. */
    fun decodeVideo(target: String, arguments: JsonArray): VideoHubEvent? {
        val first = arguments.firstOrNull()?.withCamelCaseKeys() ?: return null
        return when (target) {
            VideoHubContract.Events.RECEIVE_GROUP_CALL_INVITE ->
                VideoHubEvent.GroupCallInvite(decodeElement(ReceiveGroupCallInviteResponse.serializer(), first), false)
            VideoHubContract.Events.RECEIVE_EXTERNAL_GROUP_CALL_INVITE ->
                VideoHubEvent.GroupCallInvite(decodeElement(ReceiveGroupCallInviteResponse.serializer(), first), true)
            VideoHubContract.Events.CALL_REJECTED ->
                VideoHubEvent.CallRejected(decodeElement(CallRejectedDto.serializer(), first))
            VideoHubContract.Events.NEW_PUBLISHER,
            VideoHubContract.Events.EXISTING_PARTICIPANTS,
            VideoHubContract.Events.REMOTE_MEDIA_STATE_CHANGED,
            VideoHubContract.Events.REMOTE_FEED_LEFT,
            VideoHubContract.Events.SCREEN_SHARE_STARTED,
            VideoHubContract.Events.SCREEN_SHARE_ENDED,
            -> VideoHubEvent.Media(target, first)
            else -> null
        }
    }

    private fun message(args: List<JsonElement>): MessageSendResponse? =
        obj(args, MessageSendResponse.serializer())?.takeIf { it.id > 0 && it.conversationId > 0 }

    private fun reason(element: JsonElement?): AttentionReason =
        (element as? JsonPrimitive)?.contentOrNull?.let { raw ->
            AttentionReason.entries.firstOrNull { it != AttentionReason.UNKNOWN && it.wireName.equals(raw, true) }
        } ?: AttentionReason.UNKNOWN

    @Suppress("ReturnCount")
    private fun typing(args: List<JsonElement>): HubEvent? {
        val conversationId = args.longAt(0) ?: return null
        val userId = args.stringAt(1) ?: return null
        return HubEvent.TypingStatus(conversationId, userId, args.boolAt(2) ?: false)
    }

    @Suppress("ReturnCount")
    private fun readReceipt(args: List<JsonElement>): HubEvent? {
        val conversationId = args.longAt(0) ?: return null
        val userId = args.stringAt(1) ?: return null
        return HubEvent.ReadReceipt(conversationId, userId, WaiveraTime.parseInstant(args.stringAt(2)))
    }

    @Suppress("ReturnCount", "MagicNumber")
    private fun reaction(args: List<JsonElement>): HubEvent? {
        val conversationId = args.longAt(0) ?: return null
        val messageId = args.longAt(1) ?: return null
        val userId = args.stringAt(2) ?: return null
        val action = args.stringAt(4)?.let { raw ->
            ReactionAction.entries.firstOrNull { it != ReactionAction.UNKNOWN && it.wireName.equals(raw, true) }
        } ?: ReactionAction.UNKNOWN
        return HubEvent.Reaction(conversationId, messageId, userId, args.stringAt(3), action)
    }

    /** A bare number (`[4411]`), or an object with `id` from older backends. */
    private fun activityId(element: JsonElement?): Long? = when (element) {
        is JsonPrimitive -> element.longOrNull ?: element.contentOrNull?.toLongOrNull()
        is JsonObject -> (element["id"] as? JsonPrimitive)?.longOrNull
        else -> null
    }

    /** An object, or a bare conversation id. */
    private fun adHoc(element: JsonElement?): RemoveAdHocFromConversationDto? = when (element) {
        is JsonObject -> decodeElement(RemoveAdHocFromConversationDto.serializer(), element)
        is JsonPrimitive -> element.longOrNull?.let { RemoveAdHocFromConversationDto(conversationId = it) }
        else -> null
    }?.takeIf { it.conversationId > 0 }

    private fun <T> obj(args: List<JsonElement>, deserializer: DeserializationStrategy<T>): T? {
        val first = args.firstOrNull() as? JsonObject ?: return null
        return decodeElement(deserializer, first)
    }

    private fun <T> decodeElement(deserializer: DeserializationStrategy<T>, element: JsonElement): T =
        WaiveraJson.decodeFromJsonElement(deserializer, element)

    private fun List<JsonElement>.primitiveAt(index: Int): JsonPrimitive? =
        getOrNull(index)?.takeUnless { it is JsonNull } as? JsonPrimitive

    private fun List<JsonElement>.longAt(index: Int): Long? =
        primitiveAt(index)?.let { it.longOrNull ?: it.contentOrNull?.toLongOrNull() }

    private fun List<JsonElement>.stringAt(index: Int): String? = primitiveAt(index)?.contentOrNull

    private fun List<JsonElement>.boolAt(index: Int): Boolean? =
        primitiveAt(index)?.let { it.booleanOrNull ?: it.contentOrNull?.toBooleanStrictOrNull() }
}
