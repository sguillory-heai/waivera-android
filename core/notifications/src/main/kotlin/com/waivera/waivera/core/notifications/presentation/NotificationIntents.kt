package com.waivera.waivera.core.notifications.presentation

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import androidx.core.net.toUri
import com.waivera.waivera.core.notifications.R
import com.waivera.waivera.core.notifications.action.NotificationActionReceiver
import com.waivera.waivera.core.notifications.deeplink.DeepLinkTarget
import com.waivera.waivera.core.notifications.deeplink.DeepLinkUris

/**
 * Pending intents for notification taps and actions. Every intent is explicit or restricted to
 * this package, and only the Reply intent is mutable (RemoteInput must attach the typed text).
 */
internal class NotificationIntents(private val context: Context) {

    fun open(target: DeepLinkTarget, requestCode: Int): PendingIntent {
        val intent = Intent(Intent.ACTION_VIEW, DeepLinkUris.toUri(target).toUri())
            .setPackage(context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun reply(conversationId: Long, threadRootMessageId: Long?): NotificationCompat.Action {
        val remoteInput = RemoteInput.Builder(NotificationActionReceiver.KEY_REPLY_TEXT)
            .setLabel(context.getString(R.string.core_notifications_reply_hint))
            .build()
        val pending = PendingIntent.getBroadcast(
            context,
            requestCode(conversationId, REQUEST_REPLY),
            NotificationActionReceiver.replyIntent(context, conversationId, threadRootMessageId),
            PendingIntent.FLAG_UPDATE_CURRENT or mutableFlag(),
        )
        return NotificationCompat.Action.Builder(
            R.drawable.core_notifications_ic_reply,
            context.getString(R.string.core_notifications_reply),
            pending,
        )
            .addRemoteInput(remoteInput)
            .setAllowGeneratedReplies(true)
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
            .setShowsUserInterface(false)
            // Replying sends as the employee, so a locked phone must be unlocked first (AS-06, API 31+).
            .setAuthenticationRequired(true)
            .build()
    }

    fun markRead(conversationId: Long, messageId: Long?): NotificationCompat.Action {
        val pending = PendingIntent.getBroadcast(
            context,
            requestCode(conversationId, REQUEST_MARK_READ),
            NotificationActionReceiver.markReadIntent(context, conversationId, messageId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Action.Builder(
            R.drawable.core_notifications_ic_mark_read,
            context.getString(R.string.core_notifications_mark_read),
            pending,
        )
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ)
            .setShowsUserInterface(false)
            .setAuthenticationRequired(true)
            .build()
    }

    private fun mutableFlag(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0

    private fun requestCode(conversationId: Long, action: Int): Int = conversationId.hashCode() * ACTION_KINDS + action

    private companion object {
        const val REQUEST_REPLY = 1
        const val REQUEST_MARK_READ = 2
        const val ACTION_KINDS = 4
    }
}
