package com.waivera.waivera.core.data.live

import com.waivera.waivera.core.data.model.ActivityItem
import com.waivera.waivera.core.data.model.Attachment
import com.waivera.waivera.core.data.model.ChannelInfo
import com.waivera.waivera.core.data.model.Conversation
import com.waivera.waivera.core.data.model.ConversationKind
import com.waivera.waivera.core.data.model.MeetingInfo
import com.waivera.waivera.core.data.model.Message
import com.waivera.waivera.core.data.model.MessagePreview
import com.waivera.waivera.core.data.model.OngoingCall
import com.waivera.waivera.core.data.model.Person
import com.waivera.waivera.core.data.model.Reaction
import com.waivera.waivera.core.data.model.TicketUpdate
import com.waivera.waivera.core.network.dto.ChannelDto
import com.waivera.waivera.core.network.dto.ChatActivityResponse
import com.waivera.waivera.core.network.dto.ConversationMeetingRoomDto
import com.waivera.waivera.core.network.dto.ConversationResponse
import com.waivera.waivera.core.network.dto.MessageSendResponse
import com.waivera.waivera.core.network.dto.OngoingCallDto
import com.waivera.waivera.core.network.dto.UnifiedConversationResponse
import com.waivera.waivera.core.network.dto.UserInfoResponse

/** DTO to domain mapping. Pure functions; every rule here cites the gap it works around. */
@Suppress("TooManyFunctions")
object Mappers {
    fun person(dto: UserInfoResponse?): Person? {
        val id = dto?.id?.takeIf(String::isNotBlank) ?: return null
        return Person(id, dto.firstName, dto.lastName, dto.displayName, dto.email, dto.avatarUrl)
    }

    fun message(dto: MessageSendResponse): Message {
        val author = person(dto.author)
        // G-11: the server replaces deleted bodies with an English literal on some endpoints and
        // returns the original on others; drop the body whenever the flag is set.
        val deleted = dto.isDeleted
        return Message(
            id = dto.id,
            conversationId = dto.conversationId,
            clientMessageId = dto.clientMessageId,
            author = author,
            authorId = dto.userId ?: author?.id,
            botId = dto.botId,
            text = if (deleted) null else dto.messageContent,
            doc = if (deleted) null else dto.messageDoc,
            contentFormat = dto.contentFormat,
            createdAt = dto.createDate,
            updatedAt = dto.updateDate,
            isEdited = dto.isEdited,
            isDeleted = deleted,
            isBookmarked = dto.isBookmarked,
            type = dto.messageType,
            attachment = if (deleted) null else attachment(dto),
            reactions = dto.reactions.mapNotNull { r ->
                val user = r.userId ?: return@mapNotNull null
                val emoji = r.reactionType ?: return@mapNotNull null
                Reaction(user, emoji)
            },
            callRecordId = dto.callRecordId,
            systemEventType = dto.systemEventType,
            systemEventData = dto.systemEventData,
            parentMessageId = dto.parentMessageId,
            threadRootMessageId = dto.threadRootMessageId ?: dto.parentMessageId,
            threadReplyCount = dto.threadReplyCount,
            plainText = if (deleted) null else dto.messagePlaintext,
        )
    }

    private fun attachment(dto: MessageSendResponse): Attachment? = dto.attachmentUrl?.takeIf(String::isNotBlank)?.let {
        Attachment(
            url = it,
            originalName = dto.attachmentOriginalName,
            contentType = dto.attachmentContentType,
            size = dto.attachmentSize,
            scanStatus = dto.attachmentScanStatus,
            type = dto.attachmentType,
        )
    }

