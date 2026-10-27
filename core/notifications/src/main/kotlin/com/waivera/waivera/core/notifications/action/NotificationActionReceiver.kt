package com.waivera.waivera.core.notifications.action

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import com.waivera.waivera.core.notifications.deeplink.DeepLinkUris
import com.waivera.waivera.core.notifications.di.NotificationsEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * Handles Reply and Mark read from chat notifications. Not exported; only this app's own
 * pending intents reach it. Work runs in `goAsync` and is bounded well inside the receiver limit.
 */
class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val conversationId = DeepLinkUris.positiveId(intent.getStringExtra(EXTRA_CONVERSATION_ID)) ?: return
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            NotificationsEntryPoint::class.java,
        )
        val pending = goAsync()
        entryPoint.scope().launch {
            try {
                withTimeout(ACTION_TIMEOUT_MILLIS) {
                    when (intent.action) {
                        ACTION_REPLY -> reply(entryPoint, intent, conversationId)
                        ACTION_MARK_READ -> markRead(entryPoint, intent, conversationId)
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }

    // Any failure to queue the reply must reach the person as a failure line, never a crash.
    @Suppress("TooGenericExceptionCaught")
    private suspend fun reply(entryPoint: NotificationsEntryPoint, intent: Intent, conversationId: Long) {
        val threadRoot = DeepLinkUris.positiveId(intent.getStringExtra(EXTRA_THREAD_ROOT_ID))
        val text = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(KEY_REPLY_TEXT)?.toString()?.trim()
        val notifier = entryPoint.notifier()
        if (text.isNullOrEmpty()) {
            // Re-post so the system stops showing the reply spinner.
            notifier.showReplyFailed(conversationId, threadRoot)
            return
        }
        try {
            entryPoint.chatActions().reply(conversationId, text, threadRoot)
            notifier.showReplySent(conversationId, threadRoot, text)
        } catch (cancelled: CancellationException) {
            notifier.showReplyFailed(conversationId, threadRoot)
            throw cancelled
        } catch (ignored: Exception) {
            notifier.showReplyFailed(conversationId, threadRoot)
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun markRead(entryPoint: NotificationsEntryPoint, intent: Intent, conversationId: Long) {
        val messageId = DeepLinkUris.positiveId(intent.getStringExtra(EXTRA_MESSAGE_ID))
        entryPoint.notifier().cancelConversation(conversationId)
        try {
            entryPoint.chatActions().markRead(conversationId, messageId)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (ignored: Exception) {
            // The notification is already gone; the conversation is marked read when next opened.
        }
    }

    companion object {
        const val KEY_REPLY_TEXT = "com.waivera.waivera.notification.REPLY_TEXT"
        internal const val ACTION_REPLY = "com.waivera.waivera.notification.REPLY"
        internal const val ACTION_MARK_READ = "com.waivera.waivera.notification.MARK_READ"
        private const val EXTRA_CONVERSATION_ID = "conversationId"
        private const val EXTRA_THREAD_ROOT_ID = "threadRootMessageId"
        private const val EXTRA_MESSAGE_ID = "messageId"
        private const val ACTION_TIMEOUT_MILLIS = 8_000L

        internal fun replyIntent(context: Context, conversationId: Long, threadRootMessageId: Long?): Intent =
            Intent(context, NotificationActionReceiver::class.java)
                .setAction(ACTION_REPLY)
                .putExtra(EXTRA_CONVERSATION_ID, conversationId.toString())
                .putExtra(EXTRA_THREAD_ROOT_ID, threadRootMessageId?.toString())

        internal fun markReadIntent(context: Context, conversationId: Long, messageId: Long?): Intent =
            Intent(context, NotificationActionReceiver::class.java)
                .setAction(ACTION_MARK_READ)
                .putExtra(EXTRA_CONVERSATION_ID, conversationId.toString())
                .putExtra(EXTRA_MESSAGE_ID, messageId?.toString())
    }
}
