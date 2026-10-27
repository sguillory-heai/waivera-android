package com.waivera.waivera.core.data

import app.cash.turbine.test
import com.waivera.waivera.core.network.dto.BookmarkUpdateDto
import com.waivera.waivera.core.network.dto.ChatReadStateDto
import com.waivera.waivera.core.realtime.HubEvent
import com.waivera.waivera.core.realtime.HubEventDecoder
import com.waivera.waivera.core.testing.Fixtures
import com.waivera.waivera.core.testing.eventually
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

class RealtimeSyncTest {
    private val testScope = TestScope()
    private val graph = TestGraph(testScope.backgroundScope)
    private val sync = graph.realtimeSync

    @AfterTest
    fun tearDown() = graph.close()

    private fun event(name: String): HubEvent {
        val invocation = Fixtures.hub(name)
        return assertNotNull(HubEventDecoder.decode(invocation.target, invocation.arguments))
    }

    private suspend fun seedInbox() {
        graph.server.fixture("GET /api/chat/my-conversations", "rest-chat-get-my-conversations.json")
        graph.server.fixture("GET /api/chat/all", "rest-chat-get-all.json")
        graph.conversations.refresh().getOrThrow()
    }

    @Test
    fun everyHubFixtureAppliesWithoutError() = testScope.runTest {
        seedInbox()
        graph.server.fixture("GET /api/chat/conversation/41", "rest-chat-get-conversation-channel.json")
        graph.server.fixture("GET /api/chat/bookmarks", "rest-chat-get-bookmarks.json")
        val failures = mutableListOf<String>()
        com.waivera.waivera.core.network.log.WaiveraLog.sink =
            com.waivera.waivera.core.network.log.LogSink { _, tag, message, _ ->
                if (tag == "RealtimeSync") failures += message
            }
        try {
            Fixtures.names("hub-").filterNot { it.startsWith("hub-video-") }.forEach { sync.apply(event(it)) }
        } finally {
            com.waivera.waivera.core.network.log.WaiveraLog.sink =
                com.waivera.waivera.core.network.log.LogSink { _, _, _, _ -> }
        }
        assertEquals(emptyList(), failures)
    }

    @Test
    fun newMessageUpdatesPreviewAndBadgeOnce() = testScope.runTest {
        seedInbox()
        val before = graph.cache.conversation(7)?.unreadCount ?: 0
        val received = event("hub-receive-message.json") as HubEvent.MessageReceived
        val fromOther = received.copy(
            message = received.message.copy(id = 9999, conversationId = 7, userId = "usr_priya_raman", author = null),
        )
        sync.apply(fromOther)
        sync.apply(HubEvent.ConversationListMessage(fromOther.message))
        val after = graph.cache.conversation(7)
        assertEquals(9999L, after?.lastMessage?.messageId)
        assertEquals(before + 1, after?.unreadCount, "the two deliveries of one message count once")
        assertNotNull(graph.cache.message(9999))
    }

    @Test
    fun ownMessagesAndTheVisibleConversationDoNotBumpTheBadge() = testScope.runTest {
        seedInbox()
        val before = graph.cache.conversation(7)?.unreadCount ?: 0
        val base = (event("hub-receive-message.json") as HubEvent.MessageReceived).message
        sync.apply(
            HubEvent.MessageReceived(
                base.copy(id = 10_001, conversationId = 7, userId = "usr_alex_rivera", author = null),
            ),
        )
        graph.visible.set(7)
        sync.apply(
            HubEvent.MessageReceived(
                base.copy(id = 10_002, conversationId = 7, userId = "usr_priya_raman", author = null),
            ),
        )
        assertEquals(before, graph.cache.conversation(7)?.unreadCount)
    }

    @Test
    fun messageForAnUnknownConversationHydratesIt() = testScope.runTest {
        graph.server.fixture("GET /api/chat/conversation/7", "rest-chat-get-conversation.json")
        sync.apply(event("hub-receive-conversation-list-message.json"))
        eventually { graph.cache.conversation(7) != null }
    }

