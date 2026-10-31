package com.waivera.waivera.core.notifications

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import androidx.core.app.NotificationCompat
import androidx.core.content.pm.ShortcutManagerCompat
import com.waivera.waivera.core.notifications.channel.NotificationChannelInstaller
import com.waivera.waivera.core.notifications.channel.WaiveraChannel
import com.waivera.waivera.core.notifications.payload.PushMessage
import com.waivera.waivera.core.notifications.presentation.AndroidNotifier
import com.waivera.waivera.core.notifications.presentation.ConversationNotificationInfo
import com.waivera.waivera.core.notifications.presentation.ConversationShortcuts
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AndroidNotifierTest {
    private val context: Application = RuntimeEnvironment.getApplication()
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val notes = mutableListOf<PushStep>()
    private val notifier = AndroidNotifier(context) { step, _ -> notes += step }

    private fun chat(messageId: Long, body: String, conversationId: Long = 7) = PushMessage.Chat(
        conversationId = conversationId,
        messageId = messageId,
        threadRootMessageId = null,
        title = "Alex Rivera",
        body = body,
    )

    private fun posted(tag: String): Notification? =
        manager.activeNotifications.firstOrNull { it.tag == tag }?.notification

    private fun postedChat(conversationId: Long): Notification =
        assertNotNull(posted(AndroidNotifier.chatTag(conversationId)))

    @Before
    fun setUp() {
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        NotificationChannelInstaller(context).install()
    }

    @Test
    fun `a chat push becomes a messaging style notification with reply and mark read`() {
        notifier.show(chat(9312, "can you cover Friday?"), WaiveraChannel.Messages, conversation = null)
        val notification = assertNotNull(posted(AndroidNotifier.chatTag(7)))
        assertEquals(WaiveraChannel.Messages.id, notification.channelId)
        assertEquals(NotificationCompat.CATEGORY_MESSAGE, notification.category)
        val style = assertNotNull(NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification))
        assertEquals(listOf("can you cover Friday?"), style.messages.map { it.text.toString() })
        val actions = (0 until NotificationCompat.getActionCount(notification))
            .map { NotificationCompat.getAction(notification, it) }
        assertEquals(
            listOf(
                NotificationCompat.Action.SEMANTIC_ACTION_REPLY,
                NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ,
            ),
            actions.map { it?.semanticAction },
        )
        assertNotNull(actions.first()?.remoteInputs?.singleOrNull())
        assertNotNull(notification.publicVersion, "lock screen shows a redacted version")
    }

    @Test
    fun `reply and mark read need an unlocked device`() {
        notifier.show(chat(9312, "can you cover Friday?"), WaiveraChannel.Messages, conversation = null)
        val actions = postedChat(7).actions.orEmpty()
        assertEquals(2, actions.size)
        assertTrue(actions.all { it.isAuthenticationRequired }, "no replying as the employee from a locked phone")
    }

    @Test
    fun `later messages update the same notification`() {
        notifier.show(chat(1, "first"), WaiveraChannel.Messages, null)
        notifier.show(chat(2, "second"), WaiveraChannel.Messages, null)
        val chats = manager.activeNotifications.filter { it.tag == AndroidNotifier.chatTag(7) }
        assertEquals(1, chats.size)
        val style = assertNotNull(
            NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(chats.single().notification),
        )
        assertEquals(listOf("first", "second"), style.messages.map { it.text.toString() })
    }

    @Test
    fun `the same message delivered twice is shown once`() {
        notifier.show(chat(1, "first"), WaiveraChannel.Messages, null)
        notifier.show(chat(1, "first"), WaiveraChannel.Messages, null)
        val style = assertNotNull(
            NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(postedChat(7)),
        )
        assertEquals(1, style.messages.size)
    }

    @Test
    fun `messages are capped`() {
        (1L..12L).forEach { notifier.show(chat(it, "message $it"), WaiveraChannel.Messages, null) }
        val style = assertNotNull(
            NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(postedChat(7)),
        )
        assertEquals(AndroidNotifier.MAX_MESSAGES, style.messages.size)
        assertEquals("message 12", style.messages.last().text.toString())
    }

    @Test
    fun `chat notifications are grouped per conversation with a summary`() {
        notifier.show(chat(1, "hi", conversationId = 7), WaiveraChannel.Messages, null)
        notifier.show(
            chat(2, "hey", conversationId = 8),
            WaiveraChannel.GroupActivity,
            ConversationNotificationInfo("North team", true),
        )
        assertNotNull(posted(AndroidNotifier.chatTag(7)))
        val group = assertNotNull(posted(AndroidNotifier.chatTag(8)))
        assertEquals(AndroidNotifier.CHAT_GROUP, group.group)
        val style = assertNotNull(NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(group))
        assertEquals("North team", style.conversationTitle?.toString())
        assertTrue(style.isGroupConversation)
        val summary = manager.activeNotifications.single { sbn ->
            sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0
        }
        assertEquals(AndroidNotifier.CHAT_GROUP, summary.notification.group)
    }

    @Test
    fun `only the newest message keeps its picture`() {
        (1L..3L).forEach { notifier.show(chat(it, "message $it"), WaiveraChannel.Messages, null) }
        val style =
            assertNotNull(NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(postedChat(7)))
        assertEquals(listOf(false, false, true), style.messages.map { it.person?.icon != null })
    }

    @Test
    fun `clearing a showing chat alert is traced, clearing nothing is not`() {
        notifier.cancelConversation(7)
        notifier.show(chat(1, "hi"), WaiveraChannel.Messages, null)
        notifier.cancelConversation(7)
        assertEquals(listOf(PushStep.Cleared), notes)
    }

    @Test
    fun `one conversation shows without a summary`() {
        notifier.show(chat(1, "hi"), WaiveraChannel.Messages, null)
        assertEquals(listOf(AndroidNotifier.chatTag(7)), manager.activeNotifications.map { it.tag })
    }

    @Test
    fun `the summary goes when fewer than two conversations are left`() {
        notifier.show(chat(1, "hi", conversationId = 7), WaiveraChannel.Messages, null)
        notifier.show(chat(2, "hey", conversationId = 8), WaiveraChannel.Messages, null)
        assertNotNull(posted(AndroidNotifier.SUMMARY_TAG))
        notifier.cancelConversation(7)
        assertNull(posted(AndroidNotifier.SUMMARY_TAG), "a lone summary showed as a bare \"Waivera\"")
        assertNotNull(posted(AndroidNotifier.chatTag(8)))
    }

    @Test
    fun `a chat notification is a conversation named after the sender`() {
        notifier.show(chat(1, "can you cover Friday?"), WaiveraChannel.Messages, null)
        val id = ConversationShortcuts.shortcutId(7)
        assertEquals(id, postedChat(7).shortcutId)
        val shortcut = ShortcutManagerCompat.getDynamicShortcuts(context).single { it.id == id }
        assertEquals("Alex Rivera", shortcut.shortLabel.toString())
        val style =
            assertNotNull(NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(postedChat(7)))
        assertNotNull(style.messages.single().person?.icon, "the sender has a picture")
    }

    @Test
    fun `a group conversation is named after the group`() {
        notifier.show(chat(1, "hey"), WaiveraChannel.GroupActivity, ConversationNotificationInfo("North team", true))
        val shortcut = ShortcutManagerCompat.getDynamicShortcuts(context)
            .single { it.id == ConversationShortcuts.shortcutId(7) }
        assertEquals("North team", shortcut.shortLabel.toString())
    }

    @Test
    fun `an inline reply keeps the conversation layout`() {
        notifier.show(chat(1, "can you cover Friday?"), WaiveraChannel.Messages, null)
        notifier.showReplySent(conversationId = 7, threadRootMessageId = null, text = "Yes")
        assertEquals(ConversationShortcuts.shortcutId(7), postedChat(7).shortcutId)
    }

    @Test
    fun `an unknown push with nothing to say is not shown`() {
        notifier.show(PushMessage.Unknown("ping", null, null), WaiveraChannel.Digest, null)
        assertTrue(manager.activeNotifications.isEmpty())
    }

    @Test
    fun `a generic push without text still has a line under the title`() {
        notifier.show(PushMessage.Unknown("announcement", "Office closed", null), WaiveraChannel.Digest, null)
        val notification = manager.activeNotifications.single().notification
        assertEquals("New message", notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString())
    }

    @Test
    fun `opening the conversation clears its notification and the summary when it was the last`() {
        notifier.show(chat(1, "hi"), WaiveraChannel.Messages, null)
        notifier.cancelConversation(7)
        assertNull(posted(AndroidNotifier.chatTag(7)))
        assertTrue(manager.activeNotifications.isEmpty())
    }

    @Test
    fun `an inline reply is appended as the person's own message`() {
        notifier.show(chat(1, "can you cover Friday?"), WaiveraChannel.Messages, null)
        notifier.showReplySent(conversationId = 7, threadRootMessageId = null, text = "Yes")
        val style = assertNotNull(
            NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(postedChat(7)),
        )
        assertEquals(listOf("can you cover Friday?", "Yes"), style.messages.map { it.text.toString() })
        assertNull(style.messages.last().person, "a null person is the device user")
    }

    @Test
    fun `sign-out clears every notification`() {
        notifier.show(chat(1, "hi"), WaiveraChannel.Messages, null)
        notifier.show(PushMessage.Unknown("announcement", "Office closed", null), WaiveraChannel.Digest, null)
        notifier.cancelAll()
        assertTrue(manager.activeNotifications.isEmpty())
        assertTrue(ShortcutManagerCompat.getDynamicShortcuts(context).isEmpty(), "no names left for the next account")
    }

    @Test
    fun `nothing is posted when the permission is revoked`() {
        shadowOf(context).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        notifier.show(chat(1, "hi"), WaiveraChannel.Messages, null)
        assertTrue(manager.activeNotifications.isEmpty())
        assertTrue(!notifier.canPost())
    }
}
