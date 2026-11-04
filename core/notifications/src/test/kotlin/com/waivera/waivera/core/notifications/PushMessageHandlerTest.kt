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
import com.waivera.waivera.core.notifications.presentation.ReadSyncSink
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
    private val notes = mutableListOf<Pair<PushStep, String?>>()
    private val reads = FakeReadSync()
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
        trace = { step, detail -> notes += step to detail },
        reads = reads,
    )
    private val chatData = PushFixtures.load("chat-message.json").data

    @Test
    fun `every push leaves a note of where it ended`() = runTest {
        handler.handle(chatData)
        signedIn = false
        handler.handle(chatData)
        handler.handle(mapOf("type" to "chat"))
        assertEquals(
            listOf(PushStep.Shown to null, PushStep.Hidden to "SignedOut", PushStep.Unusable to null),
            notes.filter { it.first != PushStep.Received },
        )
        assertEquals(3, notes.count { it.first == PushStep.Received }, "every payload is noted as it arrives")
        val received = notes.first { it.first == PushStep.Received }.second.orEmpty()
        assertTrue("keys [" in received && "Chat" in received, received)
    }

    @Test
    fun `a read on another device is applied silently and never shown`() = runTest {
        val chat = handler.handle(mapOf("type" to "chat.read", "conversationId" to "7", "threadRootMessageId" to "31"))
        val one = handler.handle(mapOf("type" to "notification.read", "notificationId" to "n-1"))
        val all = handler.handle(mapOf("type" to "notification.read", "all" to "true"))
        val unusable = handler.handle(mapOf("type" to "notification.read"))

        listOf(chat, one, all).forEach {
            assertEquals(
                PresentationDecision.SuppressReason.ReadElsewhere,
                assertIs<PresentationDecision.Suppress>(it).reason,
            )
        }
        assertNull(unusable)
        assertEquals(listOf<Pair<Long, Long?>>(7L to 31L), reads.conversations)
        assertEquals(listOf<String?>("n-1", null), reads.notices)
        assertTrue(notifier.shown.isEmpty())
    }

    @Test
    fun `a read sync after sign-out changes nothing`() = runTest {
        signedIn = false
        handler.handle(mapOf("type" to "chat.read", "conversationId" to "7"))
        assertTrue(reads.conversations.isEmpty())
    }

    @Test
    fun `a push of an unknown type with nothing to say is dropped`() = runTest {
        val decision = handler.handle(mapOf("type" to "future.kind"))
        assertEquals(
            PresentationDecision.SuppressReason.Blank,
            assertIs<PresentationDecision.Suppress>(decision).reason,
        )
        assertTrue(notifier.shown.isEmpty())
    }

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
    fun `a main timeline message is shown while only one of its threads is on screen`() = runTest {
        hubConnected = true
        tracker.enter(conversationId = 7, threadRootMessageId = 9310)
        handler.handle(chatData)
        val shown = assertIs<PushMessage.Chat>(notifier.shown.single().first)
        assertNull(shown.threadRootMessageId)
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

private class FakeReadSync : ReadSyncSink {
    val conversations = mutableListOf<Pair<Long, Long?>>()
    val notices = mutableListOf<String?>()

    override suspend fun conversationRead(
        conversationId: Long,
        threadRootMessageId: Long?,
        readAt: java.time.Instant?,
    ) {
        conversations += conversationId to threadRootMessageId
    }

    override suspend fun noticeRead(notificationId: String?) {
        notices += notificationId
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

    override fun cancelChat(conversationId: Long, threadRootMessageId: Long?) {
        if (threadRootMessageId == null) cancelled += conversationId
    }

    override fun cancelNotice(notificationId: String?) = Unit

    override fun cancelAll() = Unit
}
