package com.waivera.waivera.core.realtime

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement

/**
 * One physical hub connection. A transport is used once: started, then closed by the server,
 * the network or [stop]. [DefaultRealtimeClient] builds a new one for every attempt, which is
 * what makes stale callbacks easy to ignore.
 */
interface HubTransport {
    /** Opens the connection; throws when the handshake fails. */
    suspend fun start()

    suspend fun stop()

    /** Calls a hub method that returns a value. Throws [HubInvocationException]. */
    suspend fun invoke(method: String, arguments: List<Any?>): JsonElement

    /** Calls a `void` hub method and waits for the server to finish it. Throws [HubInvocationException]. */
    suspend fun invokeUnit(method: String, arguments: List<Any?>)

    /** Fire-and-forget call; still throws if the connection is gone. */
    suspend fun send(method: String, arguments: List<Any?>)
}

/** Receives server invocations and the close signal on the transport's own thread. */
interface HubTransportListener {
    fun onInvocation(target: String, arguments: JsonArray)

    /** Called once when the connection ends, with the cause when it was not a clean stop. */
    fun onClosed(error: Throwable?)
}

fun interface HubTransportFactory {
    /**
     * @param accessToken returns the Firebase ID token for the handshake, refreshed when the
     *   argument is true. It may block; the transport calls it off the main thread.
     */
    fun create(
        url: String,
        accessToken: (forceRefresh: Boolean) -> String?,
        forceRefreshToken: Boolean,
        listener: HubTransportListener,
    ): HubTransport
}

/** A `HubException` from the server, or a transport failure while invoking. */
class HubInvocationException(message: String?, cause: Throwable? = null) : Exception(message, cause)
