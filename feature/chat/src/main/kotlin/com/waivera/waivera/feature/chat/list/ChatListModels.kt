package com.waivera.waivera.feature.chat.list

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable

enum class ChatListFilter { All, Unread, Channels }

/** Why the list is empty; each has its own copy (spec 02 §5). */
enum class EmptyReason { NoMatches, AllCaughtUp, NoChannels, NoConversations }

/** The connection pill (spec 02 §1.2). */
enum class ConnectionUi { Online, Connecting, Reconnecting, Offline }

@Immutable
sealed interface RowLeading {
    data class Channel(val archived: Boolean) : RowLeading

    data object Bot : RowLeading

    data object Group : RowLeading

    /** [userId] is the backend id, used for the avatar and "View profile". */
    data class Direct(val userId: String?, val name: String) : RowLeading
}

/** One inbox row, fully resolved so the row composable does no work in composition. */
@Immutable
data class ChatRowUi(
    val id: Long,
    val title: String,
    val leading: RowLeading,
    val preview: String,
    val previewKind: PreviewKind,
    val senderPrefix: String? = null,
    val typingLabel: String? = null,
    val timeLabel: String = "",
    val unreadCount: Int = 0,
    val hasAttachment: Boolean = false,
    val isChannel: Boolean = false,
    val isReadOnly: Boolean = false,
    val isCallLive: Boolean = false,
    val isOpeningCall: Boolean = false,
    /** Non-null while the arrival wash should play for this message id. */
    val arrivalKey: Long? = null,
    val lastMessageId: Long? = null,
) {
    val isUnread: Boolean get() = unreadCount > 0

    /** "View profile" is offered for direct chats with a known person. */
    val profileUserId: String? get() = (leading as? RowLeading.Direct)?.userId
}

@Immutable
sealed interface ChatListContent {
    /** Nothing cached and the first fetch is running; the skeleton shows after ~300 ms. */
    data object Loading : ChatListContent

    /** Nothing cached and the fetch failed. */
    data class Error(@StringRes val messageRes: Int) : ChatListContent

    data class Empty(val reason: EmptyReason) : ChatListContent

    data object Rows : ChatListContent
}

@Immutable
data class ChatListUiState(
    val content: ChatListContent = ChatListContent.Loading,
    val rows: List<ChatRowUi> = emptyList(),
    val query: String = "",
    val filter: ChatListFilter = ChatListFilter.All,
    val connection: ConnectionUi = ConnectionUi.Connecting,
    /** Only for a pull the person started; background refreshes stay silent. */
    val isRefreshing: Boolean = false,
    /** A refresh failed while cached rows are on screen. */
    @StringRes val refreshErrorRes: Int? = null,
    val accountName: String = "",
    val myUserId: String? = null,
)

sealed interface ChatListEffect {
    data class OpenConversation(val conversationId: Long) : ChatListEffect

    data class OpenPerson(val userId: String) : ChatListEffect

    /** A validated https web lobby URL. */
    data class OpenUrl(val url: String) : ChatListEffect

    data class ShowMessage(@StringRes val messageRes: Int, val retry: ChatListRetry? = null) : ChatListEffect
}

sealed interface ChatListRetry {
    data class MarkRead(val conversationId: Long) : ChatListRetry

    data class JoinCall(val conversationId: Long) : ChatListRetry
}
