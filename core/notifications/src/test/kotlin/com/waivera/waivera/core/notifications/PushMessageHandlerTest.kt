package com.waivera.waivera.core.notifications

import com.waivera.waivera.core.notifications.channel.WaiveraChannel
import com.waivera.waivera.core.notifications.payload.PushMessage
import com.waivera.waivera.core.notifications.presentation.ActiveConversationTracker
import com.waivera.waivera.core.notifications.presentation.ClientNotificationPreferences
import com.waivera.waivera.core.notifications.presentation.ConversationInfoSource
import com.waivera.waivera.core.notifications.presentation.ConversationNotificationInfo
import com.waivera.waivera.core.notifications.presentation.NotificationPreferencesSource
import com.waivera.waivera.core.notifications.presentation.Notifier
import com.waivera.waivera.core.notifications.presentation.PresentationDecision
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class PushMessageHandlerTest {
    private val notifier = FakeNotifier()
    private var preferences = ClientNotificationPreferences()
    private var hubConnected = false
    private val conversations = mutableMapOf<Long, ConversationNotificationInfo>()
    private var signedIn = true
    private val threadRoots = mutableMapOf<Long, Long>()
    private val tracker = ActiveConversationTracker(notifier)
    private val handler = PushMessageHandler(
        notifier = notifier,
        preferences = object : NotificationPreferencesSource {
            override suspend fun current() = preferences
        },
        conversations = object : ConversationInfoSource {
            override suspend fun find(conversationId: Long) = conversations[conversationId]
        },
        hub = { hubConnected },
        tracker = tracker,
        session = { signedIn },
        threads = { messageId -> threadRoots[messageId] },
    )
    private val chatData = PushFixtures.load("chat-message.json").data

    @Test
    fun `a chat push in the background is shown on the messages channel`() = runTest {
        handler.handle(chatData)
        assertEquals(listOf(WaiveraChannel.Messages), notifier.shown.map { it.second })
        assertIs<PushMessage.Chat>(notifier.shown.single().first)
    }

    @Test
    fun `a group conversation uses group activity and passes the cached title`() = runTest {
        conversations[7] = ConversationNotificationInfo(title = "North team", isGroup = true)
        handler.handle(chatData)
        assertEquals(WaiveraChannel.GroupActivity, notifier.shown.single().second)
        assertEquals("North team", notifier.shownConversations.single()?.title)
    }

    @Test
    fun `the conversation on screen with the hub connected shows nothing`() = runTest {
        hubConnected = true
        tracker.enter(conversationId = 7)
        val decision = handler.handle(chatData)
        assertIs<PresentationDecision.Suppress>(decision)
        assertTrue(notifier.shown.isEmpty())
    }

    @Test
    fun `the conversation on screen with the hub down still notifies`() = runTest {
        tracker.enter(conversationId = 7)
        handler.handle(chatData)
        assertEquals(1, notifier.shown.size)
    }

    @Test
    fun `leaving the conversation re-enables notifications for it`() = runTest {
        hubConnected = true
        tracker.enter(conversationId = 7)
        tracker.leave(conversationId = 7)
        handler.handle(chatData)
        assertEquals(1, notifier.shown.size)
    }

    @Test
    fun `leaving a different conversation keeps the visible one`() {
        tracker.enter(conversationId = 7)
        tracker.leave(conversationId = 8)
        assertEquals(7L, tracker.visible.value?.conversationId)
    }

    @Test
    fun `entering a conversation clears its notification`() {
        tracker.enter(conversationId = 7)
        assertEquals(listOf(7L), notifier.cancelled)
    }

    @Test
    fun `nothing is posted without notification permission`() = runTest {
        notifier.allowed = false
        val decision = handler.handle(chatData)
        assertIs<PresentationDecision.Show>(decision)
        assertTrue(notifier.shown.isEmpty())
    }

    @Test
    fun `push turned off in settings shows nothing`() = runTest {
        preferences = ClientNotificationPreferences(pushEnabled = false)
        handler.handle(chatData)
        assertTrue(notifier.shown.isEmpty())
    }

    @Test
    fun `an unusable payload is ignored`() = runTest {
        assertNull(handler.handle(mapOf("type" to "chat", "conversationId" to "0")))
        assertTrue(notifier.shown.isEmpty())
    }

    @Test
    fun `nothing is shown while nobody is signed in`() = runTest {
        signedIn = false
        val decision = handler.handle(chatData)
        val suppressed = assertIs<PresentationDecision.Suppress>(decision)
        assertEquals(PresentationDecision.SuppressReason.SignedOut, suppressed.reason)
        assertTrue(notifier.shown.isEmpty())
    }

    @Test
    fun `a thread reply is shown while only its main conversation is open`() = runTest {
        // The payload has no thread id (AF-12); the cache knows message 9312 is a reply.
        threadRoots[9312] = 9310
        hubConnected = true
        tracker.enter(conversationId = 7)
        handler.handle(chatData)
        val shown = assertIs<PushMessage.Chat>(notifier.shown.single().first)
        assertEquals(9310L, shown.threadRootMessageId, "a tap opens the thread")
    }

    @Test
    fun `a thread reply is suppressed while that thread is on screen`() = runTest {
        threadRoots[9312] = 9310
        hubConnected = true
        tracker.enter(conversationId = 7, threadRootMessageId = 9310)
        assertIs<PresentationDecision.Suppress>(handler.handle(chatData))
        assertTrue(notifier.shown.isEmpty())
    }

    @Test
    fun `legacy pushes use the notification block for their text`() = runTest {
        val fixture = PushFixtures.load("push-legacy-meeting.json")
        handler.handle(fixture.data, fixture.title, fixture.body)
        val (message, channel) = notifier.shown.single()
        assertEquals(WaiveraChannel.ScheduleChanges, channel)
        assertEquals("Meeting scheduled", message.title)
    }
}

private class FakeNotifier : Notifier {
    var allowed = true
    val shown = mutableListOf<Pair<PushMessage, WaiveraChannel>>()
    val shownConversations = mutableListOf<ConversationNotificationInfo?>()
    val cancelled = mutableListOf<Long>()

    override fun canPost(): Boolean = allowed

    override fun show(message: PushMessage, channel: WaiveraChannel, conversation: ConversationNotificationInfo?) {
        shown += message to channel
        shownConversations += conversation
    }

    override fun showReplySent(conversationId: Long, threadRootMessageId: Long?, text: String) = Unit

    override fun showReplyFailed(conversationId: Long, threadRootMessageId: Long?) = Unit

    override fun cancelConversation(conversationId: Long) {
        cancelled += conversationId
    }

    override fun cancelAll() = Unit
}
