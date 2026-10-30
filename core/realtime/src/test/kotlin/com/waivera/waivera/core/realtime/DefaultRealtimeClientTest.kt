package com.waivera.waivera.core.realtime

import app.cash.turbine.test
import com.waivera.waivera.core.network.AuthTokenProvider
import com.waivera.waivera.core.network.NetworkConfig
import com.waivera.waivera.core.network.error.WaiveraError
import com.waivera.waivera.core.realtime.ChatHubContract.Methods
import com.waivera.waivera.core.testing.FakeHubTransportFactory
import com.waivera.waivera.core.testing.Fixtures
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultRealtimeClientTest {
    private val factory = FakeHubTransportFactory()
    private val tokenRequests = mutableListOf<Boolean>()
    private val tokens = AuthTokenProvider { force ->
        tokenRequests += force
        "token"
    }
    private val config = NetworkConfig(apiBaseUrl = "https://api.example.test", webBaseUrl = "https://app.example.test")

    private fun TestScope.client(scope: CoroutineScope = backgroundScope, networkConfig: NetworkConfig = config) =
        DefaultRealtimeClient(
            networkConfig,
            tokens,
            factory,
            scope,
            ReconnectPolicy(baseDelayMillis = 1_000, maxDelayMillis = 8_000, random = Random(1)),
        )

    private fun TestScope.connected(): DefaultRealtimeClient = client().also {
        it.start("usr_alex_rivera")
        it.setForeground(true)
        runCurrent()
    }

    @Test
    fun staysDisconnectedUntilSignedInAndForeground() = runTest {
        val client = client()
        client.start("usr_alex_rivera")
        runCurrent()
        assertEquals(ConnectionState.Disconnected, client.state.value)
        assertTrue(factory.created.isEmpty())

        client.setForeground(true)
        runCurrent()
        assertEquals(ConnectionState.Connected, client.state.value)
        assertEquals("https://api.example.test/api/chatHub", factory.latest.url)
        assertEquals(1L, client.connectionEpoch.value)
    }

    @Test
    fun backgroundingClosesTheConnection() = runTest {
        val client = connected()
        client.setForeground(false)
        runCurrent()
        assertTrue(factory.latest.stopped)
        assertEquals(ConnectionState.Disconnected, client.state.value)
    }

    @Test
    fun unconfiguredHubNeverConnects() = runTest {
        val client = client(networkConfig = NetworkConfig("", ""))
        client.start("usr_alex_rivera")
        client.setForeground(true)
        runCurrent()
        assertEquals(ConnectionState.Disconnected, client.state.value)
        assertTrue(factory.created.isEmpty())
    }

    @Test
    fun dropReconnectsRejoinsAndRequestsResync() = runTest {
        val client = connected()
        val lease = client.attendConversation(7)
        runCurrent()
        assertEquals(listOf(Methods.JOIN_CONVERSATION), factory.latest.calls.map { it.method })

        client.resyncRequests.test {
            factory.latest.drop()
            runCurrent()
            assertEquals(2, factory.created.size)
            assertEquals(ConnectionState.Connected, client.state.value)
            assertEquals(
                listOf<Any?>(7L),
                factory.latest.calls.single {
                    it.method == Methods.JOIN_CONVERSATION
                }.arguments,
            )
            assertEquals(2L, awaitItem())
        }
        lease.close()
        runCurrent()
        assertEquals(Methods.LEAVE_CONVERSATION, factory.latest.calls.last().method)
    }

    @Test
    fun aConversationAttendedWhileTheHandshakeFinishesIsJoinedExactlyOnce() = runTest {
        val client = client()
        val leases = mutableListOf<AutoCloseable>()
        factory.onStarted = { leases += client.attendConversation(7) }
        client.start("usr_alex_rivera")
        client.setForeground(true)
        advanceTimeBy(1)
        runCurrent()
        // A second lease while connected must not join again.
        leases += client.attendConversation(7)
        runCurrent()
        assertEquals(ConnectionState.Connected, client.state.value)
        assertEquals(
            listOf<List<Any?>>(listOf(7L)),
            factory.latest.calls.filter { it.method == Methods.JOIN_CONVERSATION }.map { it.arguments },
        )
        leases.forEach { it.close() }
    }

    @Test
    fun resumeFromBackgroundCountsAsReconnect() = runTest {
        val client = connected()
        client.resyncRequests.test {
            client.setForeground(false)
            runCurrent()
            client.setForeground(true)
            runCurrent()
            assertEquals(2L, awaitItem())
        }
    }

    @Test
    fun failedHandshakesBackOffForeverAndForceATokenRefreshOncePerOutage() = runTest {
        factory.failStarts = 6
        val client = client()
        client.start("usr_alex_rivera")
        client.setForeground(true)
        runCurrent()
        // Attempt 1 fails immediately; later attempts wait with capped backoff.
        val reconnecting = assertIs<ConnectionState.Reconnecting>(client.state.value)
        assertEquals(2, reconnecting.attempt)
        repeat(6) {
            advanceTimeBy(8_001)
            runCurrent()
        }
        assertEquals(ConnectionState.Connected, client.state.value)
        assertEquals(7, factory.created.size)
        // IS-02: only the first retry forces a refresh; forcing one on every retry floods
        // Firebase during a long outage. This replaced an expectation of a refresh per retry.
        assertEquals(listOf(false, true, false, false, false, false, false), tokenRequests)
        factory.latest.drop()
        factory.failStarts = 2
        repeat(3) {
            advanceTimeBy(8_001)
            runCurrent()
        }
        assertEquals(ConnectionState.Connected, client.state.value)
        assertEquals(listOf(false, true, false), tokenRequests.drop(7), "a new outage gets one refresh again")
    }

    @Test
    fun retryNowSkipsTheWait() = runTest {
        factory.failStarts = 2
        val client = client()
        client.start("usr_alex_rivera")
        client.setForeground(true)
        runCurrent()
        assertIs<ConnectionState.Reconnecting>(client.state.value)
        client.retryNow()
        runCurrent()
        assertIs<ConnectionState.Reconnecting>(client.state.value)
        client.retryNow()
        runCurrent()
        assertEquals(ConnectionState.Connected, client.state.value)
    }

    @Test
    fun eventsFromAStaleTransportAreIgnored() = runTest {
        val client = connected()
        val stale = factory.latest
        stale.drop()
        runCurrent()
        client.events.test {
            stale.emit(Fixtures.hub("hub-receive-message.json"))
            factory.latest.emit(Fixtures.hub("hub-receive-reaction-added.json"))
            runCurrent()
            assertIs<HubEvent.Reaction>(awaitItem())
            expectNoEvents()
        }
    }

    @Test
    fun eventsArriveInOrder() = runTest {
        val client = connected()
        client.events.test {
            factory.latest.emit(Fixtures.hub("hub-receive-message.json"))
            factory.latest.emit(Fixtures.hub("hub-receive-message-update.json"))
            factory.latest.emit(Fixtures.hub("hub-receive-message-delete.json"))
            runCurrent()
            assertIs<HubEvent.MessageReceived>(awaitItem())
            assertIs<HubEvent.MessageUpdated>(awaitItem())
            assertIs<HubEvent.MessageDeleted>(awaitItem())
        }
    }

    @Test
    fun callsFailWithRealtimeUnavailableWhenDisconnected() = runTest {
        val client = client()
        assertFailsWith<WaiveraError.RealtimeUnavailable> { client.sendReaction(1, "👍") }
    }

    @Test
    fun hubExceptionsMapToTypedErrors() = runTest {
        val client = connected()
        factory.hubErrors[Methods.SEND_TYPING_STATUS] = "You cannot post in this conversation."
        assertFailsWith<WaiveraError.ChannelReadOnly> { client.sendTypingStatus(7, true) }
    }

    @Test
    fun sendMessageDecodesTheCommittedMessage() = runTest {
        val client = connected()
        factory.results[Methods.SEND_MESSAGE] = { args ->
            buildJsonObject {
                put("id", 501)
                put("conversationId", args[0] as Long)
                put("clientMessageId", args[4] as String)
                put("messageContent", "hello")
            }
        }
        val sent = client.sendMessage(7, "hello", clientMessageId = "0b7c5f0e-8d0e-4d55-9c55-0b1f0f5c2a11")
        assertEquals(501L, sent.id)
        assertEquals("0b7c5f0e-8d0e-4d55-9c55-0b1f0f5c2a11", sent.clientMessageId)
        val args = factory.latest.calls.last().arguments
        assertEquals(6, args.size)
    }

    @Test
    fun stopForgetsJoinsAndResetsEpoch() = runTest {
        val client = connected()
        client.attendConversation(7)
        runCurrent()
        client.stop()
        runCurrent()
        assertEquals(0L, client.connectionEpoch.value)
        client.start("usr_alex_rivera")
        runCurrent()
        assertEquals(1L, client.connectionEpoch.value)
        assertTrue(factory.latest.calls.none { it.method == Methods.JOIN_CONVERSATION })
    }

    @Test
    fun aTransportThatCannotBeBuiltIsAFailedAttemptNotACrash() = runTest {
        var failuresLeft = 1
        val flaky = HubTransportFactory { url, token, force, listener ->
            check(failuresLeft-- <= 0) { "Could not build the hub connection" }
            factory.create(url, token, force, listener)
        }
        val client = DefaultRealtimeClient(
            config,
            tokens,
            flaky,
            backgroundScope,
            ReconnectPolicy(baseDelayMillis = 1_000, maxDelayMillis = 8_000, random = Random(1)),
        )
        client.start("usr_alex_rivera")
        client.setForeground(true)
        runCurrent()
        assertIs<ConnectionState.Reconnecting>(client.state.value)

        advanceTimeBy(8_001)
        runCurrent()
        assertEquals(ConnectionState.Connected, client.state.value)
        assertEquals(1, factory.created.size)
    }
}
