package com.waivera.waivera.core.data.model

/**
 * The copy a conversation title needs. Android code uses
 * [com.waivera.waivera.core.data.text.ResourceConversationTitleText]; tests pass an English fake.
 */
interface ConversationTitleText {
    val channelFallback: String
    val conversationFallback: String
    val botTitle: String
    val unknownPerson: String

    /** Two other people, both named in full: "Priya Raman, Jordan Lee". */
    fun pairTitle(first: String, second: String): String

    /** Three or more other people: "Priya Raman & 2 others". */
    fun groupTitle(first: String, others: Int): String
}

/**
 * The one conversation-title rule (UI review UI2-05), used by the inbox row, the conversation and
 * thread headers, Today and notification titles, so a chat reads the same at every step from
 * alert to conversation. The inbox ellipsizes, so it takes the same string as the header.
 */
object ConversationTitles {
    private const val BOT_PREFIX = "bot:"
    private const val PAIR = 2
    private const val EMAIL_LOCAL_MAX = 16
    private const val EMAIL_LOCAL_KEEP = 15
    private const val COMPACT_MAX = 18
    private const val COMPACT_KEEP = 17
    private const val ELLIPSIS = "…"
    private val WHITESPACE = Regex("\\s+")

    fun format(c: Conversation, myUserId: String?, text: ConversationTitleText): String {
        if (isChannel(c)) return c.channel?.name?.trim()?.takeIf(String::isNotEmpty) ?: text.channelFallback
        if (isBot(c, myUserId)) return text.botTitle
        val names = others(c, myUserId).map { displayName(it).ifEmpty { text.unknownPerson } }
        return when (names.size) {
            0 -> text.conversationFallback
            1 -> names.first()
            PAIR -> text.pairTitle(names[0], names[1])
            else -> text.groupTitle(compactLabel(names.first()), names.size - 1)
        }
    }

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

    /** A people conversation with more than one other person (not a channel). */
    fun isGroup(c: Conversation, myUserId: String?): Boolean = !isChannel(c) && others(c, myUserId).size > 1

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
}
