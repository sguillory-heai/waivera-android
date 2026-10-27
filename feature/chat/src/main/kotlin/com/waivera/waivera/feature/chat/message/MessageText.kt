package com.waivera.waivera.feature.chat.message

import java.net.URI
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Link policy for everything that came from a message or a payload (03 §7, 11 §15): only http
 * and https with a host open, in the browser. `javascript:`, `file:`, `intent:` and custom
 * schemes are never opened.
 */
object SafeLinks {
    fun isAllowed(href: String?): Boolean {
        val value = href?.trim().orEmpty()
        if (value.isEmpty()) return false
        val uri = runCatching { URI(value) }.getOrNull() ?: return false
        val scheme = uri.scheme?.lowercase() ?: return false
        return (scheme == "http" || scheme == "https") && !uri.host.isNullOrBlank()
    }
}

/** What the composer sends: the trimmed text plus the matching TipTap document. */
data class OutgoingMessage(val text: String, val doc: JsonElement)

/**
 * Ports `createOutgoingMessage` (03 §11): CRLF normalised and trimmed, one paragraph whose lines
 * are separated by hard breaks. Typed text is never read as markup. Null when nothing is left.
 */
fun createOutgoingMessage(rawText: String): OutgoingMessage? {
    val text = rawText.replace(Regex("\r\n?"), "\n").trim()
    if (text.isEmpty()) return null
    val content = buildJsonArray {
        text.split('\n').forEachIndexed { index, line ->
            if (index > 0) add(buildJsonObject { put("type", "hardBreak") })
            if (line.isNotEmpty()) {
                add(
                    buildJsonObject {
                        put("type", "text")
                        put("text", line)
                    },
                )
            }
        }
    }
    val doc = JsonObject(
        mapOf(
            "type" to JsonPrimitive("doc"),
            "content" to JsonArray(listOf(JsonObject(mapOf("type" to JsonPrimitive("paragraph"), "content" to content)))),
        ),
    )
    return OutgoingMessage(text, doc)
}
