package com.waivera.waivera.core.notifications.presentation

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** The conversation (or thread) currently on screen and resumed. */
data class VisibleChat(val conversationId: Long, val threadRootMessageId: Long? = null)

/**
 * Records which chat is on screen. The app shell is the only caller: it calls [enter] when the
 * top route is a conversation or thread and the activity is resumed, and [clear] otherwise, so a
 * chat that is merely on the back stack never counts as visible. Chat screens never call it.
 * Entering a chat also clears its notification.
 */
@Singleton
class ActiveConversationTracker @Inject constructor(private val notifier: Notifier) {
    private val visibleChat = MutableStateFlow<VisibleChat?>(null)

    val visible: StateFlow<VisibleChat?> = visibleChat.asStateFlow()

    fun enter(conversationId: Long, threadRootMessageId: Long? = null) {
        visibleChat.value = VisibleChat(conversationId, threadRootMessageId)
        notifier.cancelConversation(conversationId)
    }

    /** Nothing is on screen (the app left the foreground, or the shell left the chat). */
    fun clear() {
        visibleChat.value = null
    }

    fun leave(conversationId: Long, threadRootMessageId: Long? = null) {
        visibleChat.update { current ->
            if (current == VisibleChat(conversationId, threadRootMessageId)) null else current
        }
    }
}
