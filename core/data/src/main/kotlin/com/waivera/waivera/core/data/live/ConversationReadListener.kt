package com.waivera.waivera.core.data.live

/**
 * Told when a conversation was read, in this app or on another device (the hub's read state for
 * this user), so its notifications leave the shade too (IF-07). Implemented in `:app` over the
 * notifier.
 */
fun interface ConversationReadListener {
    fun onRead(conversationId: Long)
}
