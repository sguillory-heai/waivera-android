package com.waivera.waivera.core.notifications.presentation

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import com.waivera.waivera.core.notifications.R
import com.waivera.waivera.core.notifications.channel.WaiveraChannel
import com.waivera.waivera.core.notifications.deeplink.DeepLinkTarget
import com.waivera.waivera.core.notifications.payload.PushMessage
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds and posts Waivera notifications.
 *
 * Chat: one `MessagingStyle` notification per conversation (tag `chat:{id}`), so later messages
 * update it in place instead of stacking; messages are de-duplicated by message id and capped at
 * [MAX_MESSAGES]. Chat notifications share a group with a summary, carry inline Reply and Mark
 * read, and show a public version without sender or text on the lock screen.
 */
@Singleton
class AndroidNotifier @Inject constructor(@ApplicationContext private val context: Context) : Notifier {
    private val manager = NotificationManagerCompat.from(context)
    private val intents = NotificationIntents(context)

    override fun canPost(): Boolean = manager.areNotificationsEnabled() && hasPostPermission()

    override fun show(message: PushMessage, channel: WaiveraChannel, conversation: ConversationNotificationInfo?) {
        when (message) {
            is PushMessage.Chat -> showChat(message, channel, conversation)
            else -> showGeneric(message, channel)
        }
    }

    override fun showReplySent(conversationId: Long, threadRootMessageId: Long?, text: String) {
        val existing = activeChat(conversationId)
        val state = ChatNotificationExtras.from(existing)
        val style = existing?.let(NotificationCompat.MessagingStyle::extractMessagingStyleFromNotification)
            ?: messagingStyle(context, conversation = null)
        style.addMessage(NotificationCompat.MessagingStyle.Message(text, System.currentTimeMillis(), null as Person?))
        val target = state.target ?: DeepLinkTarget.Conversation(conversationId)
        val notification = chatBuilder(
            conversationId,
            threadRootMessageId,
            state.messageIds.lastOrNull(),
            target,
            state.channel,
        )
            .setStyle(style)
            .setOnlyAlertOnce(true)
            .addExtras(state.copy(target = target).toBundle())
            .build()
        post(chatTag(conversationId), notification)
    }

    override fun showReplyFailed(conversationId: Long, threadRootMessageId: Long?) {
        val existing = activeChat(conversationId)
        val state = ChatNotificationExtras.from(existing)
        val target = state.target ?: DeepLinkTarget.Conversation(conversationId)
        val notification = chatBuilder(
            conversationId,
            threadRootMessageId,
            state.messageIds.lastOrNull(),
            target,
            state.channel,
        )
            .setContentTitle(existing?.extras?.getCharSequence(Notification.EXTRA_TITLE))
            .setContentText(context.getString(R.string.core_notifications_reply_failed))
            .setOnlyAlertOnce(true)
            .addExtras(state.copy(target = target).toBundle())
            .build()
        post(chatTag(conversationId), notification)
    }

    override fun cancelConversation(conversationId: Long) {
        manager.cancel(chatTag(conversationId), NOTIFICATION_ID)
        if (activeChatCount() == 0) manager.cancel(SUMMARY_TAG, NOTIFICATION_ID)
    }

    override fun cancelAll() {
        manager.cancelAll()
    }

    private fun showChat(
        message: PushMessage.Chat,
        channel: WaiveraChannel,
        conversation: ConversationNotificationInfo?,
    ) {
        val existing = activeChat(message.conversationId)
        val state = ChatNotificationExtras.from(existing)
        if (message.messageId != null && message.messageId in state.messageIds) return

        val style = existing?.let(NotificationCompat.MessagingStyle::extractMessagingStyleFromNotification)
            ?: messagingStyle(context, conversation)
        val senderName = message.title ?: context.getString(R.string.core_notifications_someone)
        val sender = Person.Builder().setName(senderName).setKey("sender:$senderName").build()
        val text = message.body ?: context.getString(R.string.core_notifications_new_message)
        style.addMessage(NotificationCompat.MessagingStyle.Message(text, System.currentTimeMillis(), sender))
        while (style.messages.size > MAX_MESSAGES) style.messages.removeAt(0)

        val extras = ChatNotificationExtras(
            messageIds = (state.messageIds + listOfNotNull(message.messageId)).takeLast(MAX_MESSAGES),
            target = message.target,
            channel = channel,
        )
        val notification = chatBuilder(
            message.conversationId,
            message.threadRootMessageId,
            message.messageId,
            message.target,
            channel,
        )
            .setStyle(style)
            .addExtras(extras.toBundle())
            .build()
        post(chatTag(message.conversationId), notification)
        postSummary()
    }

