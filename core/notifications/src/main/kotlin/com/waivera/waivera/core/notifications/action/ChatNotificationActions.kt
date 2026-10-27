package com.waivera.waivera.core.notifications.action

/**
 * What notification actions do, implemented in `:app` on top of the data layer.
 * Both calls must return quickly: they run inside a broadcast receiver's `goAsync` window.
 */
interface ChatNotificationActions {
    /**
     * Queues an inline reply in the outbox (optimistic, retried by the outbox) and returns once it
     * is queued. Throws if it could not be queued; the notification then shows a failure line.
     */
    suspend fun reply(conversationId: Long, text: String, threadRootMessageId: Long?)

    /** Marks the conversation read through [lastMessageId] (read-through), or entirely when null. */
    suspend fun markRead(conversationId: Long, lastMessageId: Long?)
}
