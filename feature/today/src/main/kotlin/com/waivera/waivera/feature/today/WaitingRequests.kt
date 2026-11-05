package com.waivera.waivera.feature.today

import com.waivera.waivera.core.data.model.ChangeRequest
import com.waivera.waivera.core.data.repository.RequestRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine

/**
 * The requests behind Needs you, read from the server's live pending list rather than their
 * notifications, which keep saying "pending" after another approver decides. Only for someone
 * who may review requests (the web's Admin Requests rule); the list is shared with the Requests
 * tab, so a decision there clears it here too.
 */
internal class WaitingRequests(private val requests: RequestRepository) {
    private val attempted = MutableStateFlow(false)

    val state: Flow<WaitingState> = combine(requests.access, requests.waiting, attempted) { access, waiting, tried ->
        WaitingState(
            items = if (access.showsRequests) waiting.orEmpty() else emptyList(),
            settled = tried || (access.loaded && (!access.showsRequests || waiting != null)),
        )
    }

    suspend fun refresh() {
        requests.ensureAccess()
        if (requests.access.value.showsRequests) requests.refreshWaiting()
        attempted.value = true
    }
}

/**
 * [settled] stays false until the pending list has answered once (or was not needed), so a cold
 * start never shows the caught-up reward just before request cards arrive.
 */
internal data class WaitingState(val items: List<ChangeRequest> = emptyList(), val settled: Boolean = false)
