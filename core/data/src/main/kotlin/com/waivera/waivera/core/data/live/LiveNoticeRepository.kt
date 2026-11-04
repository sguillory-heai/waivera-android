package com.waivera.waivera.core.data.live

import com.waivera.waivera.core.data.model.Notice
import com.waivera.waivera.core.data.model.NoticeFeed
import com.waivera.waivera.core.data.model.SyncStatus
import com.waivera.waivera.core.data.model.Synced
import com.waivera.waivera.core.data.repository.NoticeRepository
import com.waivera.waivera.core.network.api.NotificationApi
import com.waivera.waivera.core.network.error.WaiveraError
import com.waivera.waivera.core.network.error.apiCall
import com.waivera.waivera.core.network.error.catchingWaivera
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The web bell's notifications, held in memory: the server is the record and the list is short,
 * so a cold start shows the skeleton for one call instead of a stale copy. The unread count comes
 * from the server's own count, so it includes rows older than the first page.
 */
@Singleton
class LiveNoticeRepository @Inject constructor(private val api: NotificationApi, private val clock: Clock) :
    NoticeRepository,
    SignOutCleaner {
    private val state = MutableStateFlow(Synced(NoticeFeed()))
    private val refreshLock = Mutex()

    /** Bumped on sign-out, so a fetch still in flight never writes the last account's list back. */
    @Volatile private var generation = 0

    /** Read here but maybe not yet on the server's page; kept read until a page agrees. */
    private val readLocally = mutableSetOf<String>()

    override fun observeNotices(): Flow<Synced<NoticeFeed>> = state.asStateFlow()

    override suspend fun refresh(): Result<Unit> = refreshLock.withLock {
        val started = generation
        state.update { it.copy(status = SyncStatus.Refreshing) }
        catchingWaivera {
            val items = apiCall { api.getNotifications() }.mapNotNull(NoticeMapping::notice)
            // The count is a nicety: when it fails the page's own unread rows stand in.
            val unread = catchingWaivera { apiCall { api.getUnreadCount() }.data }.getOrNull()
            NoticeFeed(items, unread ?: items.count { !it.read })
        }.onSuccess { feed ->
            if (generation == started) {
                state.update { current -> Synced(merged(feed, current.data), SyncStatus.Idle, Instant.now(clock)) }
            }
        }.onFailure { error ->
            if (generation == started) {
                state.update { it.copy(status = SyncStatus.Failed(WaiveraError.from(error))) }
            }
        }.map { }
    }

    /**
     * The fetched page, keeping what changed on the phone while it was in flight: rows read here
     * stay read, and rows that arrived live and are newer than the page stay at the top.
     */
    private fun merged(fetched: NoticeFeed, current: NoticeFeed): NoticeFeed = synchronized(readLocally) {
        readLocally.removeAll(fetched.items.filter { it.read }.mapTo(mutableSetOf()) { it.id })
        val fetchedIds = fetched.items.mapTo(mutableSetOf()) { it.id }
        val newest = fetched.items.mapNotNull { it.createdAt }.maxOrNull()
        val arrived = current.items.filter { n ->
            n.id !in fetchedIds && newest != null && n.createdAt != null && n.createdAt > newest
        }
        val stale = fetched.items.count { it.id in readLocally && !it.read }
        val items = arrived + fetched.items.map { if (it.id in readLocally) it.copy(read = true) else it }
        NoticeFeed(items, (fetched.unreadCount - stale + arrived.count { !it.read }).coerceAtLeast(0))
    }

    override suspend fun onArrived(notice: Notice) {
        state.update { current ->
            val known = current.data.items.any { it.id == notice.id }
            if (known) return@update current
            val items = listOf(notice) + current.data.items
            val unread = current.data.unreadCount + if (notice.read) 0 else 1
            current.copy(data = NoticeFeed(items, unread))
        }
    }

    override suspend fun markRead(id: String): Result<Unit> {
        val before = state.value.data
        // Unknown or already read: nothing to change or send.
        if (before.items.none { it.id == id && !it.read }) return Result.success(Unit)
        synchronized(readLocally) { readLocally += id }
        state.update { it.copy(data = before.markRead(setOf(id))) }
        return catchingWaivera { apiCall { api.markRead(id) } }.map { }.onFailure { rollBack(setOf(id)) }
    }

    override suspend fun markAllRead(): Result<Unit> {
        val unreadIds = state.value.data.items.filter { !it.read }.map { it.id }.toSet()
        val unreadBefore = state.value.data.unreadCount
        if (unreadBefore == 0 && unreadIds.isEmpty()) return Result.success(Unit)
        synchronized(readLocally) { readLocally += unreadIds }
        state.update { it.copy(data = NoticeFeed(it.data.items.map { n -> n.copy(read = true) }, 0)) }
        return catchingWaivera { apiCall { api.markAllRead() } }.map { }.onFailure {
            synchronized(readLocally) { readLocally -= unreadIds }
            state.update { current ->
                val items = current.data.items.map { n -> if (n.id in unreadIds) n.copy(read = false) else n }
                current.copy(data = NoticeFeed(items, unreadBefore))
            }
        }
    }

    private fun rollBack(ids: Set<String>) {
        synchronized(readLocally) { readLocally -= ids }
        state.update { current -> current.copy(data = current.data.rolledBack(ids)) }
    }

    private fun NoticeFeed.rolledBack(ids: Set<String>): NoticeFeed {
        val items = this.items.map { if (it.id in ids) it.copy(read = false) else it }
        return NoticeFeed(items, unreadCount + ids.size)
    }

    override suspend fun clear() {
        generation++
        synchronized(readLocally) { readLocally.clear() }
        state.value = Synced(NoticeFeed())
    }
}

private fun NoticeFeed.markRead(ids: Set<String>): NoticeFeed {
    val newlyRead = items.count { it.id in ids && !it.read }
    return NoticeFeed(
        items = items.map { if (it.id in ids) it.copy(read = true) else it },
        unreadCount = (unreadCount - newlyRead).coerceAtLeast(0),
    )
}
