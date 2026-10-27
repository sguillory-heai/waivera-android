package com.waivera.waivera.core.data.model

import com.waivera.waivera.core.network.error.WaiveraError
import java.time.Instant

/**
 * A cached value plus how it got there, so a screen can tell its four states apart:
 * loading ([isInitialLoad]), empty ([isEmpty] after a sync), error ([status] is
 * [SyncStatus.Failed], with [data] still showing the last good copy) and content.
 * Reconnect state comes from [com.waivera.waivera.core.data.repository.RealtimeStatusRepository].
 */
data class Synced<out T>(
    val data: T,
    val status: SyncStatus = SyncStatus.Idle,
    /** When the server last confirmed [data]; null when it never has (cold cache). */
    val lastSyncedAt: Instant? = null,
) {
    val isInitialLoad: Boolean get() = lastSyncedAt == null && status !is SyncStatus.Failed

    val error: WaiveraError? get() = (status as? SyncStatus.Failed)?.error

    val isRefreshing: Boolean get() = status == SyncStatus.Refreshing
}

/** True once a sync has completed and found nothing. */
val Synced<Collection<*>>.isEmpty: Boolean get() = lastSyncedAt != null && data.isEmpty()

sealed interface SyncStatus {
    data object Idle : SyncStatus

    data object Refreshing : SyncStatus

    data class Failed(val error: WaiveraError) : SyncStatus
}