    private fun showGeneric(message: PushMessage, channel: WaiveraChannel) {
        val category = when (message) {
            is PushMessage.IncomingCall -> NotificationCompat.CATEGORY_CALL
            else -> NotificationCompat.CATEGORY_EVENT
        }
        val notification = baseBuilder(channel)
            .setContentTitle(message.title ?: context.getString(R.string.core_notifications_summary_title))
            .setContentText(message.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message.body))
            .setAutoCancel(true)
            .setCategory(category)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion(channel))
            .setContentIntent(intents.open(message.target, requestCode = message.target.hashCode()))
            .build()
        post("$GENERIC_TAG_PREFIX${message.target.hashCode()}", notification)
    }

    private fun chatBuilder(
        conversationId: Long,
        threadRootMessageId: Long?,
        messageId: Long?,
        target: DeepLinkTarget,
        channel: WaiveraChannel?,
    ): NotificationCompat.Builder {
        val resolvedChannel = channel ?: WaiveraChannel.Messages
        return baseBuilder(resolvedChannel)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setGroup(CHAT_GROUP)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion(resolvedChannel))
            .setContentIntent(intents.open(target, requestCode = conversationId.hashCode()))
            .addAction(intents.reply(conversationId, threadRootMessageId))
            .addAction(intents.markRead(conversationId, messageId))
    }

    private fun postSummary() {
        val count = activeChatCount()
        if (count == 0) return
        val summary = baseBuilder(WaiveraChannel.Messages)
            .setContentTitle(context.getString(R.string.core_notifications_summary_title))
            .setContentText(
                context.resources.getQuantityString(R.plurals.core_notifications_summary_text, count, count),
            )
            .setGroup(CHAT_GROUP)
            .setGroupSummary(true)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
            .setAutoCancel(true)
            .setContentIntent(intents.open(DeepLinkTarget.Inbox, requestCode = SUMMARY_REQUEST_CODE))
            .build()
        post(SUMMARY_TAG, summary)
    }

    private fun publicVersion(channel: WaiveraChannel): Notification = baseBuilder(channel)
        .setContentTitle(context.getString(R.string.core_notifications_summary_title))
        .setContentText(context.getString(R.string.core_notifications_new_message))
        .build()

    private fun baseBuilder(channel: WaiveraChannel): NotificationCompat.Builder =
        NotificationCompat.Builder(context, channel.id)
            .setSmallIcon(R.drawable.core_notifications_ic_notification)
            .setColor(ContextCompat.getColor(context, R.color.core_notifications_accent))

    private fun activeChat(conversationId: Long): Notification? = context.getSystemService<NotificationManager>()
        ?.activeNotifications
        ?.firstOrNull { it.tag == chatTag(conversationId) && it.id == NOTIFICATION_ID }
        ?.notification

    private fun activeChatCount(): Int = context.getSystemService<NotificationManager>()
        ?.activeNotifications
        ?.count { it.tag?.startsWith(CHAT_TAG_PREFIX) == true }
        ?: 0

    private fun hasPostPermission(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED

    private fun post(tag: String, notification: Notification) {
        // Checked on every post: the permission can be revoked while the app is running.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        manager.notify(tag, NOTIFICATION_ID, notification)
    }

    companion object {
        const val CHAT_GROUP = "com.waivera.waivera.CHAT"
        const val MAX_MESSAGES = 7
        internal const val NOTIFICATION_ID = 1
        internal const val CHAT_TAG_PREFIX = "chat:"
        internal const val GENERIC_TAG_PREFIX = "push:"
        internal const val SUMMARY_TAG = "chat-summary"
        private const val SUMMARY_REQUEST_CODE = -1

        fun chatTag(conversationId: Long): String = "$CHAT_TAG_PREFIX$conversationId"
    }
}

private fun messagingStyle(
    context: Context,
    conversation: ConversationNotificationInfo?,
): NotificationCompat.MessagingStyle {
    val me = Person.Builder().setName(context.getString(R.string.core_notifications_you)).setKey("self").build()
    return NotificationCompat.MessagingStyle(me).apply {
        if (conversation?.isGroup == true) {
            conversationTitle = conversation.title
            isGroupConversation = true
        }
    }
}
