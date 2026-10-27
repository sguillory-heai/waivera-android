package com.waivera.waivera.core.testing

import com.waivera.waivera.core.realtime.HubInvocationException
import com.waivera.waivera.core.realtime.HubTransport
import com.waivera.waivera.core.realtime.HubTransportFactory
import com.waivera.waivera.core.realtime.HubTransportListener
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull

/** A hub call recorded by [FakeHubTransport]. */
data class HubCall(val method: String, val arguments: List<Any?>)

/**
 * Hands out [FakeHubTransport]s and keeps every one it created, newest last. Configure
 * [failStarts] to make the next N handshakes fail, and [results] to answer value-returning
 * invocations.
 */
class FakeHubTransportFactory : HubTransportFactory {
    val created = mutableListOf<FakeHubTransport>()
    var failStarts = 0
    val results = mutableMapOf<String, (List<Any?>) -> JsonElement>()
    val hubErrors = mutableMapOf<String, String>()

    /** Runs at the end of every successful handshake, before the client sees it succeed. */
    var onStarted: () -> Unit = {}

    /** Every call across all transports, in order. */
    val calls: List<HubCall> get() = created.flatMap { it.calls }

    val latest: FakeHubTransport get() = created.last()

    override fun create(
        url: String,
        accessToken: (forceRefresh: Boolean) -> String?,
        forceRefreshToken: Boolean,
        listener: HubTransportListener,
    ): HubTransport = FakeHubTransport(this, url, accessToken, forceRefreshToken, listener).also { created += it }
}

class FakeHubTransport internal constructor(
    private val factory: FakeHubTransportFactory,
    val url: String,
    private val accessToken: (Boolean) -> String?,
    val forceRefreshToken: Boolean,
    private val listener: HubTransportListener,
) : HubTransport {
    val calls = mutableListOf<HubCall>()
    var started = false
        private set
    var stopped = false
        private set
    var tokenUsed: String? = null
        private set

    override suspend fun start() {
        tokenUsed = accessToken(forceRefreshToken)
        if (factory.failStarts > 0) {
            factory.failStarts--
            throw HubInvocationException("handshake failed")
        }
        started = true
        factory.onStarted()
    }

    override suspend fun stop() {
        if (stopped) return
        stopped = true
        listener.onClosed(null)
    }

    override suspend fun invoke(method: String, arguments: List<Any?>): JsonElement {
        record(method, arguments)
        return factory.results[method]?.invoke(arguments) ?: JsonNull
    }

    override suspend fun invokeUnit(method: String, arguments: List<Any?>) {
        record(method, arguments)
    }

    override suspend fun send(method: String, arguments: List<Any?>) {
        record(method, arguments)
    }

    private fun record(method: String, arguments: List<Any?>) {
        if (stopped) throw HubInvocationException("connection closed")
        calls += HubCall(method, arguments)
        factory.hubErrors[method]?.let { throw HubInvocationException(it) }
    }

    /** Simulates the server sending [target] with [arguments]. */
    fun emit(target: String, arguments: JsonArray) = listener.onInvocation(target, arguments)

    fun emit(invocation: HubInvocation) = emit(invocation.target, invocation.arguments)

    /** Simulates the network dropping the connection. */
    fun drop(error: Throwable = java.io.IOException("connection lost")) {
        stopped = true
        listener.onClosed(error)
    }
}
