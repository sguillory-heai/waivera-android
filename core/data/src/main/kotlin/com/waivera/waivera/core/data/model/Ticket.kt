package com.waivera.waivera.core.data.model

import java.net.URI
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * The payload of a bot `ticket.update` system message (03 §15), with values as sent; screens
 * format dates and labels. Only built when the id, title and an http(s) url are present.
 */
@Serializable
data class TicketUpdate(
    val ticketId: String,
    val title: String,
    val url: String,
    val type: String? = null,
    val priority: String? = null,
    val createdBy: String? = null,
    val createdDate: String? = null,
    val createdAt: String? = null,
    val dueDateLabel: String? = null,
    val dueDate: String? = null,
) {
    companion object {
        const val EVENT_TYPE = "ticket.update"
        private const val MAX_UNWRAP = 3
        private val json = Json { isLenient = true }

        /** For a message flagged as a ticket update; null for anything else. */
        fun from(systemEventType: String?, payload: JsonElement?): TicketUpdate? =
            if (systemEventType == EVENT_TYPE) parse(payload) else null

        /** Unwraps up to three layers of JSON string, as the reference client does. */
        @Suppress("ReturnCount")
        fun parse(payload: JsonElement?): TicketUpdate? {
            var candidate: JsonElement? = payload
            repeat(MAX_UNWRAP) {
                val primitive = candidate as? JsonPrimitive
                if (primitive != null && primitive.isString) {
                    candidate = runCatching { json.parseToJsonElement(primitive.content) }.getOrNull() ?: return null
                }
            }
            val value = candidate as? JsonObject ?: return null
            return TicketUpdate(
                ticketId = value.text("ticketId") ?: return null,
                title = value.text("title") ?: return null,
                url = value.text("url")?.takeIf(::isWebUrl) ?: return null,
                type = value.text("type"),
                priority = value.text("priority"),
                createdBy = value.text("createdBy"),
                createdDate = value.rawString("createdDate"),
                createdAt = value.rawString("createdAt"),
                dueDateLabel = value.rawString("dueDateLabel"),
                dueDate = value.rawString("dueDate"),
            )
        }

        /** `http` or `https` with a host; anything else is never opened. */
        fun isWebUrl(value: String?): Boolean {
            val trimmed = value?.trim().orEmpty()
            if (trimmed.isEmpty()) return false
            val uri = runCatching { URI(trimmed) }.getOrNull() ?: return false
            val scheme = uri.scheme?.lowercase() ?: return false
            return (scheme == "http" || scheme == "https") && !uri.host.isNullOrBlank()
        }

        private fun JsonObject.rawString(key: String): String? =
            (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull

        /** Any primitive as trimmed text; null when blank (the reference app uses `String(value ?? '')`). */
        private fun JsonObject.text(key: String): String? =
            (this[key] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf(String::isNotEmpty)
    }
}
