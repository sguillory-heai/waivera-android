package com.waivera.waivera.feature.chat.list

import com.waivera.waivera.core.data.model.Conversation
import com.waivera.waivera.core.data.model.ConversationTitles
import com.waivera.waivera.core.data.model.Person
import java.time.Instant

/**
 * The inbox row rules from spec 02 §4, as pure functions over the domain model. One place, one
 * pass: the Expo app normalised twice with different defaults, which is not repeated here.
 */
object ConversationPresentation {
    /** A conversation with any channel object is a channel, even when the kind says otherwise. */
    fun isChannel(c: Conversation): Boolean = ConversationTitles.isChannel(c)

    /** Everyone except me; with no known identity, everyone (matching the reference client). */
    fun others(c: Conversation, myUserId: String?): List<Person> = ConversationTitles.others(c, myUserId)

    /** Automated bot conversations (spec 02 §4 "Bot detection"). Never true for channels. */
    fun isBot(c: Conversation, myUserId: String?): Boolean = ConversationTitles.isBot(c, myUserId)

    fun isGroup(c: Conversation, myUserId: String?): Boolean = ConversationTitles.isGroup(c, myUserId)

    /** The person a direct row shows: the first other participant, else the first participant. */
    fun peer(c: Conversation, myUserId: String?): Person? =
        others(c, myUserId).firstOrNull() ?: c.participants.firstOrNull()

    fun leading(c: Conversation, myUserId: String?): RowLeading = when {
        isChannel(c) -> RowLeading.Channel(archived = c.channel?.isArchived == true)
        isBot(c, myUserId) -> RowLeading.Bot
        isGroup(c, myUserId) -> RowLeading.Group
        else -> peer(c, myUserId).let { RowLeading.Direct(userId = it?.id, name = it?.let(::displayName).orEmpty()) }
    }

    /** The shared title rule, so the row reads exactly like the conversation header. */
    fun title(c: Conversation, myUserId: String?, text: ChatListText): String =
        ConversationTitles.format(c, myUserId, text)

    fun displayName(p: Person): String = ConversationTitles.displayName(p)

    fun compactLabel(raw: String): String = ConversationTitles.compactLabel(raw)

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
        """\s*-?\s*\{\[\s*TIMESTAMP\s*[-·:]\s*\d{1,2}/\d{1,2}/\d{4}\s+\d{1,2}:\d{2}:\d{2}\s*\]\}\s*""",
        RegexOption.IGNORE_CASE,
    )
    private val TRAILING_DASH = Regex("""\s*-\s*$""")
    private val WHITESPACE = Regex("\\s+")

    fun clean(value: String): String =
        value.replace(TOKEN, " ").replace(TRAILING_DASH, "").replace(WHITESPACE, " ").trim()
}
