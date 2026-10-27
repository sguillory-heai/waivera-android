package com.waivera.waivera.core.data

import com.waivera.waivera.core.data.model.SendState
import com.waivera.waivera.core.data.model.SessionState
import com.waivera.waivera.core.data.outbox.OutboxItem
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest

/** AS-03: a queued message is only ever sent as the account that wrote it. */
class OutboxOwnershipTest {
    private val testScope = TestScope()
    private val graph = TestGraph(testScope.backgroundScope)

    @AfterTest
    fun tearDown() = graph.close()

    private fun item(author: String) = OutboxItem(
        clientMessageId = "c-$author",
        conversationId = 7,
        authorId = author,
        text = "hello",
        createdAt = graph.clock.now,
    )

    @Test
    fun aMessageQueuedByAnotherAccountIsDroppedNotSent() = testScope.runTest {
        graph.outbox.put(item("usr_previous_person"))
        assertFalse(graph.processor.process())
        assertTrue(graph.realtime.calls.isEmpty(), "nothing goes over the hub")
        assertTrue(graph.server.requests.isEmpty(), "nothing goes over REST")
        assertNull(graph.outbox.get("c-usr_previous_person"))
    }

    @Test
    fun nothingIsSentWithoutASignedInUser() = testScope.runTest {
        graph.session.mutableState.value = SessionState.SignedOut()
        graph.outbox.put(item("usr_alex_rivera"))
        assertFalse(graph.processor.process(), "the next sign-in processes the queue, not a retry loop")
        assertTrue(graph.realtime.calls.isEmpty())
        assertEquals(SendState.QUEUED, graph.outbox.get("c-usr_alex_rivera")?.state)
    }

    @Test
    fun theSignedInAuthorsOwnMessageIsSent() = testScope.runTest {
        graph.outbox.put(item("usr_alex_rivera"))
        assertFalse(graph.processor.process())
        assertEquals("SendMessage", graph.realtime.calls.single().first)
        assertNull(graph.outbox.get("c-usr_alex_rivera"))
    }
}
