package com.waivera.waivera.feature.chat.message

import com.waivera.waivera.core.data.model.LinkedFaq
import com.waivera.waivera.core.data.repository.LinkLookupRepository
import com.waivera.waivera.core.network.NetworkConfig
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Previews for the Waivera links in one screen's messages. Each target is read once while the
 * screen is open, with the reader's own access, and the published FAQs once for every article
 * link; a new screen reads again, so a change of access shows the next time a chat opens.
 * Anything the reader can't open, or that fails to load, shows as unavailable.
 */
class ChatLinkResolver @Inject constructor(private val lookups: LinkLookupRepository, config: NetworkConfig) {
    val links = ChatLinks(config.webBase)

    private val state = MutableStateFlow<Map<LinkTarget, LinkPreview>>(emptyMap())
    val previews: StateFlow<Map<LinkTarget, LinkPreview>> = state.asStateFlow()

    private var faqs: Deferred<Result<List<LinkedFaq>>>? = null

    /** Rows with their links' previews attached and their text [ChatLinks.present]ed. */
    fun decorate(items: List<TimelineItem>, previews: Map<LinkTarget, LinkPreview>): Decorated {
        val wanted = LinkedHashSet<LinkTarget>()
        val decorated = items.map { item ->
            (item as? TimelineItem.MessageRow)?.let { decorate(it, previews, wanted) } ?: item
        }
        return Decorated(decorated, wanted)
    }

    /** One row, adding its targets to [wanted]. */
    fun decorate(
        row: TimelineItem.MessageRow,
        previews: Map<LinkTarget, LinkPreview>,
        wanted: MutableSet<LinkTarget>,
    ): TimelineItem.MessageRow {
        val rich = row.rich ?: return row
        if (row.message.isDeleted) return row
        val found = targetsOf(rich)
        if (found.isEmpty()) return row
        wanted += found.map { it.second }
        return row.copy(
            rich = links.present(rich, previews),
            links = found.map { (href, target) ->
                MessageLink(href, previews[target] ?: LinkPreview.Loading(target.kind))
            },
        )
    }

    // A preview arriving re-decorates the same rows, so each document's links are read once.
    private val targetCache = java.util.Collections.synchronizedMap(
        java.util.IdentityHashMap<RichDocument, List<Pair<String, LinkTarget>>>(),
    )

    private fun targetsOf(rich: RichDocument): List<Pair<String, LinkTarget>> {
        targetCache[rich]?.let { return it }
        if (targetCache.size > MAX_CACHED_DOCUMENTS) targetCache.clear()
        return links.targets(rich).also { targetCache[rich] = it }
    }

    /** Starts reading every target in [targets] not read yet. Call on the main thread. */
    fun request(targets: Collection<LinkTarget>, scope: CoroutineScope, today: () -> LocalDate) {
        val fresh = targets.filter { it !in state.value }
        if (fresh.isEmpty()) return
        state.update { current -> current + fresh.associateWith { LinkPreview.Loading(it.kind) } }
        fresh.forEach { target ->
            scope.launch {
                val preview = resolve(target, scope, today)
                state.update { it + (target to preview) }
            }
        }
    }

    /** What opening [href] does: a task opens in the app once its preview knows it, the rest on the web. */
    fun effectFor(href: String): ChatEffect? {
        val target = links.target(href)
        (target?.let(state.value::get) as? LinkPreview.Task)?.let { return ChatEffect.OpenTask(it.id) }
        return links.absolute(href)?.takeIf(SafeLinks::isAllowed)?.let(ChatEffect::OpenUrl)
    }

    private suspend fun resolve(target: LinkTarget, scope: CoroutineScope, today: () -> LocalDate): LinkPreview {
        val preview = when (target) {
            is LinkTarget.Task -> task(target, today)
            is LinkTarget.Resource -> lookups.resource(target.id).getOrNull()
                ?.let { LinkPreview.Resource(it.title, it.provider, it.city) }
            is LinkTarget.Faq -> faq(target, scope)
        }
        return preview ?: LinkPreview.Unavailable(target.kind)
    }

    private suspend fun task(target: LinkTarget.Task, today: () -> LocalDate): LinkPreview.Task? {
        val task = lookups.task(target.number).getOrNull() ?: return null
        // The server reads the number in the reader's agency; a link naming another agency's
        // prefix points at someone else's task that happens to share the number.
        val prefix = task.key.substringBefore('-', missingDelimiterValue = "").takeIf(String::isNotEmpty)
        val number = task.key.substringAfterLast('-')
        val matches = number == target.number.toString() &&
            (target.prefix == null || prefix.equals(target.prefix, ignoreCase = true))
        if (!matches) return null
        return LinkPreview.Task(
            id = task.id,
            key = task.key,
            title = task.title,
            state = task.state,
            due = task.due,
            dueShowsYear = task.due?.let { it.year != today().year } == true,
        )
    }

    private suspend fun faq(target: LinkTarget.Faq, scope: CoroutineScope): LinkPreview.Faq? {
        val index = faqs ?: scope.async { lookups.faqs() }.also { faqs = it }
        return index.await().getOrNull()
            ?.firstOrNull { if (target.id != null) it.id == target.id else it.slug == target.slug }
            ?.let { LinkPreview.Faq(it.title, it.summary) }
    }

    class Decorated(val items: List<TimelineItem>, val targets: Set<LinkTarget>)

    private companion object {
        const val MAX_CACHED_DOCUMENTS = 2_000
    }
}
