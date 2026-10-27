package com.waivera.waivera.feature.chat.message

import androidx.compose.runtime.Immutable
import com.waivera.waivera.core.data.model.Message
import java.util.Collections
import java.util.IdentityHashMap
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/** Inline marks the renderer understands (03 §7). Unknown marks are ignored. */
enum class RichMark { Bold, Italic, Strike, Underline, Code }

@Immutable
sealed interface RichInline {
    /** [href] is set only for a link whose target passed [SafeLinks.isAllowed]. */
    data class Text(val text: String, val marks: Set<RichMark> = emptySet(), val href: String? = null) : RichInline

    /** `@label`, falling back to the id, then "mention". */
    data class Mention(val label: String) : RichInline

    data object HardBreak : RichInline
}

@Immutable
sealed interface RichBlock {
    data class Paragraph(val inlines: List<RichInline>) : RichBlock

    /** Level defaults to 2, as in the reference renderer. */
    data class Heading(val level: Int, val inlines: List<RichInline>) : RichBlock

    /** Children render in the secondary colour. */
    data class Blockquote(val blocks: List<RichBlock>) : RichBlock

    /** [ordered] items show "N." with N = position + 1; bullet items show "•". */
    data class ListBlock(val ordered: Boolean, val items: List<List<RichBlock>>) : RichBlock

    data class CodeBlock(val text: String) : RichBlock

    data object HorizontalRule : RichBlock

    /** Rows of cells; each cell holds blocks. Rendered in a horizontally scrolling grid. */
    data class Table(val rows: List<List<List<RichBlock>>>) : RichBlock

    /** An unknown node with content: its children render as blocks. */
    data class Group(val blocks: List<RichBlock>) : RichBlock
}

@Immutable
data class RichDocument(val blocks: List<RichBlock>)

/**
 * TipTap JSON to [RichDocument]. Mirrors the reference "onion peeler" so wrapped, stringified and
 * double-stringified documents all render (03 §7), and walks the tree recursively (11 §35).
 */
object TipTapParser {
    private const val MAX_DEPTH = 10
    private const val EDITOR_MARKER = "\"editor\""
    private val PREFERRED_KEYS = listOf("doc", "messageDoc", "content", "data", "value")
    private val lenientJson = Json { isLenient = true }

    /** The rich document for [message], or null when it should render as plain text. */
    fun documentFor(message: Message): RichDocument? = extractDoc(message.doc, message.text)?.let(::parse)

    /** Finds the first `{type: "doc", content: [...]}` in [doc], else in [text] when it mentions an editor. */
    fun extractDoc(doc: JsonElement?, text: String?): JsonObject? {
        find(doc, 0, Collections.newSetFromMap(IdentityHashMap()))?.let { return it }
        if (text != null && text.contains(EDITOR_MARKER)) {
            return find(JsonPrimitive(text), 0, Collections.newSetFromMap(IdentityHashMap()))
        }
        return null
    }

    @Suppress("ReturnCount", "CyclomaticComplexMethod")
    private fun find(payload: JsonElement?, depth: Int, seen: MutableSet<Any>): JsonObject? {
        if (payload == null || payload is JsonNull || depth > MAX_DEPTH) return null
        if (payload !is JsonPrimitive && !seen.add(payload)) return null
        return when (payload) {
            is JsonPrimitive -> {
                val raw = payload.contentOrNull?.takeIf { payload.isString && it.isNotEmpty() } ?: return null
                safeJsonParse(raw)?.let { find(it, depth + 1, seen) }
            }
            is JsonArray -> payload.firstNotNullOfOrNull { find(it, depth + 1, seen) }
            is JsonObject -> {
                if (payload.string("type") == "doc" && payload["content"] is JsonArray) return payload
                PREFERRED_KEYS.firstNotNullOfOrNull { key ->
                    payload[key]?.takeIf { it !is JsonNull }?.let { find(it, depth + 1, seen) }
                } ?: payload.values.firstNotNullOfOrNull { find(it, depth + 1, seen) }
            }
        }
    }

    /** JSON.parse after stripping one layer of surrounding quotes and un-escaping `\"`. */
    internal fun safeJsonParse(value: String): JsonElement? {
        var clean = value.trim()
        if (clean.length >= 2 && clean.startsWith('"') && clean.endsWith('"')) {
            clean = clean.substring(1, clean.length - 1).replace("\\\"", "\"")
        }
        return runCatching { lenientJson.parseToJsonElement(clean) }.getOrNull()
    }

    fun parse(doc: JsonObject): RichDocument = RichDocument(blocks(doc.children()))

    private fun blocks(nodes: List<JsonObject>): List<RichBlock> {
        val result = mutableListOf<RichBlock>()
        val pendingInline = mutableListOf<JsonObject>()
        fun flushInline() {
            if (pendingInline.isNotEmpty()) {
                result += RichBlock.Paragraph(inlines(pendingInline.toList(), emptySet(), null))
                pendingInline.clear()
            }
        }
        for (node in nodes) {
            if (node.string("type") in INLINE_TYPES) {
                pendingInline += node
                continue
            }
            flushInline()
            block(node)?.let(result::add)
        }
        flushInline()
        return result
    }

