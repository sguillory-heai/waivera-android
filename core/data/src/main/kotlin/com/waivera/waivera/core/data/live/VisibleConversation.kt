package com.waivera.waivera.core.data.live

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The conversation currently on screen, shared by unread counting and push suppression. */
@Singleton
class VisibleConversation @Inject constructor() {
    private val visible = MutableStateFlow<Long?>(null)
    val id: StateFlow<Long?> = visible.asStateFlow()

    fun set(conversationId: Long?) {
        visible.value = conversationId
    }
}
