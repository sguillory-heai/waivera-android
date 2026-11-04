package com.waivera.waivera.feature.chat.message

import java.net.URLDecoder
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** What a Waivera link in a message points at. Equal targets share one preview. */
sealed interface LinkTarget {
    val kind: LinkKind

    /** `/task/HEA-1042` or `/task/1042`; [prefix] is the agency's, when the link names it. */
    data class Task(val number: Int, val prefix: String?) : LinkTarget {
        override val kind = LinkKind.Task
    }

    /** `/faqs/article/{id}` ([id], lower case) or `/faqs/{slug}` ([slug]). */
    data class Faq(val id: String?, val slug: String?) : LinkTarget {
        override val kind = LinkKind.Faq
    }

    /** `/resources/{id}`, [id] in lower case. */
    data class Resource(val id: String) : LinkTarget {
        override val kind = LinkKind.Resource
    }
}

/**
 * Finds Waivera links in messages and decides how they show, by the web's rules
 * (`TaskFaqLinkPreviews.tsx`, `ResourceChatService.ts`): only links on the configured web app,
 * site-relative or absolute, without credentials; at most [MAX_LINKS] per message.
 */
@Suppress("TooManyFunctions") // Reading each kind of link, and presenting the text, are small steps.
class ChatLinks(private val webBase: HttpUrl?) {
    /** The target [href] points at, or null for anything that isn't a Waivera task, FAQ or resource. */
    fun target(href: String): LinkTarget? {
        if (href.length > MAX_HREF || UNSAFE.containsMatchIn(href)) return null
        val url = absolute(href)?.toHttpUrlOrNull() ?: return null
        return task(url.encodedPath) ?: faq(url.encodedPath) ?: resource(url)
    }

    private fun task(path: String): LinkTarget.Task? {
        val reference = TASK.matchEntire(path)?.groupValues?.get(1)?.let(::decode)
        val parts = reference?.let(FRIENDLY_TASK::matchEntire) ?: return null
        return parts.groupValues[2].toLongOrNull()?.takeIf { it in 1..Int.MAX_VALUE }
            ?.let { LinkTarget.Task(it.toInt(), parts.groupValues[1].takeIf(String::isNotEmpty)) }
    }

    private fun faq(path: String): LinkTarget.Faq? {
        FAQ_ARTICLE.matchEntire(path)?.let { match ->
            return decode(match.groupValues[1])?.takeIf(UUID::matches)?.let { LinkTarget.Faq(it.lowercase(), null) }
        }
        val slug = FAQ_SLUG.matchEntire(path)?.groupValues?.get(1)?.let(::decode)
        return slug?.takeIf(SLUG::matches)?.let { LinkTarget.Faq(null, it) }
    }

    /** Resource links never carry a query or fragment, as the web writes them. */
    private fun resource(url: HttpUrl): LinkTarget.Resource? {
        if (url.encodedQuery != null || url.encodedFragment != null) return null
        val id = RESOURCE.matchEntire(url.encodedPath)?.groupValues?.get(1)
        return id?.takeIf { UUID.matches(it) && it != NIL_UUID }?.let { LinkTarget.Resource(it.lowercase()) }
    }

    /**
     * [href] as a full address on the web app: site-relative links resolve against the web base;
     * absolute ones must already be on it. Null for anything else.
     */
    fun absolute(href: String): String? {
        val base = webBase ?: return null
        // A backslash reads as a slash to URL parsers, so `/\\host` would leave the site.
        if (UNSAFE.containsMatchIn(href)) return null
        val url = if (href.startsWith("/") && !href.startsWith("//")) base.resolve(href) else href.toHttpUrlOrNull()
        val sameSite = url != null && url.scheme == base.scheme && url.host == base.host && url.port == base.port
        return url?.toString()?.takeIf { sameSite && url.username.isEmpty() && url.password.isEmpty() }
    }

    /** Each distinct target in [document], in reading order, with the first link that named it. */
    fun targets(document: RichDocument): List<Pair<String, LinkTarget>> {
        val found = LinkedHashMap<LinkTarget, String>()
        fun visit(inlines: List<RichInline>) = inlines.forEach { inline ->
            val href = when (inline) {
                is RichInline.Text -> inline.href
                is RichInline.Mention -> inline.href
                RichInline.HardBreak -> null
            } ?: return@forEach
            if (found.size < MAX_LINKS) target(href)?.let { found.putIfAbsent(it, href) }
        }
        document.blocks.forEachInline(::visit)
        return found.map { (target, href) -> href to target }
    }

    /**
     * [document] as it reads once its links have previews: an address shown as text is replaced
     * by the short name of what it points at (a task's number, a resource's or article's title),
     * so the reader can match text to preview; a link that ends the message leaves the text,
     * since its preview sits right under it. Links still loading, or that the reader can't
     * open, are left as written.
     */
    fun present(document: RichDocument, previews: Map<LinkTarget, LinkPreview>): RichDocument {
        fun resolved(href: String?): LinkPreview? =
            href?.let(::target)?.let(previews::get)?.takeIf { it.shortName() != null }
        val renamed = document.blocks.mapInlines { inlines ->
            inlines.map { inline ->
                val preview = (inline as? RichInline.Text)?.let { resolved(it.href) }
                val name = preview?.shortName()
                if (inline is RichInline.Text && name != null && looksLikeAddress(inline.text, inline.href)) {
                    inline.copy(text = name)
                } else {
                    inline
                }
            }
        }
        return RichDocument(dropTrailingLink(renamed) { resolved(it) != null })
    }

