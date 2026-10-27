package com.waivera.waivera.core.data.live

import com.waivera.waivera.core.data.db.DocumentDao
import com.waivera.waivera.core.data.db.DocumentEntity
import com.waivera.waivera.core.data.model.SyncStatus
import com.waivera.waivera.core.data.model.Synced
import com.waivera.waivera.core.network.error.WaiveraError
import com.waivera.waivera.core.network.error.catchingWaivera
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Tracks, per cache key, whether a refresh is running or failed (in memory) and when the server
 * last confirmed the data (persisted, so a warm start is not shown as a first load).
 */
@Singleton
class SyncTracker @Inject constructor(private val documents: DocumentDao, private val clock: Clock) {
    private val statuses = MutableStateFlow<Map<String, SyncStatus>>(emptyMap())
    private val locks = mutableMapOf<String, Mutex>()
    private val locksGuard = Mutex()

    fun status(key: String): Flow<SyncStatus> = statuses.map { it[key] ?: SyncStatus.Idle }.distinctUntilChanged()

    fun lastSynced(key: String): Flow<Instant?> =
        documents.observe(metaKey(key)).map { row -> row?.updatedAt?.let(Instant::ofEpochMilli) }.distinctUntilChanged()

    suspend fun lastSyncedNow(key: String): Instant? =
        documents.get(metaKey(key))?.updatedAt?.let(Instant::ofEpochMilli)

    /** Runs [block] once at a time per key, recording the outcome. */
    suspend fun <T> track(key: String, block: suspend () -> T): Result<T> {
        val lock = locksGuard.withLock { locks.getOrPut(key) { Mutex() } }
        return lock.withLock {
            statuses.update { it + (key to SyncStatus.Refreshing) }
            val result = catchingWaivera(block)
            result.onSuccess {
                documents.upsert(DocumentEntity(metaKey(key), clock.millis(), "{}"))
                statuses.update { it - key }
            }.onFailure { error ->
                statuses.update { it + (key to SyncStatus.Failed(WaiveraError.from(error))) }
            }
            result
        }
    }

    suspend fun forget(key: String) {
        documents.delete(metaKey(key))
        statuses.update { it - key }
    }

    fun reset() {
        statuses.value = emptyMap()
    }

    fun <T> synced(key: String, data: Flow<T>): Flow<Synced<T>> =
        combine(data, status(key), lastSynced(key)) { value, status, last -> Synced(value, status, last) }

    private fun metaKey(key: String) = "sync:$key"
}
