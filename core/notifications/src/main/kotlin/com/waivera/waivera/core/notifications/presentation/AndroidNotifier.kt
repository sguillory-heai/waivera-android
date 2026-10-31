package com.waivera.waivera.core.notifications.presentation

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.content.ContextCompat
import androidx.core.content.LocusIdCompat
import androidx.core.content.getSystemService
import com.waivera.waivera.core.notifications.PushStep
import com.waivera.waivera.core.notifications.PushTrace
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
 * [MAX_MESSAGES]. Each names a conversation shortcut, so Android 11+ shows it as a conversation
 * with the sender's picture, like a text message. Chat notifications share a group, with a summary
 * only while two or more conversations are showing; they carry inline Reply and Mark read, and
 * show a public version without sender or text when the lock screen hides sensitive content.
 */
@Singleton
class AndroidNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val trace: PushTrace,
) : Notifier {
    private val manager = NotificationManagerCompat.from(context)
    private val intents = NotificationIntents(context)
    private val shortcuts = ConversationShortcuts(context)
    private val main = Handler(Looper.getMainLooper())

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
            .conversationShortcut(existing?.shortcutId)
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
            .conversationShortcut(existing?.shortcutId)
            .setContentTitle(existing?.extras?.getCharSequence(Notification.EXTRA_TITLE))
            .setContentText(context.getString(R.string.core_notifications_reply_failed))
            .setOnlyAlertOnce(true)
            .addExtras(state.copy(target = target).toBundle())
            .build()
        post(chatTag(conversationId), notification)
    }

    override fun cancelConversation(conversationId: Long) {
        val tag = chatTag(conversationId)
        if (activeChat(conversationId) != null) trace.note(PushStep.Cleared, null)
        manager.cancel(tag, NOTIFICATION_ID)
        // The system list can still hold the notification just cancelled, so it is left out by
        // tag; counting it once left the summary behind as a bare "Waivera" notification.
        syncSummary(activeChatTags() - tag)
    }

    override fun cancelAll() {
        manager.cancelAll()
        runCatching { shortcuts.removeAll() }
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
        // In a group the collapsed line is "<sender>: <text>", so a long sender is shortened to
        // leave the text room; the key keeps the full name, which identifies the sender.
        val shownName = if (style.isGroupConversation) SenderName.short(context, senderName) else senderName
        // The picture and the conversation shortcut only dress the notification; if a device
        // refuses either, the message must still show in the plain layout.
        val sender = Person.Builder()
            .setName(shownName)
            .setKey("sender:$senderName")
            .setIcon(runCatching { InitialsAvatar.icon(context, senderName) }.getOrNull())
            .build()
        val text = message.body ?: context.getString(R.string.core_notifications_new_message)
        style.addMessage(NotificationCompat.MessagingStyle.Message(text, System.currentTimeMillis(), sender))
        while (style.messages.size > MAX_MESSAGES) style.messages.removeAt(0)
        style.dropOlderPictures()

        val extras = ChatNotificationExtras(
            messageIds = (state.messageIds + listOfNotNull(message.messageId)).takeLast(MAX_MESSAGES),
            target = message.target,
            channel = channel,
        )
        val groupTitle = style.conversationTitle?.toString()?.takeIf { style.isGroupConversation }
        val shortcutId = runCatching {
            shortcuts.publish(
                conversationId = message.conversationId,
                label = groupTitle ?: senderName,
                person = sender.takeIf { groupTitle == null },
            )
        }.getOrNull()
        val notification = chatBuilder(
            message.conversationId,
            message.threadRootMessageId,
            message.messageId,
            message.target,
            channel,
        )
            .conversationShortcut(shortcutId)
            .setStyle(style)
            .addExtras(extras.toBundle())
            .build()
        val tag = chatTag(message.conversationId)
        post(tag, notification)
        // The system can drop a post without telling the app (a blocked channel or conversation);
        // the trace says so, so a tester can tell that from a push that never came.
        main.postDelayed({
            val posted = activeChat(message.conversationId) != null
            noteUndelivered(context, trace, posted, channel.id, shortcutId)
        }, CHECK_DELAY_MILLIS)
        // The system list may not show the post above yet, so its tag is added by hand.
        syncSummary(activeChatTags() + tag)
    }

    private fun showGeneric(message: PushMessage, channel: WaiveraChannel) {
        // An unknown push with neither title nor text has nothing to say; a bare "Waivera" with
        // an empty line looks broken.
        if (message is PushMessage.Unknown && message.title == null && message.body == null) return
        val body = message.body ?: context.getString(R.string.core_notifications_new_message)
        val category = when (message) {
            is PushMessage.IncomingCall -> NotificationCompat.CATEGORY_CALL
            else -> NotificationCompat.CATEGORY_EVENT
        }
        val notification = baseBuilder(channel)
            .setContentTitle(message.title ?: context.getString(R.string.core_notifications_summary_title))
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
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

    /**
     * The system bundles a group of two or more on its own, but a summary is what keeps them
     * together on every launcher; with one conversation it only risks being left behind alone.
     */
    private fun syncSummary(chatTags: Set<String>) {
        val count = chatTags.size
        if (count < MIN_FOR_SUMMARY) {
            manager.cancel(SUMMARY_TAG, NOTIFICATION_ID)
            return
        }
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

    private fun activeChatTags(): Set<String> = context.getSystemService<NotificationManager>()
        ?.activeNotifications
        ?.mapNotNull { it.tag?.takeIf { tag -> tag.startsWith(CHAT_TAG_PREFIX) } }
        ?.toSet()
        .orEmpty()

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
        private const val MIN_FOR_SUMMARY = 2
        private const val CHECK_DELAY_MILLIS = 1_500L
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
    // MessagingStyle declares its own apply(builder), which shadows the scope function.
    val style = NotificationCompat.MessagingStyle(me)
    if (conversation?.isGroup == true) {
        style.setConversationTitle(conversation.title)
        style.setGroupConversation(true)
    }
    return style
}

private fun NotificationCompat.Builder.conversationShortcut(shortcutId: String?): NotificationCompat.Builder =
    if (shortcutId == null) this else setShortcutId(shortcutId).setLocusId(LocusIdCompat(shortcutId))

/**
 * Every message carries its sender, picture included, and the whole notification crosses one
 * binder call: seven pictures came close to its 1 MB limit, and a post over it fails silently.
 * Only the newest message keeps its picture; the system shows it for the conversation.
 */
private fun NotificationCompat.MessagingStyle.dropOlderPictures() {
    val all = messages
    for (index in 0 until all.size - 1) {
        val message = all[index]
        val person = message.person
        if (person?.icon != null) {
            all[index] = NotificationCompat.MessagingStyle.Message(
                message.text,
                message.timestamp,
                person.toBuilder().setIcon(null).build(),
            )
        }
    }
}

/** Notes a chat alert the system dropped or is hiding, with the settings that decide it (numbers only). */
private fun noteUndelivered(
    context: Context,
    trace: PushTrace,
    posted: Boolean,
    channelId: String,
    shortcutId: String?,
) {
    val system = context.getSystemService<NotificationManager>() ?: return
    val step = when {
        !posted -> PushStep.Gone
        system.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL -> PushStep.Muted
        else -> return
    }
    val conversationImportance = if (shortcutId != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        system.getNotificationChannel(channelId, shortcutId)?.importance
    } else {
        null
    }
    val detail = "channel ${system.getNotificationChannel(channelId)?.importance}, " +
        "conversation $conversationImportance, dnd ${system.currentInterruptionFilter}, " +
        "app ${system.areNotificationsEnabled()}"
    trace.note(step, detail)
}
