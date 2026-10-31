package com.waivera.waivera.core.notifications.presentation

import android.app.Notification
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import com.waivera.waivera.core.notifications.R

/**
 * Debuggable builds only, and temporary: a chat alert that the system accepts but never draws
 * left no clue on a tester's phone, so beside the real alert each chat push also posts the same
 * message in simpler forms, each adding one feature. The first form that goes missing names the
 * feature the device cannot draw.
 */
internal class DebugAlertVariants(private val context: Context, private val postAlert: (String, Notification) -> Unit) {

    // Unit tests run debuggable too; they assert exact notification lists, so they get none.
    fun isOn(): Boolean =
        context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0 && Build.FINGERPRINT != "robolectric"

    /** [base] builds a fresh builder on the alert's channel; [full] is the real alert's builder. */
    fun post(
        sender: String,
        text: String,
        style: NotificationCompat.MessagingStyle,
        base: () -> NotificationCompat.Builder,
        full: () -> NotificationCompat.Builder,
    ) {
        val variants = listOf(
            "A plain" to base().setContentTitle(sender).setContentText(text),
            "B conversation layout" to base().setStyle(copy(style)),
            "C plus group" to base().setStyle(copy(style)).setGroup(AndroidNotifier.CHAT_GROUP),
            "D everything but group" to full().setGroup(null).setStyle(copy(style)),
            "E real alert" to full().setStyle(copy(style)),
        )
        variants.forEachIndexed { index, (name, builder) ->
            val label = context.getString(R.string.core_notifications_debug_variant, name)
            postAlert("$TAG_PREFIX$index", builder.setSubText(label).setOnlyAlertOnce(true).build())
        }
    }

    // A style can belong to only one builder, so each variant gets its own copy.
    private fun copy(style: NotificationCompat.MessagingStyle): NotificationCompat.MessagingStyle {
        val copy = NotificationCompat.MessagingStyle(style.user)
        copy.setConversationTitle(style.conversationTitle)
        copy.setGroupConversation(style.isGroupConversation)
        style.messages.forEach(copy::addMessage)
        return copy
    }

    companion object {
        const val TAG_PREFIX = "debug-variant:"
    }
}
