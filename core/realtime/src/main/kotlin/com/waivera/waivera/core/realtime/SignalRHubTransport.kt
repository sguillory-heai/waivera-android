package com.waivera.waivera.core.realtime

import com.google.gson.Gson
import com.google.gson.JsonElement as GsonElement
import com.google.gson.JsonParser
import com.microsoft.signalr.HubConnection
import com.microsoft.signalr.HubConnectionBuilder
import com.waivera.waivera.core.network.WaiveraJson
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import javax.inject.Inject
import kotlinx.coroutines.rx3.await
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull

/**
 * [HubTransport] over the Microsoft SignalR Java client (JSON protocol). Arguments arrive as Gson
 * trees and are re-parsed into kotlinx JSON so one decoder ([HubEventDecoder]) handles every
 * source, fixtures included. The Java client has no automatic reconnect; [DefaultRealtimeClient]
 * owns that.
 *
 * Suppressed: The Java client throws unchecked RuntimeExceptions and takes varargs.
 */
@Suppress("TooGenericExceptionCaught", "SpreadOperator")
class SignalRHubTransport(
    url: String,
    accessToken: (Boolean) -> String?,
    forceRefreshToken: Boolean,
    private val listener: HubTransportListener,
) : HubTransport {
    private val connection: HubConnection

    init {
        var forceNext = forceRefreshToken
        val tokenSingle = Single.fromCallable {
            val token = accessToken(forceNext).orEmpty()
            forceNext = false
            token
        }.subscribeOn(Schedulers.io())
        connection = HubConnectionBuilder.create(url)
            .withAccessTokenProvider(tokenSingle)
            .build()
        connection.serverTimeout = SERVER_TIMEOUT_MILLIS
        connection.keepAliveInterval = KEEP_ALIVE_MILLIS
        connection.onClosed { error -> listener.onClosed(error) }
        registerEvents()
    }

    @Suppress("MagicNumber")
    private fun registerEvents() {
        val type = GsonElement::class.java
        ChatHubContract.Events.argumentCounts.forEach { (target, count) ->
            when (count) {
                1 -> connection.on(target, { a -> dispatch(target, a) }, type)
                2 -> connection.on(target, { a, b -> dispatch(target, a, b) }, type, type)
                3 -> connection.on(target, { a, b, c -> dispatch(target, a, b, c) }, type, type, type)
                4 -> connection.on(target, { a, b, c, d -> dispatch(target, a, b, c, d) }, type, type, type, type)
                5 -> connection.on(
                    target,
                    { a, b, c, d, e -> dispatch(target, a, b, c, d, e) },
                    type,
                    type,
                    type,
                    type,
                    type,
                )
                else -> error("Unsupported argument count $count for $target")
            }
        }
    }

    private fun dispatch(target: String, vararg arguments: GsonElement?) {
        listener.onInvocation(target, JsonArray(arguments.map { it.toKotlinx() }))
    }

    override suspend fun start() {
        connection.start().await()
    }

    override suspend fun stop() {
        connection.stop().await()
    }

    override suspend fun invoke(method: String, arguments: List<Any?>): JsonElement = try {
        connection.invoke(GsonElement::class.java, method, *arguments.toWire()).await().toKotlinx()
    } catch (e: RuntimeException) {
        throw HubInvocationException(e.message, e)
    }

    override suspend fun invokeUnit(method: String, arguments: List<Any?>) {
        try {
            connection.invoke(method, *arguments.toWire()).await()
        } catch (e: RuntimeException) {
            throw HubInvocationException(e.message, e)
        }
    }

    override suspend fun send(method: String, arguments: List<Any?>) {
        try {
            connection.send(method, *arguments.toWire())
        } catch (e: RuntimeException) {
            throw HubInvocationException(e.message, e)
        }
    }

    /** kotlinx JSON values become Gson trees so the client's Gson serializer writes them verbatim. */
    private fun List<Any?>.toWire(): Array<Any?> = map { value ->
        if (value is JsonElement) JsonParser.parseString(value.toString()) else value
    }.toTypedArray()

    private fun GsonElement?.toKotlinx(): JsonElement =
        if (this == null || isJsonNull) JsonNull else WaiveraJson.parseToJsonElement(GSON.toJson(this))

    class Factory @Inject constructor() : HubTransportFactory {
        override fun create(
            url: String,
            accessToken: (forceRefresh: Boolean) -> String?,
            forceRefreshToken: Boolean,
            listener: HubTransportListener,
        ): HubTransport = SignalRHubTransport(url, accessToken, forceRefreshToken, listener)
    }

    private companion object {
        val GSON = Gson()

        // hub.md §1: the server keep-alive is 15 s, so the client waits at least twice that.
        const val SERVER_TIMEOUT_MILLIS = 30_000L
        const val KEEP_ALIVE_MILLIS = 15_000L
    }
}
