package com.waivera.waivera.core.realtime

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** Connection state shown to the user as a quiet banner, never as a blocking error. */
sealed interface ConnectionState {
    data object Disconnected : ConnectionState
    data object Connecting : ConnectionState
    data object Connected : ConnectionState

    /** Waiting [delayMillis] before reconnect attempt number [attempt]. */
    data class Reconnecting(val attempt: Int, val delayMillis: Long) : ConnectionState
}

/**
 * The app's only door to the chat hub. The SignalR Java client has no automatic
 * reconnect, so implementations own retry with [ReconnectPolicy] and must rejoin
 * open conversations and resync over REST after every reconnect.
 */
interface RealtimeClient {
    val state: StateFlow<ConnectionState>

    /** Raw JSON payloads for a hub event, emitted while connected. */
    fun events(eventName: String): Flow<String>

    suspend fun invoke(method: String, vararg args: Any?)

    suspend fun connect()

    suspend fun disconnect()
}
