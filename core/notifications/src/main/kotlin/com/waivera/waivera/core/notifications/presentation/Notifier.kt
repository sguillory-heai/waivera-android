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

    fun cancelConversation(conversationId: Long)

    /** Sign-out clears everything so the next account never sees the previous one's messages. */
    fun cancelAll()
}
