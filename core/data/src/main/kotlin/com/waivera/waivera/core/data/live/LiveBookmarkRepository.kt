package com.waivera.waivera.core.data.live

import com.waivera.waivera.core.data.model.Bookmark
import com.waivera.waivera.core.data.model.Message
import com.waivera.waivera.core.data.model.Synced
import com.waivera.waivera.core.data.repository.BookmarkRepository
import com.waivera.waivera.core.network.api.ChatApi
import com.waivera.waivera.core.network.error.WaiveraError
import com.waivera.waivera.core.network.error.apiCall
import com.waivera.waivera.core.network.error.catchingWaivera
import com.waivera.waivera.core.network.error.requireData
import com.waivera.waivera.core.network.log.WaiveraLog
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * Bookmarks. The bookmarks table is the source of truth once loaded and toggles update both it
 * and the cached message. G-04: `GET /api/chat/bookmarks` returns 500 when a bot message is
 * bookmarked, so a server error falls back to per-conversation search with four workers.
 */
@Singleton
class LiveBookmarkRepository @Inject constructor(
    private val chatApi: ChatApi,
    private val cache: ChatCache,
    private val sync: SyncTracker,
) : BookmarkRepository {
    override fun observeBookmarks(): Flow<Synced<List<Bookmark>>> = sync.synced(KEY, cache.observeBookmarks())

    override fun observeBookmarkedIds(): Flow<Set<Long>> =
        cache.observeBookmarks().map { list -> list.map { it.messageId }.toSet() }.distinctUntilChanged()

    override suspend fun refresh(): Result<Unit> = sync.track(KEY) {
        val messages = try {
            apiCall { chatApi.getBookmarks() }.requireData().map(Mappers::message)
        } catch (e: WaiveraError.Server) {
            WaiveraLog.w(TAG, "Bookmarks endpoint failed (${e.httpStatus}); using per-conversation search")
            fallback()
        }
        val bookmarked = messages.filter { !it.isDeleted }.map { it.copy(isBookmarked = true) }
        cache.transactions.run {
            cache.bookmarkDao.clear()
            cache.putBookmarks(bookmarked)
        }
    }

    private suspend fun fallback(): List<Message> {
        val gate = Semaphore(FALLBACK_WORKERS)
        val conversations = cache.conversations().map { it.id }
        return coroutineScope {
            conversations.map { id ->
                async { gate.withPermit { searchAllPages(id) } }
            }.awaitAll().flatten()
        }
    }

    private suspend fun searchAllPages(conversationId: Long): List<Message> {
        val found = mutableListOf<Message>()
        var page = 1
        while (page <= MAX_FALLBACK_PAGES) {
            val batch = catchingWaivera {
                apiCall { chatApi.searchConversation(conversationId, bookmarkedOnly = true, pageIndex = page) }.requireData()
            }.getOrNull().orEmpty()
            found += batch.map(Mappers::message)
            if (batch.size < ChatApi.SEARCH_PAGE_SIZE) break
            page++
        }
        return found
    }

    override suspend fun toggle(messageId: Long): Result<Boolean> = catchingWaivera {
        val now = apiCall { chatApi.toggleBookmark(messageId) }.data ?: throw WaiveraError.Unexpected()
        apply(messageId, now)
        now
    }

    /** From the toggle response or the hub's `ReceiveBookmarkUpdate` (own devices). */
    suspend fun apply(messageId: Long, isBookmarked: Boolean) {
        cache.updateMessage(messageId) { it.copy(isBookmarked = isBookmarked) }
        if (isBookmarked) {
            cache.message(messageId)?.let { cache.putBookmarks(listOf(it)) }
        } else {
            cache.bookmarkDao.delete(listOf(messageId))
        }
    }

    companion object {
        const val KEY = "bookmarks"
        private const val TAG = "Bookmarks"
        private const val FALLBACK_WORKERS = 4
        private const val MAX_FALLBACK_PAGES = 10
    }
}
