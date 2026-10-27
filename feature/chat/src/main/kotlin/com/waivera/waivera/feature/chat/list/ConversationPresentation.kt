package com.waivera.waivera.feature.chat.list

import com.waivera.waivera.core.data.model.Conversation
import com.waivera.waivera.core.data.model.ConversationKind
import com.waivera.waivera.core.data.model.MessagePreview
import com.waivera.waivera.core.data.model.Person
import com.waivera.waivera.core.network.dto.MessageType
import java.time.Instant

/**
 * The inbox row rules from spec 02 §4, as pure functions over the domain model. One place, one
 * pass: the Expo app normalised twice with different defaults, which is not repeated here.
 */
object ConversationPresentation {
    private const val BOT_PREFIX = "bot:"
    private const val EMAIL_LOCAL_MAX = 16
    private const val EMAIL_LOCAL_KEEP = 15
    private const val COMPACT_MAX = 18
    private const val COMPACT_KEEP = 17
    private const val ELLIPSIS = "…"
    private val WHITESPACE = Regex("\\s+")

    /** A conversation with any channel object is a channel, even when the kind says otherwise. */
    fun isChannel(c: Conversation): Boolean = c.kind == ConversationKind.CHANNEL || c.channel != null

    /** Everyone except me; with no known identity, everyone (matching the reference client). */
    fun others(c: Conversation, myUserId: String?): List<Person> =
        if (myUserId == null) c.participants else c.participants.filter { it.id != myUserId }

    /** Automated bot conversations (spec 02 §4 "Bot detection"). Never true for channels. */
    @Suppress("ReturnCount")
    fun isBot(c: Conversation, myUserId: String?): Boolean {
        if (isChannel(c)) return false
        if (c.kind == ConversationKind.BOT) return true
        if (c.participants.any { it.id.startsWith(BOT_PREFIX) }) return true
        if (c.lastMessage?.authorId?.startsWith(BOT_PREFIX) == true) return true
        // Legacy bot DMs list only the viewer as a participant.
        if (c.participants.size != 1) return false
        return myUserId == null || c.participants.first().id == myUserId
    }

    fun isGroup(c: Conversation, myUserId: String?): Boolean = !isChannel(c) && others(c, myUserId).size > 1

    /** The person a direct row shows: the first other participant, else the first participant. */
    fun peer(c: Conversation, myUserId: String?): Person? =
        others(c, myUserId).firstOrNull() ?: c.participants.firstOrNull()

    fun leading(c: Conversation, myUserId: String?): RowLeading = when {
        isChannel(c) -> RowLeading.Channel(archived = c.channel?.isArchived == true)
        isBot(c, myUserId) -> RowLeading.Bot
        isGroup(c, myUserId) -> RowLeading.Group
        else -> peer(c, myUserId).let { RowLeading.Direct(userId = it?.id, name = it?.let(::displayName).orEmpty()) }
    }

    fun title(c: Conversation, myUserId: String?, text: ChatListText): String {
        if (isChannel(c)) return c.channel?.name?.trim()?.takeIf(String::isNotEmpty) ?: text.channelFallback
        if (isBot(c, myUserId)) return text.botTitle
        val others = others(c, myUserId)
        return when (others.size) {
            0 -> text.conversationFallback
            1 -> displayName(others.first()).ifEmpty { text.unknownPerson }
            else -> text.groupTitle(
                compactLabel(displayName(others.first()).ifEmpty { text.unknownPerson }),
                others.size - 1,
            )
        }
    }

    /** "First Last" from the non-blank parts, else display name, else email; empty when none. */
    fun displayName(p: Person): String {
        val explicit = listOfNotNull(p.firstName, p.lastName).map(String::trim).filter(String::isNotEmpty)
            .joinToString(" ")
        return explicit.ifEmpty { p.displayName?.trim().orEmpty() }.ifEmpty { p.email?.trim().orEmpty() }
    }

    /**
     * Short label for the first name in a group title. Mirrors `compactParticipantLabel` in the
     * Expo source: an email keeps its local part, cut to 15 characters only when longer than 16.
     */
    @Suppress("ReturnCount")
    fun compactLabel(raw: String): String {
        if ('@' in raw) {
            val local = raw.substringBefore('@').ifEmpty { raw }
            return if (local.length > EMAIL_LOCAL_MAX) local.take(EMAIL_LOCAL_KEEP) + ELLIPSIS else local
        }
        if (raw.length <= COMPACT_MAX) return raw
        val words = raw.split(WHITESPACE).filter(String::isNotEmpty)
        if (words.size >= 2) {
            val abbreviated = "${words.first().first()}. ${words.last()}"
            if (abbreviated.length <= COMPACT_MAX) return abbreviated
        }
        return raw.take(COMPACT_KEEP) + ELLIPSIS
    }