    @Suppress("CyclomaticComplexMethod")
    private fun block(node: JsonObject): RichBlock? = when (node.string("type")) {
        "paragraph" -> RichBlock.Paragraph(inlines(node.children(), emptySet(), null))
        "heading" -> RichBlock.Heading(
            level = node.attrs()?.get("level")?.let { (it as? JsonPrimitive)?.intOrNull } ?: DEFAULT_HEADING,
            inlines = inlines(node.children(), emptySet(), null),
        )
        "blockquote" -> RichBlock.Blockquote(blocks(node.children()))
        "bulletList" -> RichBlock.ListBlock(ordered = false, items = listItems(node))
        "orderedList" -> RichBlock.ListBlock(ordered = true, items = listItems(node))
        "listItem" -> RichBlock.Group(blocks(node.children()))
        "codeBlock" -> RichBlock.CodeBlock(node.children().joinToString("") { plainText(it) })
        "horizontalRule" -> RichBlock.HorizontalRule
        "table" -> RichBlock.Table(
            node.children().filter { it.string("type") == "tableRow" }.map { row ->
                row.children().map { cell -> blocks(cell.children()) }
            },
        )
        else -> node.children().takeIf { node["content"] is JsonArray }?.let { RichBlock.Group(blocks(it)) }
    }

    private fun listItems(node: JsonObject): List<List<RichBlock>> = node.children().map { child ->
        if (child.string("type") == "listItem") blocks(child.children()) else listOfNotNull(block(child))
    }

    private fun inlines(nodes: List<JsonObject>, inherited: Set<RichMark>, inheritedHref: String?): List<RichInline> =
        nodes.flatMap { node ->
            when (node.string("type")) {
                "text" -> {
                    val text = node.string("text").orEmpty()
                    if (text.isEmpty()) {
                        emptyList()
                    } else {
                        val (marks, href) = marks(node, inherited, inheritedHref)
                        listOf(RichInline.Text(text, marks, href))
                    }
                }
                "mention" -> listOf(RichInline.Mention(mentionLabel(node)))
                "hardBreak" -> listOf(RichInline.HardBreak)
                else -> inlines(node.children(), inherited, inheritedHref)
            }
        }

    private fun marks(
        node: JsonObject,
        inherited: Set<RichMark>,
        inheritedHref: String?,
    ): Pair<Set<RichMark>, String?> {
        val result = inherited.toMutableSet()
        var href = inheritedHref
        (node["marks"] as? JsonArray).orEmpty().filterIsInstance<JsonObject>().forEach { mark ->
            when (mark.string("type")) {
                "bold" -> result += RichMark.Bold
                "italic" -> result += RichMark.Italic
                "strike" -> result += RichMark.Strike
                "underline" -> result += RichMark.Underline
                "code" -> result += RichMark.Code
                "link" -> href = mark.attrs()?.string("href")?.takeIf(SafeLinks::isAllowed)
            }
        }
        return result to href
    }

    private fun mentionLabel(node: JsonObject): String {
        val attrs = node.attrs()
        val label = attrs?.string("label") ?: attrs?.get("id")?.let { (it as? JsonPrimitive)?.contentOrNull }
        return label ?: "mention"
    }

    /** Plain-text projection used for copy, edit and previews (03 §7), ported exactly. */
    fun plainText(doc: JsonObject): String = nodePlainText(doc).replace(Regex("\n{3,}"), "\n\n").trim()

    private fun nodePlainText(node: JsonObject): String {
        when (node.string("type")) {
            "text" -> return node.string("text").orEmpty()
            "mention" -> {
                val attrs = node.attrs()
                val label = attrs?.string("label") ?: attrs?.get("id")?.let { (it as? JsonPrimitive)?.contentOrNull }
                return if (label == null) "@mention" else "@$label"
            }
            "hardBreak" -> return "\n"
        }
        val children = node.children().joinToString("") { nodePlainText(it) }
        return if (node.string("type") in LINE_TYPES) "$children\n" else children
    }

    private fun JsonObject.children(): List<JsonObject> =
        (this["content"] as? JsonArray).orEmpty().filterIsInstance<JsonObject>()

    private fun JsonObject.attrs(): JsonObject? = this["attrs"] as? JsonObject

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull

    private val INLINE_TYPES = setOf("text", "mention", "hardBreak")
    private val LINE_TYPES = setOf("paragraph", "heading", "blockquote", "listItem", "codeBlock", "tableRow")
    private const val DEFAULT_HEADING = 2
}

/** The text copied or edited for [message]: the document's plain text, else the stored text. */
fun Message.plainText(): String {
    if (isDeleted) return ""
    val doc = TipTapParser.extractDoc(doc, text)
    return doc?.let(TipTapParser::plainText) ?: text.orEmpty().trim()
}