    private fun looksLikeAddress(text: String, href: String?): Boolean {
        val trimmed = text.trim()
        return trimmed == href ||
            trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true) ||
            trimmed.startsWith("/")
    }

    /** Without the last paragraph's closing link and the spaces or line breaks before it. */
    private fun dropTrailingLink(blocks: List<RichBlock>, hasPreview: (String?) -> Boolean): List<RichBlock> {
        val last = blocks.lastOrNull() as? RichBlock.Paragraph ?: return blocks
        val inlines = last.inlines.toMutableList()
        while (inlines.lastOrNull().isBlank()) inlines.removeAt(inlines.lastIndex)
        val href = (inlines.lastOrNull() as? RichInline.Text)?.href
        if (href == null || !hasPreview(href)) return blocks
        while ((inlines.lastOrNull() as? RichInline.Text)?.href == href) inlines.removeAt(inlines.lastIndex)
        while (inlines.lastOrNull().isBlank()) inlines.removeAt(inlines.lastIndex)
        val rest = blocks.dropLast(1)
        return if (inlines.isEmpty()) rest else rest + RichBlock.Paragraph(inlines)
    }

    private fun RichInline?.isBlank(): Boolean =
        this == RichInline.HardBreak || (this is RichInline.Text && href == null && text.isBlank())

    private fun decode(value: String): String? = runCatching {
        URLDecoder.decode(value, Charsets.UTF_8.name())
    }.getOrNull()

    companion object {
        const val MAX_LINKS = 20
        private const val MAX_HREF = 2048
        private const val NIL_UUID = "00000000-0000-0000-0000-000000000000"

        // Controls, bidi overrides and backslashes are refused before parsing can hide them.
        private val UNSAFE =
            Regex("[\\u0000-\\u0020\\u007f-\\u009f\\u061c\\u200e\\u200f\\u202a-\\u202e\\u2066-\\u2069\\\\]")
        private val TASK = Regex("^/task/([^/]+)/?$", RegexOption.IGNORE_CASE)
        private val FRIENDLY_TASK = Regex("^(?:([a-z0-9]+)-)?([0-9]+)$", RegexOption.IGNORE_CASE)
        private val FAQ_ARTICLE = Regex("^/faqs/article/([^/]+)/?$", RegexOption.IGNORE_CASE)
        private val FAQ_SLUG = Regex("^/faqs/([^/]+)/?$", RegexOption.IGNORE_CASE)
        private val RESOURCE = Regex("^/resources/([^/]+)$")
        private val UUID =
            Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$", RegexOption.IGNORE_CASE)
        private val SLUG = Regex("^[a-z0-9][a-z0-9_-]{0,199}$", RegexOption.IGNORE_CASE)
    }
}

/** What a preview's link is called in the text once it resolves; null while loading or unavailable. */
internal fun LinkPreview.shortName(): String? = when (this) {
    is LinkPreview.Task -> key
    is LinkPreview.Resource -> title
    is LinkPreview.Faq -> title
    is LinkPreview.Loading, is LinkPreview.Unavailable -> null
}

private fun List<RichBlock>.forEachInline(visit: (List<RichInline>) -> Unit) {
    forEach { it.forEachInline(visit) }
}

private fun RichBlock.forEachInline(visit: (List<RichInline>) -> Unit) {
    when (this) {
        is RichBlock.Paragraph -> visit(inlines)
        is RichBlock.Heading -> visit(inlines)
        is RichBlock.Blockquote -> blocks.forEachInline(visit)
        is RichBlock.ListBlock -> items.forEach { it.forEachInline(visit) }
        is RichBlock.Table -> rows.flatten().forEach { it.forEachInline(visit) }
        is RichBlock.Group -> blocks.forEachInline(visit)
        is RichBlock.CodeBlock, RichBlock.HorizontalRule -> Unit
    }
}

private fun List<RichBlock>.mapInlines(change: (List<RichInline>) -> List<RichInline>): List<RichBlock> = map { block ->
    when (block) {
        is RichBlock.Paragraph -> block.copy(inlines = change(block.inlines))
        is RichBlock.Heading -> block.copy(inlines = change(block.inlines))
        is RichBlock.Blockquote -> block.copy(blocks = block.blocks.mapInlines(change))
        is RichBlock.ListBlock -> block.copy(items = block.items.map { it.mapInlines(change) })
        is RichBlock.Table -> block.copy(rows = block.rows.map { row -> row.map { it.mapInlines(change) } })
        is RichBlock.Group -> block.copy(blocks = block.blocks.mapInlines(change))
        is RichBlock.CodeBlock, RichBlock.HorizontalRule -> block
    }
}
