package com.waivera.waivera.core.network

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * 401s that survived a freshly refreshed token: Firebase still accepts the user but sprout_api
 * may have disabled or removed the account. The session checks the bootstrap again and ends
 * only if that is rejected too (IF-05), so one misbehaving endpoint cannot sign anyone out.
 */
@Singleton
class SessionRejections @Inject constructor() {
    private val reports = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    val events: SharedFlow<Unit> = reports.asSharedFlow()

    /** Called from OkHttp threads; never blocks. */
    fun report() {
        reports.tryEmit(Unit)
    }
}
