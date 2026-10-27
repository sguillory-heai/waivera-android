package com.waivera.waivera.core.notifications.presentation

import android.app.Notification
import android.os.Bundle
import com.waivera.waivera.core.notifications.channel.WaiveraChannel
import com.waivera.waivera.core.notifications.deeplink.DeepLinkTarget
import com.waivera.waivera.core.notifications.deeplink.DeepLinkUris

/**
 * State stored on a posted chat notification, so the next push for the same conversation can
 * de-duplicate by message id and keep its tap target and channel.
 */
internal data class ChatNotificationExtras(
    val messageIds: List<Long>,
    val target: DeepLinkTarget?,
    val channel: WaiveraChannel?,
) {
    fun toBundle(): Bundle = Bundle().apply {
        putLongArray(KEY_MESSAGE_IDS, messageIds.toLongArray())
        putString(KEY_TARGET, target?.let(DeepLinkUris::toUri))
        putString(KEY_CHANNEL, channel?.id)
    }

    companion object {
        private const val KEY_MESSAGE_IDS = "com.waivera.waivera.notification.MESSAGE_IDS"
        private const val KEY_TARGET = "com.waivera.waivera.notification.TARGET"
        private const val KEY_CHANNEL = "com.waivera.waivera.notification.CHANNEL"

        val Empty = ChatNotificationExtras(messageIds = emptyList(), target = null, channel = null)

        fun from(notification: Notification?): ChatNotificationExtras {
            val extras = notification?.extras ?: return Empty
            return ChatNotificationExtras(
                messageIds = extras.getLongArray(KEY_MESSAGE_IDS)?.toList().orEmpty(),
                target = DeepLinkUris.parse(extras.getString(KEY_TARGET)),
                channel = WaiveraChannel.fromId(extras.getString(KEY_CHANNEL)),
            )
        }
    }
}
