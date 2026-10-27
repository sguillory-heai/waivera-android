package com.waivera.waivera.feature.chat.list

import com.waivera.waivera.core.network.WaiveraJson
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Plain text for an inbox preview when `messageContent` carries a serialized TipTap document
 * (rich text sent from the web editor) or simple HTML. The walk is recursive because TipTap
 * nests nodes arbitrarily (pitfall 35); anything unrecognised falls back to the raw string.
 */
internal object RichTextPlain {
    private const val MAX_DEPTH = 32
    private val BLOCKS = setOf("paragraph", "heading", "blockquote", "listItem", "codeBlock", "tableRow")
    private val TAG = Regex("<[^>]+>")
    private val BLOCK_TAG = Regex("(?i)</?(p|div|br|li|h[1-6]|blockquote)\\b[^>]*>")
    private val ENTITIES = mapOf(
        "&amp;" to "&",
        "&lt;" to "<",
        "&gt;" to ">",
        "&quot;" to "\"",
        "&#39;" to "'",
        "&nbsp;" to " ",
    )

    fun toPlainText(raw: String): String {
        val trimmed = raw.trim()
        val fromDoc = if (trimmed.startsWith("{") && trimmed.contains("\"type\"")) parseDoc(trimmed) else null
        return when {
            fromDoc != null -> fromDoc
            trimmed.startsWith("<") && TAG.containsMatchIn(trimmed) -> stripHtml(trimmed)
            else -> raw
        }
    }

    private fun parseDoc(json: String): String? = try {
        val root = WaiveraJson.parseToJsonElement(json)
        findDoc(root, 0)?.let { nodeText(it, 0) }
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

    /** The document node: `{type: "doc"}` itself, or nested under a wrapper such as `editor`. */
    private fun findDoc(element: JsonElement, depth: Int): JsonObject? = when {
        depth > MAX_DEPTH -> null
        element is JsonObject && element.type() == "doc" -> element
        element is JsonObject -> element.values.firstNotNullOfOrNull { findDoc(it, depth + 1) }
        element is JsonArray -> element.firstNotNullOfOrNull { findDoc(it, depth + 1) }
        element is JsonPrimitive -> encodedDoc(element)?.let { findDoc(it, depth + 1) }
        else -> null
    }

    /** Some payloads double-encode the document as a JSON string. */
    private fun encodedDoc(element: JsonPrimitive): JsonElement? {
        val content = element.contentOrNull?.trim()
            ?.takeIf { element.isString && it.startsWith("{") && it.contains("\"doc\"") }
            ?: return null
        return try {
            WaiveraJson.parseToJsonElement(content)
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private fun nodeText(node: JsonElement, depth: Int): String {
        if (depth > MAX_DEPTH || node !is JsonObject) return ""
        return when (val type = node.type()) {
            "text" -> (node["text"] as? JsonPrimitive)?.contentOrNull.orEmpty()
            "mention" -> {
                val attrs = node["attrs"] as? JsonObject
                val label = (attrs?.get("label") as? JsonPrimitive)?.contentOrNull
                    ?: (attrs?.get("id") as? JsonPrimitive)?.contentOrNull
                if (label == null) "@mention" else "@$label"
            }
            "hardBreak" -> "\n"
            else -> {
                val children = (node["content"] as? JsonArray).orEmpty().joinToString("") { nodeText(it, depth + 1) }
                if (type in BLOCKS) "$children\n" else children
            }
        }
    }

    private fun JsonObject.type(): String? = (this["type"] as? JsonPrimitive)?.contentOrNull

    private fun stripHtml(html: String): String {
        var text = html.replace(BLOCK_TAG, "\n").replace(TAG, "")
        ENTITIES.forEach { (entity, value) -> text = text.replace(entity, value) }
        return text
    }
}
