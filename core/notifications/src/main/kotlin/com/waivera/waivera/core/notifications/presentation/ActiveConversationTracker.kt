package com.waivera.waivera.core.notifications.presentation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/** The conversation (or thread) currently on screen and resumed. */
data class VisibleChat(val conversationId: Long, val threadRootMessageId: Long? = null)

/**
 * Records which chat is on screen. The conversation and thread screens call [enter] when
 * resumed and [leave] when paused (for example from a `LifecycleResumeEffect`), so a chat
 * that is merely on the back stack never counts as visible. Entering a chat also clears its
 * notification.
 */
@Singleton
class ActiveConversationTracker @Inject constructor(private val notifier: Notifier) {
    private val visibleChat = MutableStateFlow<VisibleChat?>(null)

    val visible: StateFlow<VisibleChat?> = visibleChat.asStateFlow()

    fun enter(conversationId: Long, threadRootMessageId: Long? = null) {
        visibleChat.value = VisibleChat(conversationId, threadRootMessageId)
        notifier.cancelConversation(conversationId)
    }

    fun leave(conversationId: Long, threadRootMessageId: Long? = null) {
        visibleChat.update { current ->
            if (current == VisibleChat(conversationId, threadRootMessageId)) null else current
        }
    }
}
