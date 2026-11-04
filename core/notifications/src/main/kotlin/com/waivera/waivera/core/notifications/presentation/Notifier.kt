package com.waivera.waivera.core.notifications.presentation

import com.waivera.waivera.core.notifications.channel.WaiveraChannel
import com.waivera.waivera.core.notifications.payload.PushMessage

/** Posts and clears Waivera notifications. [AndroidNotifier] is the real one; tests use a fake. */
interface Notifier {
    /** Whether the app may post at all (runtime permission and app-level switch). */
    fun canPost(): Boolean

    fun show(message: PushMessage, channel: WaiveraChannel, conversation: ConversationNotificationInfo?)

    /** Adds the person's own inline reply to the conversation's notification. */
    fun showReplySent(conversationId: Long, threadRootMessageId: Long?, text: String)

    /** Replaces the reply spinner with a short failure line; the chat screen offers Retry. */
    fun showReplyFailed(conversationId: Long, threadRootMessageId: Long?)

    /** Clears one chat notification: the main chat's when [threadRootMessageId] is null, else that thread's. */
    fun cancelChat(conversationId: Long, threadRootMessageId: Long?)

    /** Clears the main chat's notification; its threads keep theirs until each is read. */
    fun cancelConversation(conversationId: Long) = cancelChat(conversationId, threadRootMessageId = null)

    fun cancelThread(conversationId: Long, threadRootMessageId: Long) = cancelChat(conversationId, threadRootMessageId)

    /** Clears a stored notification's entry, or with a null id every one of them. */
    fun cancelNotice(notificationId: String?)

    /** Sign-out clears everything so the next account never sees the previous one's messages. */
    fun cancelAll()
}