    fun preview(dto: MessageSendResponse?, participants: List<Person>): MessagePreview? {
        if (dto == null || dto.id <= 0) return null
        // G-05: `/all` has no author object, so resolve the name from the participant list.
        val authorId = dto.userId ?: dto.author?.id
        val authorName = person(dto.author)?.name ?: participants.firstOrNull { it.id == authorId }?.name
        return MessagePreview(
            messageId = dto.id,
            authorId = authorId,
            authorName = authorName,
            text = if (dto.isDeleted) null else dto.messageContent ?: dto.messagePlaintext,
            attachmentName = dto.attachmentOriginalName,
            attachmentContentType = dto.attachmentContentType,
            hasAttachment = !dto.attachmentUrl.isNullOrBlank(),
            isDeleted = dto.isDeleted,
            messageType = dto.messageType,
            createdAt = dto.createDate,
            contentFormat = dto.contentFormat,
            plainText = if (dto.isDeleted) null else dto.messagePlaintext?.takeIf(String::isNotBlank),
            isBot = isBot(dto.isBot, dto.botId, authorId),
            botId = dto.botId,
            systemEventType = dto.systemEventType,
            ticket = if (dto.isDeleted) null else TicketUpdate.from(dto.systemEventType, dto.systemEventData),
        )
    }

    fun preview(message: Message): MessagePreview = MessagePreview(
        messageId = message.id,
        authorId = message.authorId,
        authorName = message.author?.name,
        text = message.text,
        attachmentName = message.attachment?.originalName,
        attachmentContentType = message.attachment?.contentType,
        hasAttachment = message.attachment != null,
        isDeleted = message.isDeleted,
        messageType = message.type,
        createdAt = message.createdAt,
        contentFormat = message.contentFormat,
        plainText = message.plainText?.takeIf(String::isNotBlank),
        isBot = isBot(false, message.botId, message.authorId),
        botId = message.botId,
        systemEventType = message.systemEventType,
        ticket = if (message.isDeleted) null else TicketUpdate.from(message.systemEventType, message.systemEventData),
    )

    /** Bot authors carry `isBot`, a `botId`, or an id of the form `bot:<n>` (rest.md UserInfoResponse). */
    fun isBot(flag: Boolean, botId: String?, authorId: String?): Boolean =
        flag || !botId.isNullOrBlank() || authorId?.startsWith(BOT_PREFIX) == true

    private const val BOT_PREFIX = "bot:"

    fun conversation(dto: ConversationResponse): Conversation {
        val participants = dto.participants.mapNotNull(::person)
        return Conversation(
            id = dto.id,
            kind = kind(dto.channel, dto.isBotDirect, participants.size),
            participants = participants,
            channel = dto.channel?.let(::channel),
            lastMessage = preview(dto.lastMessage, participants),
            lastReadAt = dto.lastReadAt,
            unreadCount = dto.unreadCount,
            activeMeeting = dto.activeMeeting?.let(::meeting),
            ongoingCall = dto.ongoingCall?.let(::call),
        )
    }

    fun conversation(dto: UnifiedConversationResponse): Conversation {
        val participants = dto.participants.mapNotNull(::person)
        return Conversation(
            id = dto.conversationId,
            kind = kind(dto.channel, dto.isBotDirect, participants.size, dto.isChannel),
            participants = participants,
            channel = dto.channel?.let(::channel),
            lastMessage = preview(dto.lastMessage, participants),
            lastReadAt = dto.lastReadAt,
            unreadCount = dto.unreadCount,
            // G-13: `/all` hard-codes activeMeeting to null; never read it from here.
            activeMeeting = null,
            ongoingCall = dto.ongoingCall?.let(::call),
        )
    }

    private fun kind(channel: ChannelDto?, isBot: Boolean, participantCount: Int, isChannel: Boolean = false) = when {
        channel != null || isChannel -> ConversationKind.CHANNEL
        isBot -> ConversationKind.BOT
        participantCount > 2 -> ConversationKind.GROUP
        else -> ConversationKind.DIRECT
    }

    fun channel(dto: ChannelDto): ChannelInfo =
        ChannelInfo(dto.name, dto.topic, dto.channelType, dto.ownerId, dto.isArchived, dto.canWrite)

