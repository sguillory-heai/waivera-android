package com.waivera.waivera.core.data.live

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The chat currently on screen, shared by unread counting and push suppression. A thread on
 * screen is not its conversation's timeline: main-timeline messages that arrive meanwhile are
 * unseen, so they still count as unread (IF-02).
 */
@Singleton
class VisibleConversation @Inject constructor() {
    private val timeline = MutableStateFlow<Long?>(null)
    private val chat = MutableStateFlow<Long?>(null)

    /** The conversation whose main timeline is on screen; null while only one of its threads is. */
    val id: StateFlow<Long?> = timeline.asStateFlow()

    /** The conversation with its timeline or one of its threads on screen. */
    val chatId: StateFlow<Long?> = chat.asStateFlow()

    fun set(conversationId: Long?, threadRootMessageId: Long? = null) {
        chat.value = conversationId
        timeline.value = conversationId.takeIf { threadRootMessageId == null }
    }
}
