package com.waivera.waivera.feature.today

import com.waivera.waivera.core.data.model.ActivityFeed
import com.waivera.waivera.core.data.model.Synced
import com.waivera.waivera.core.data.repository.ActivityRepository
import com.waivera.waivera.feature.today.model.ActivityRules
import com.waivera.waivera.feature.today.model.TodayLocalState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Loads activity pages: on demand when the list nears its end, and on its own while the oldest
 * loaded page still holds unread items, so the unread count covers every page and not only the
 * first (pitfall 26). The API has no unread total, so the automatic loading stops after
 * [MAX_AUTO_PAGES] pages and the count is then shown as a lower bound.
 */
internal class ActivityPager(
    private val scope: CoroutineScope,
    private val repository: ActivityRepository,
    private val local: MutableStateFlow<TodayLocalState>,
) {
    private var autoPages = 0

    fun start() {
        scope.launch { repository.observeActivity().collect { onFeed(it) } }
    }

    /** After a full refresh the cache holds page 1 again, so the automatic budget starts over. */
    fun reset() {
        autoPages = 0
    }

    fun loadMore(auto: Boolean = false) {
        if (local.value.isLoadingMore) return
        if (auto) autoPages++
        local.update { it.copy(isLoadingMore = true, loadMoreFailed = false) }
        scope.launch {
            val result = repository.loadMore()
            local.update { it.copy(isLoadingMore = false, loadMoreFailed = result.isFailure) }
        }
    }

    private fun onFeed(synced: Synced<ActivityFeed>) {
        // Only page past a real sync; a cold cache's has-more flag means nothing yet.
        if (synced.lastSyncedAt == null) return
        val state = local.value
        val merged = ActivityRules.merge(synced.data.items)
        val mayHoldUnread = ActivityRules.olderPagesMayHoldUnread(merged, synced.data.hasMore) { it.read }
        when {
            !mayHoldUnread -> local.update { it.copy(autoPagingCapped = false) }
            autoPages >= MAX_AUTO_PAGES -> local.update { it.copy(autoPagingCapped = true) }
            !state.loadMoreFailed && !state.isLoadingMore -> loadMore(auto = true)
        }
    }

    companion object {
        /** Up to 150 more activities (5 pages of 30) are fetched without the person scrolling. */
        const val MAX_AUTO_PAGES = 5
    }
}
