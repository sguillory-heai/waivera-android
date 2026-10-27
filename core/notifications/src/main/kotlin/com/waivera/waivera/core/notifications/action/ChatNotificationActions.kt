package com.waivera.waivera.core.notifications.action

/**
 * What notification actions do, implemented in `:app` on top of the data layer.
 * Both calls must return quickly: they run inside a broadcast receiver's `goAsync` window.
 */
interface ChatNotificationActions {
    /**
     * Saves an inline reply in the outbox with a durable retry, tries it once, and returns. The
     * receiver may lose its process right after, so nothing may be left only in memory. Throws
     * if it could not be saved; the notification then shows a failure line.
     */
    suspend fun reply(conversationId: Long, text: String, threadRootMessageId: Long?)

    /** Marks the conversation read through [lastMessageId] (read-through), or entirely when null. */
    suspend fun markRead(conversationId: Long, lastMessageId: Long?)
}
