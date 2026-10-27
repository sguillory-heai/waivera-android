package com.waivera.waivera.feature.chat.conversation

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.waivera.waivera.feature.chat.message.ChatSheet
import com.waivera.waivera.feature.chat.message.ConnectionUi
import com.waivera.waivera.feature.chat.message.ReadOnlyReason
import com.waivera.waivera.feature.chat.message.SendErrorKind
import com.waivera.waivera.feature.chat.message.StagedImage
import com.waivera.waivera.feature.chat.message.TimelineItem

/** What fills the message area. Rows stay on screen through refresh errors (stale, not error). */
@Immutable
sealed interface ConversationContent {
    /** The route carried an id the API can never return (03 §1). */
    data object Invalid : ConversationContent

    data object Loading : ConversationContent

    data class Error(@StringRes val message: Int) : ConversationContent

    data object Empty : ConversationContent

    data object Messages : ConversationContent
}

/** The header call button and the in-progress banner (03 §12, web handoff). */
@Immutable
data class CallUi(val available: Boolean = false, val isLive: Boolean = false, val isOpening: Boolean = false)

@Immutable
data class ConversationUiState(
    /** Null until the conversation loads; from the shared title rule, so it matches the inbox row. */
    val title: String? = null,
    /** Everyone in a group chat, me included, for the "3 members" header line; null outside groups. */
    val memberCount: Int? = null,
    /** A one-to-one chat: the header already names the other person, so rows skip the author line. */
    val isDirect: Boolean = false,
    val connection: ConnectionUi = ConnectionUi.Connected,
    val content: ConversationContent = ConversationContent.Loading,
    /** Oldest first; the screen reverses it for the bottom-anchored list. */
    val items: List<TimelineItem> = emptyList(),
    val isStale: Boolean = false,
    val hasOlder: Boolean = false,
    val isLoadingOlder: Boolean = false,
    val olderFailed: Boolean = false,
    val highlightKey: String? = null,
    val visibleTimestamps: Set<String> = emptySet(),
    /** Display names by user id, for rows, reactions and typing. */
    val names: Map<String, String> = emptyMap(),
    /** People typing now; a blank entry is someone whose name is unknown. */
    val typingNames: List<String> = emptyList(),
    /** Null while permissions are loading: the composer stays disabled, no notice yet. */
    val canWrite: Boolean? = null,
    val readOnlyReason: ReadOnlyReason? = null,
    val sendError: SendErrorKind? = null,
    val call: CallUi = CallUi(),
    val stagedImage: StagedImage? = null,
    val editingMessageId: Long? = null,
    val sheet: ChatSheet? = null,
    val myUserId: String? = null,
    val isBot: Boolean = false,
)