    fun meeting(dto: ConversationMeetingRoomDto): MeetingInfo =
        MeetingInfo(dto.roomId, dto.calendarEventId, dto.title, dto.startDate ?: dto.createdAt)

    fun call(dto: OngoingCallDto): OngoingCall = OngoingCall(dto.roomId, dto.startTime, dto.status, dto.callRecordId)

    fun activity(dto: ChatActivityResponse): ActivityItem = ActivityItem(
        id = dto.id,
        type = dto.activityType,
        actorUserId = dto.actorUserId,
        actorName = dto.actorDisplayName,
        conversationId = dto.conversationId,
        messageId = dto.messageId,
        threadRootMessageId = dto.threadRootMessageId,
        reactionType = dto.reactionType,
        summary = dto.summary,
        createdAt = dto.createdAt,
        read = dto.read,
    )
}

/**
 * Merging rules for cached conversations (PLAN parity fix: never replace richer fields with null).
 */
object ConversationMerge {
    /**
     * G-05: `/my-conversations` has the author and meeting but drops attachment fields;
     * `/all` has attachments and channels but no author. Combine both views of one conversation.
     */
    fun combine(mine: Conversation?, all: Conversation?): Conversation? = when {
        mine == null -> all
        all == null -> mine
        else -> mine.copy(
            kind = if (all.kind == ConversationKind.CHANNEL) ConversationKind.CHANNEL else mine.kind,
            channel = mine.channel ?: all.channel,
            lastMessage = mergePreview(mine.lastMessage, all.lastMessage),
            participants = mine.participants.ifEmpty { all.participants },
            ongoingCall = mine.ongoingCall ?: all.ongoingCall,
            unreadCount = maxOf(mine.unreadCount, all.unreadCount),
        )
    }

    /** Newer preview wins; for the same message, keep every field the newer copy lacks. */
    @Suppress("ReturnCount")
    fun mergePreview(current: MessagePreview?, incoming: MessagePreview?): MessagePreview? {
        if (current == null) return incoming
        if (incoming == null) return current
        if (incoming.messageId != current.messageId) {
            val incomingNewer = incoming.messageId > current.messageId
            return if (incomingNewer) incoming else current
        }
        return incoming.copy(
            authorId = incoming.authorId ?: current.authorId,
            authorName = incoming.authorName ?: current.authorName,
            text = if (incoming.isDeleted) null else incoming.text ?: current.text,
            attachmentName = incoming.attachmentName ?: current.attachmentName,
            attachmentContentType = incoming.attachmentContentType ?: current.attachmentContentType,
            hasAttachment = incoming.hasAttachment || current.hasAttachment,
            createdAt = incoming.createdAt ?: current.createdAt,
            contentFormat = maxOf(incoming.contentFormat, current.contentFormat),
            plainText = if (incoming.isDeleted) null else incoming.plainText ?: current.plainText,
            isBot = incoming.isBot || current.isBot,
            botId = incoming.botId ?: current.botId,
            systemEventType = incoming.systemEventType ?: current.systemEventType,
            ticket = if (incoming.isDeleted) null else incoming.ticket ?: current.ticket,
        )
    }

    /**
     * Applies a fresh server copy over the cache. [keepUnread] keeps the cached badge, for
     * `/conversation/{id}` which never fills `unreadCount` (G-14).
     */
    fun refresh(cached: Conversation?, fresh: Conversation, keepUnread: Boolean = false): Conversation {
        if (cached == null) return fresh
        return fresh.copy(
            participants = fresh.participants.ifEmpty { cached.participants },
            channel = fresh.channel ?: cached.channel,
            lastMessage = mergePreview(cached.lastMessage, fresh.lastMessage),
            unreadCount = if (keepUnread) cached.unreadCount else fresh.unreadCount,
            activeMeeting = fresh.activeMeeting ?: cached.activeMeeting.takeIf { keepUnread },
        )
    }
}