    @Test
    fun reactionUpdateEditDeleteApplyToCachedMessages() = testScope.runTest {
        graph.server.fixture("GET /api/chat/7/messages/pagination", "rest-chat-get-messages-pagination.json")
        graph.messages.refreshLatest(7).getOrThrow()
        sync.apply(event("hub-receive-reaction-added.json"))
        assertTrue(graph.cache.message(9312)?.reactions?.any { it.userId == "usr_jordan_lee" } == true)
        sync.apply(event("hub-receive-reaction-removed.json"))
        assertTrue(graph.cache.message(9312)?.reactions?.none { it.userId == "usr_jordan_lee" } == true)

        val update = event("hub-receive-message-update.json") as HubEvent.MessageUpdated
        sync.apply(HubEvent.MessageUpdated(update.update.copy(messageId = 9311, newContent = "edited")))
        assertEquals("edited", graph.cache.message(9311)?.text)
        assertTrue(graph.cache.message(9311)?.isEdited == true)

        val delete = event("hub-receive-message-delete.json") as HubEvent.MessageDeleted
        sync.apply(HubEvent.MessageDeleted(delete.delete.copy(messageId = 9311)))
        assertNull(graph.cache.message(9311)?.text)
    }

    @Test
    fun readStateClearsBadgesIncludingReadAll() = testScope.runTest {
        seedInbox()
        sync.apply(HubEvent.ChatReadState(ChatReadStateDto(conversationId = 7)))
        assertEquals(0, graph.cache.conversation(7)?.unreadCount)
        sync.apply(event("hub-receive-chat-read-state-read-all.json"))
        assertTrue(graph.cache.conversations().all { it.unreadCount == 0 })
    }

    @Test
    fun activityAddAndRemove() = testScope.runTest {
        sync.apply(event("hub-receive-chat-activity.json"))
        val added = graph.cache.allActivity().single()
        sync.apply(HubEvent.ChatActivityRemoved(added.id))
        assertTrue(graph.cache.allActivity().isEmpty())
    }

    @Test
    fun bookmarkUpdateWithoutIdsRefetches() = testScope.runTest {
        graph.server.fixture("GET /api/chat/bookmarks", "rest-chat-get-bookmarks.json")
        sync.apply(HubEvent.BookmarkUpdate(BookmarkUpdateDto()))
        assertEquals(1, graph.server.requestsTo("/api/chat/bookmarks").size)
    }

    @Test
    fun callStateEndClearsTheOngoingCall() = testScope.runTest {
        seedInbox()
        sync.apply(event("hub-conversation-call-state-changed.json"))
        val id = (event("hub-conversation-call-state-changed.json") as HubEvent.CallStateChanged).state.conversationId
        assertTrue(graph.cache.conversation(id)?.ongoingCall?.isLive == true)
        sync.apply(event("hub-conversation-call-state-changed-end.json"))
        assertNull(graph.cache.conversation(id)?.ongoingCall)
    }

    @Test
    fun conversationCreatedAppearsInTheInbox() = testScope.runTest {
        sync.apply(event("hub-conversation-created-channel.json"))
        assertNotNull(graph.cache.conversation(41))
    }

    @Test
    fun typingExpiresAfterFiveSeconds() = testScope.runTest {
        graph.conversations.observeTyping(7).test {
            assertEquals(emptySet(), awaitItem())
            sync.apply(HubEvent.TypingStatus(7, "usr_priya_raman", true))
            assertEquals(setOf("usr_priya_raman"), awaitItem())
            testScheduler.advanceTimeBy(5_001)
            runCurrent()
            assertEquals(emptySet(), awaitItem())
        }
    }

    @Test
    fun resyncRefetchesEveryOpenTimelineTail() = testScope.runTest {
        seedInbox()
        graph.server.fixture("GET /api/chat/7/messages/pagination", "rest-chat-get-messages-pagination.json")
        graph.server.json("GET /api/chat/recent-activity", "[]")
        graph.messages.observeTimeline(7).test {
            eventually { graph.server.requestsTo("/api/chat/7/messages/pagination").size == 1 }
            sync.resync()
            assertEquals(2, graph.server.requestsTo("/api/chat/7/messages/pagination").size)
            assertEquals(2, graph.server.requestsTo("/api/chat/my-conversations").size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun visibleConversationSuppressesPushOnlyWhenConnected() = testScope.runTest {
        val status = com.waivera.waivera.core.data.live.LiveRealtimeStatusRepository(
            graph.realtime,
            com.waivera.waivera.core.testing.FakeConnectivityMonitor(),
            graph.visible,
            backgroundScope,
        )
        status.setVisibleConversation(7)
        assertTrue(status.shouldSuppressNotification(7))
        assertTrue(!status.shouldSuppressNotification(8))
        graph.realtime.connected = false
        assertTrue(!status.shouldSuppressNotification(7))
        assertTrue(graph.conversations.observeConversations().first().data.isEmpty())
    }
}
