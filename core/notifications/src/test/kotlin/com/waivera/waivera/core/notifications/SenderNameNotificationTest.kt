package com.waivera.waivera.core.notifications

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import com.waivera.waivera.core.notifications.channel.NotificationChannelInstaller
import com.waivera.waivera.core.notifications.channel.WaiveraChannel
import com.waivera.waivera.core.notifications.payload.PushMessage
import com.waivera.waivera.core.notifications.presentation.AndroidNotifier
import com.waivera.waivera.core.notifications.presentation.ConversationNotificationInfo
import com.waivera.waivera.core.notifications.presentation.SenderName
import com.waivera.waivera.core.testing.LongFixtures
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Group chat notifications with the long-name fixtures (wave 29 N6): the sender the system draws
 * before the text is short enough to leave the text room, while the person's key keeps the full
 * name and direct messages keep the full name as their title.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SenderNameNotificationTest {
    private val context: Application = RuntimeEnvironment.getApplication()
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val notifier = AndroidNotifier(context)

    @Before
    fun setUp() {
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        NotificationChannelInstaller(context).install()
    }

    private fun sender(name: String, group: Boolean, conversationId: Long): Person {
        notifier.show(
            PushMessage.Chat(
                conversationId = conversationId,
                messageId = conversationId * 10,
                threadRootMessageId = null,
                title = name,
                body = "On my way",
            ),
            if (group) WaiveraChannel.GroupActivity else WaiveraChannel.Messages,
            if (group) ConversationNotificationInfo("North team", isGroup = true) else null,
        )
        val posted = manager.activeNotifications.first { it.tag == AndroidNotifier.chatTag(conversationId) }
        val style =
            assertNotNull(NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(posted.notification))
        assertEquals("On my way", style.messages.last().text.toString(), "the text is kept whole")
        return assertNotNull(style.messages.last().person)
    }

    @Test
    fun `a long group sender shows as the first name and last initial`() {
        val person = sender(LongFixtures.NAME_60, group = true, conversationId = 1)
        assertEquals("Maria-Fernanda R.", person.name.toString())
        assertEquals("sender:${LongFixtures.NAME_60}", person.key)
    }

    @Test
    fun `every long group sender fits the cap and keeps its full name in the key`() {
        LongFixtures.NAMES.values.forEachIndexed { i, name ->
            val person = sender(name, group = true, conversationId = 10L + i)
            val shown = person.name.toString()
            assertTrue(shown.codePointCount(0, shown.length) <= SenderName.MAX_LENGTH, "\"$shown\" is too long")
            assertEquals("sender:$name", person.key)
            val paired = shown.indices.all { j ->
                when {
                    shown[j].isHighSurrogate() -> j + 1 < shown.length && shown[j + 1].isLowSurrogate()
                    shown[j].isLowSurrogate() -> j > 0 && shown[j - 1].isHighSurrogate()
                    else -> true
                }
            }
            assertTrue(paired, "\"$shown\" splits a code point")
        }
    }

    @Test
    fun `an unbroken group sender is cut with an ellipsis`() {
        val shown = sender(LongFixtures.NAME_UNBROKEN_40, group = true, conversationId = 2).name.toString()
        assertEquals(LongFixtures.NAME_UNBROKEN_40.take(SenderName.MAX_LENGTH - 1) + "…", shown)
    }

    @Test
    fun `two long words become the first word and an initial`() {
        val shown = sender(LongFixtures.NAME_TWO_LONG_WORDS, group = true, conversationId = 3).name.toString()
        assertEquals("Maximilianus W.", shown)
    }

    @Test
    fun `short group senders and emoji names are left as they are`() {
        listOf("Alex Rivera", "Bartholomew Fitzgerald", LongFixtures.NAME_EMOJI, LongFixtures.NAME_ONE_CHAR)
            .forEachIndexed { i, name ->
                assertEquals(name, sender(name, group = true, conversationId = 40L + i).name.toString())
            }
    }

    @Test
    fun `a direct message keeps the full name`() {
        val person = sender(LongFixtures.NAME_60, group = false, conversationId = 7)
        assertEquals(LongFixtures.NAME_60, person.name.toString())
    }

    @Test
    fun `the cut never splits a code point`() {
        val name = "🌻".repeat(SenderName.MAX_LENGTH + 4)
        val shown = SenderName.short(name, withInitial = { f, i -> "$f $i." }, cut = { "$it…" })
        assertEquals("🌻".repeat(SenderName.MAX_LENGTH - 1) + "…", shown)
        val initial = SenderName.short(
            "Maximilianus Wolfeschlegelsteinhausenberg 🌻flower",
            withInitial = { f, i -> "$f $i." },
            cut = { "$it…" },
        )
        assertEquals("Maximilianus 🌻.", initial)
    }
}
