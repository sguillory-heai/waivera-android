package com.waivera.waivera.core.notifications.presentation

import android.content.Context
import android.content.Intent
import androidx.core.app.Person
import androidx.core.content.LocusIdCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.net.toUri
import com.waivera.waivera.core.notifications.deeplink.DeepLinkTarget
import com.waivera.waivera.core.notifications.deeplink.DeepLinkUris

/**
 * Long-lived conversation shortcuts. Android 11+ draws a chat notification as a conversation (the
 * picture, sender and text, in the Conversations section, the way a text message looks) only when
 * it names one of these. Sign-out removes them all, so the next account never sees the names.
 */
internal class ConversationShortcuts(private val context: Context) {

    /** @return the shortcut id to put on the notification, or null when it could not be published. */
    fun publish(conversationId: Long, label: String, person: Person?): String? {
        val id = shortcutId(conversationId)
        val intent = Intent(
            Intent.ACTION_VIEW,
            DeepLinkUris.toUri(DeepLinkTarget.Conversation(conversationId)).toUri(),
        ).setPackage(context.packageName)
        val shortcut = ShortcutInfoCompat.Builder(context, id)
            .setShortLabel(label)
            .setLongLived(true)
            .setLocusId(LocusIdCompat(id))
            .setIcon(InitialsAvatar.icon(context, label))
            .setIntent(intent)
            .apply { if (person != null) setPerson(person) }
            .build()
        // The system can refuse while the app is rate limited; the notification still shows,
        // only in the plain layout.
        return if (pushOrFalse(shortcut)) id else null
    }

    fun removeAll() {
        val ids = ShortcutManagerCompat.getDynamicShortcuts(context).map(ShortcutInfoCompat::getId)
        if (ids.isNotEmpty()) ShortcutManagerCompat.removeLongLivedShortcuts(context, ids)
        ShortcutManagerCompat.removeAllDynamicShortcuts(context)
    }

    private fun pushOrFalse(shortcut: ShortcutInfoCompat): Boolean = try {
        ShortcutManagerCompat.pushDynamicShortcut(context, shortcut)
        true
    } catch (_: IllegalStateException) {
        false
    } catch (_: IllegalArgumentException) {
        false
    }

    companion object {
        fun shortcutId(conversationId: Long): String = "conversation:$conversationId"
    }
}
