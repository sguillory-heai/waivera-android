package com.waivera.waivera.core.data.live

import com.waivera.waivera.core.data.model.RealtimeStatus
import com.waivera.waivera.core.data.realtime.ConnectivityMonitor
import com.waivera.waivera.core.data.repository.RealtimeStatusRepository
import com.waivera.waivera.core.network.di.ApplicationScope
import com.waivera.waivera.core.realtime.ConnectionState
import com.waivera.waivera.core.realtime.RealtimeClient
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

@Singleton
class LiveRealtimeStatusRepository @Inject constructor(
    private val realtime: RealtimeClient,
    connectivity: ConnectivityMonitor,
    private val visible: VisibleConversation,
    @ApplicationScope scope: CoroutineScope,
) : RealtimeStatusRepository {
    override val status: StateFlow<RealtimeStatus> = combine(realtime.state, connectivity.isOnline, ::RealtimeStatus)
        .stateIn(scope, SharingStarted.Eagerly, RealtimeStatus(realtime.state.value, connectivity.isOnline.value))

    override fun retryNow() = realtime.retryNow()

    override fun setVisibleConversation(conversationId: Long?, threadRootMessageId: Long?) =
        visible.set(conversationId, threadRootMessageId)

    override fun shouldSuppressNotification(conversationId: Long): Boolean =
        realtime.state.value == ConnectionState.Connected && visible.chatId.value == conversationId
}
