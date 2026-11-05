package com.waivera.waivera.feature.today

import com.waivera.waivera.core.data.model.ChangeRequest
import com.waivera.waivera.core.data.repository.RequestRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * The requests behind Needs you, read from the server's live pending list rather than their
 * notifications, which keep saying "pending" after another approver decides. Only for someone
 * who may review requests (the web's Admin Requests rule); the list is shared with the Requests
 * tab, so a decision there clears it here too.
 */
internal class WaitingRequests(private val requests: RequestRepository) {
    val items: Flow<List<ChangeRequest>> = combine(requests.access, requests.waiting) { access, waiting ->
        if (access.showsRequests) waiting.orEmpty() else emptyList()
    }

    suspend fun refresh() {
        requests.ensureAccess()
        if (requests.access.value.showsRequests) requests.refreshWaiting()
    }
}