    /**
     * The preview line (spec 02 §4 "Preview"). A blank result means the server sent a thin
     * `lastMessage` and the row should be hydrated from `/conversation/{id}`.
     */
    @Suppress("ReturnCount")
    fun preview(message: MessagePreview?, text: ChatListText): Preview {
        if (message == null) return Preview(text.noMessages, PreviewKind.Placeholder)
        // Deleted rows would otherwise be blank forever and re-hydrated on every refresh.
        if (message.isDeleted) return Preview(text.deletedMessage, PreviewKind.Placeholder)
        val body = firstLine(bodyText(message))
        if (body.isNotEmpty()) return Preview(body, PreviewKind.Text)
        if (message.hasAttachment || message.attachmentContentType != null) {
            val type = message.attachmentContentType.orEmpty().lowercase()
            val label = when {
                type == "image" || type.startsWith("image/") -> text.photo
                else -> message.attachmentName?.trim()?.takeIf(String::isNotEmpty) ?: text.fileAttachment
            }
            return Preview(label, PreviewKind.Attachment)
        }
        if (isSystem(message)) return Preview(text.conversationUpdate, PreviewKind.System)
        return Preview("", PreviewKind.Blank)
    }

    fun isSystem(message: MessagePreview): Boolean =
        message.messageType == MessageType.SYSTEM || message.messageType == MessageType.CHANNEL_EVENT

    private fun bodyText(message: MessagePreview): String {
        val raw = message.text?.takeIf(String::isNotBlank) ?: return ""
        val plain = RichTextPlain.toPlainText(raw)
        return if (isSystem(message)) SystemText.clean(plain) else plain
    }

    private fun firstLine(value: String): String =
        value.replace("\r\n", "\n").replace('\r', '\n').trim('\n', ' ', '\t').substringBefore('\n')
            .replace(WHITESPACE, " ").trim()

    /**
     * "You" for my own message, the author's first name in groups and channels, nothing for a
     * direct chat from the other person, bots, placeholders and system rows.
     */
    fun senderPrefix(c: Conversation, preview: Preview, myUserId: String?, text: ChatListText): String? {
        val message = c.lastMessage ?: return null
        if (preview.kind != PreviewKind.Text && preview.kind != PreviewKind.Attachment) return null
        if (isSystem(message)) return null
        val author = message.authorId
        if (author != null && author == myUserId) return text.you
        if (!isChannel(c) && !isGroup(c, myUserId)) return null
        val name = c.participants.firstOrNull { it.id == author }?.let(::displayName)?.takeIf(String::isNotEmpty)
            ?: message.authorName?.trim()?.takeIf(String::isNotEmpty)
            ?: return null
        return name.split(WHITESPACE).first()
    }

    /** Spec 02 §4 "Typing override". */
    fun typingLabel(c: Conversation, typingUserIds: Set<String>, myUserId: String?, text: ChatListText): String? {
        val ids = typingUserIds - setOfNotNull(myUserId)
        return when {
            ids.isEmpty() -> null
            ids.size > 1 -> text.severalTyping
            else -> {
                val person = c.participants.firstOrNull { it.id == ids.first() }
                text.typing(person?.let(::displayName)?.takeIf(String::isNotEmpty) ?: text.someone)
            }
        }
    }

    /** A live call or meeting room. An unknown call status is inert (pitfall 19). */
    fun isCallLive(c: Conversation): Boolean = c.ongoingCall?.isLive == true || c.activeMeeting?.roomId != null

    /**
     * Inbox order: newest activity first, rows without a timestamp last (epoch 0), then by id
     * so the order is stable when times tie.
     */
    val Order: Comparator<Conversation> = compareByDescending<Conversation> {
        it.activityAt ?: Instant.EPOCH
    }.thenByDescending { it.id }

    fun matches(row: ChatRowUi, filter: ChatListFilter, query: String): Boolean {
        val passesFilter = when (filter) {
            ChatListFilter.All -> true
            ChatListFilter.Unread -> row.unreadCount > 0
            ChatListFilter.Channels -> row.isChannel
        }
        if (!passesFilter) return false
        val q = query.trim().lowercase()
        return q.isEmpty() || row.title.lowercase().contains(q) || row.preview.lowercase().contains(q)
    }

    fun emptyReason(filter: ChatListFilter, query: String): EmptyReason = when {
        query.isNotBlank() -> EmptyReason.NoMatches
        filter == ChatListFilter.Unread -> EmptyReason.AllCaughtUp
        filter == ChatListFilter.Channels -> EmptyReason.NoChannels
        else -> EmptyReason.NoConversations
    }
}

enum class PreviewKind { Text, Attachment, System, Placeholder, Blank }

data class Preview(val text: String, val kind: PreviewKind)

/** Strips the `{[TIMESTAMP- 08/10/2026 02:20:12]}` token from system text (spec 03 §16). */
internal object SystemText {
    private val TOKEN = Regex(
        """\s*-?\s*\{\[\s*TIMESTAMP\s*[-·:]\s*\d{1,2}/\d{1,2}/\d{4}\s+\d{1,2}:\d{2}:\d{2}\s*]}\s*""",
        RegexOption.IGNORE_CASE,
    )
    private val TRAILING_DASH = Regex("""\s*-\s*$""")
    private val WHITESPACE = Regex("\\s+")

    fun clean(value: String): String =
        value.replace(TOKEN, " ").replace(TRAILING_DASH, "").replace(WHITESPACE, " ").trim()
}
