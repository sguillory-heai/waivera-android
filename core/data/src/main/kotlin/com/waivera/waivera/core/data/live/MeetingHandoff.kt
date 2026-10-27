package com.waivera.waivera.core.data.live

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Base64
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl

/**
 * Web lobby handoff for calls and Sprout meetings (03 §19, 07 §7). The web app reads
 * `state = base64(encodeURIComponent(JSON))`, and the `calenderEventId` misspelling is part of
 * that contract.
 */
object MeetingHandoff {
    @Serializable
    data class State(
        val calenderEventId: String? = null,
        /** A string in the web contract (`String(conversation.id)` in the reference client, 03 §19). */
        val relatedChatConversationId: String? = null,
        val isPublisher: Boolean = false,
        val isOneToOneCall: Boolean = false,
    )

    // Nulls are written explicitly, matching JSON.stringify in the reference client.
    private val json = Json {
        encodeDefaults = true
        explicitNulls = true
    }

    fun lobbyUrl(webBase: HttpUrl, roomId: String, state: State): String {
        val payload = json.encodeToString(State.serializer(), state)
        val encoded = Base64.getEncoder().encodeToString(
            encodeUriComponent(payload).toByteArray(StandardCharsets.UTF_8),
        )
        // Built by hand so the encoding matches encodeURIComponent exactly (OkHttp's query
        // encoder leaves some base64 characters alone).
        return webBase.toString().trimEnd('/') + "/lobby/" + encodeUriComponent(roomId) + "?state=" +
            encodeUriComponent(encoded)
    }

    /** JavaScript `encodeURIComponent`: like form encoding, but spaces are %20 and `!'()*~` stay. */
    fun encodeUriComponent(value: String): String = URLEncoder.encode(value, "UTF-8")
        .replace("+", "%20")
        .replace("%21", "!")
        .replace("%27", "'")
        .replace("%28", "(")
        .replace("%29", ")")
        .replace("%7E", "~")
}
