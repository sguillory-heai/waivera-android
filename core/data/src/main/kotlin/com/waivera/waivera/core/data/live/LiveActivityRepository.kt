package com.waivera.waivera.core.data.live

import com.waivera.waivera.core.data.model.ActivityFeed
import com.waivera.waivera.core.data.model.ActivityItem
import com.waivera.waivera.core.data.model.Synced
import com.waivera.waivera.core.data.repository.ActivityRepository
import com.waivera.waivera.core.network.api.ChatApi
import com.waivera.waivera.core.network.error.WaiveraError
import com.waivera.waivera.core.network.error.apiCall
import com.waivera.waivera.core.network.error.apiCallUnit
import com.waivera.waivera.core.network.error.catchingWaivera
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable

@Serializable
data class ActivityMeta(val nextPage: Int = 2, val hasMore: Boolean = true)

/** Mentions, thread replies and reactions (06-activity). Pages of 30, has-more = full page. */
@Singleton
class LiveActivityRepository @Inject constructor(
    private val chatApi: ChatApi,
    private val cache: ChatCache,
    private val sync: SyncTracker,
) : ActivityRepository {
    private val pageLock = Mutex()

    override fun observeActivity(): Flow<Synced<ActivityFeed>> {
        val feed =
            combine(cache.observeActivity(), cache.observeDocument(META, ActivityMeta.serializer())) { items, meta ->
                ActivityFeed(items, meta?.hasMore ?: true)
            }
        return sync.synced(KEY, feed)
    }

    override suspend fun refresh(): Result<Unit> = sync.track(KEY) {
        val page = fetch(1)
        cache.transactions.run {
            cache.activityDao.clear()
            cache.putActivity(page)
            cache.putDocument(META, ActivityMeta.serializer(), ActivityMeta(2, page.size >= ChatApi.ACTIVITY_PAGE_SIZE))
        }
    }

    /**
     * One page at a time: a second call while one runs waits for it and then reads the
     * advanced cursor, so the same page is never fetched twice.
     */
    override suspend fun loadMore(): Result<Boolean> = catchingWaivera {
        pageLock.withLock {
            val meta = cache.document(META, ActivityMeta.serializer()) ?: ActivityMeta()
            if (!meta.hasMore) return@withLock false
            val page = fetch(meta.nextPage)
            cache.putActivity(page)
            val hasMore = page.size >= ChatApi.ACTIVITY_PAGE_SIZE
            cache.putDocument(META, ActivityMeta.serializer(), ActivityMeta(meta.nextPage + 1, hasMore))
            hasMore
        }
    }

    private suspend fun fetch(page: Int): List<ActivityItem> = apiCall {
        chatApi.getRecentActivity(page, ChatApi.ACTIVITY_PAGE_SIZE)
    }.filter { it.id > 0 }.map(Mappers::activity)

    /** Optimistic: the row reads as read at once and goes back to unread if the call fails. */
    override suspend fun markRead(activityId: Long): Result<Unit> = catchingWaivera {
        val changed = cache.activity(listOf(activityId)).filter { !it.read }
        markLocally(changed.map { it.id })
        try {
            apiCallUnit { chatApi.markActivityRead(activityId) }
        } catch (error: WaiveraError) {
            restoreUnread(changed.map { it.id })
            throw error
        }
    }

    override suspend fun markAllRead(): Result<Unit> = catchingWaivera {
        val changed = cache.allActivity().filter { !it.read }
        cache.putActivity(changed.map { it.copy(read = true) })
        try {
            apiCallUnit { chatApi.markAllActivityRead() }
        } catch (error: WaiveraError) {
            restoreUnread(changed.map { it.id })
            throw error
        }
    }

    /**
     * Rolls back an optimistic read. Rows read meanwhile by the server (hub `ChatReadState`)
     * cannot be told apart, so a refresh after the failure settles them.
     */
    private suspend fun restoreUnread(ids: List<Long>) {
        if (ids.isEmpty()) return
        cache.putActivity(cache.activity(ids).map { it.copy(read = false) })
    }

    suspend fun markLocally(ids: List<Long>) {
        if (ids.isEmpty()) return
        cache.putActivity(cache.activity(ids).map { it.copy(read = true) })
    }

    suspend fun onActivity(item: ActivityItem) = cache.putActivity(listOf(item))

    suspend fun onRemoved(id: Long) = cache.activityDao.delete(listOf(id))

    companion object {
        const val KEY = "activity"
        private const val META = "activity:meta"
    }
}
