package com.waivera.waivera.feature.chat.list

import com.waivera.waivera.core.data.model.Conversation
import com.waivera.waivera.core.data.model.ConversationKind
import com.waivera.waivera.core.data.model.Person
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
        // The data layer folds isBot, botId and a `bot:` author into the flag; previews built
        // elsewhere (tests, the demo) may carry only the author id.
        val last = c.lastMessage
        if (last?.isBot == true || last?.authorId?.startsWith(BOT_PREFIX) == true) return true
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

    /** A live call or meeting room. An unknown call status is inert (pitfall 19). */
    fun isCallLive(c: Conversation): Boolean = c.ongoingCall?.isLive == true || c.activeMeeting?.roomId != null

    /**
     * Inbox order: newest activity first (`Conversation.activityAt` falls back from the last
     * message to the call and meeting times; the API sends no modified or created date), rows
     * without any timestamp last (epoch 0), then by id so the order is stable when times tie.
     */
    val Order: Comparator<Conversation> = compareByDescending<Conversation> {
        it.activityAt ?: Instant.EPOCH
    }.thenByDescending { it.id }
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
