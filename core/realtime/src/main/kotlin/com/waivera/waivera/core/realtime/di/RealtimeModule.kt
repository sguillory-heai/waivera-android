package com.waivera.waivera.core.realtime.di

import com.waivera.waivera.core.realtime.HubTransportFactory
import com.waivera.waivera.core.realtime.ReconnectPolicy
import com.waivera.waivera.core.realtime.SignalRHubTransport
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * The SignalR transport and backoff policy. [com.waivera.waivera.core.realtime.RealtimeClient]
 * itself is bound in core:data (live `DefaultRealtimeClient`) or core:demo (scripted fake hub),
 * so demo mode never opens a socket.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RealtimeModule {
    @Binds
    abstract fun transportFactory(factory: SignalRHubTransport.Factory): HubTransportFactory

    companion object {
        @Provides
        fun reconnectPolicy(): ReconnectPolicy = ReconnectPolicy()
    }
}
