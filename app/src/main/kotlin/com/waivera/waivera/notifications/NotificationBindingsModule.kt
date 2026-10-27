package com.waivera.waivera.notifications

import com.waivera.waivera.core.data.repository.RealtimeStatusRepository
import com.waivera.waivera.core.notifications.action.ChatNotificationActions
import com.waivera.waivera.core.notifications.presentation.ConversationInfoSource
import com.waivera.waivera.core.notifications.presentation.HubConnectionMonitor
import com.waivera.waivera.core.notifications.presentation.NotificationPreferencesSource
import com.waivera.waivera.core.notifications.token.PushTokenSink
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Connects `:core:notifications` ports to the data layer. */
@Module
@InstallIn(SingletonComponent::class)
abstract class NotificationBindingsModule {
    @Binds
    abstract fun chatActions(impl: DataLayerChatNotificationActions): ChatNotificationActions

    @Binds
    abstract fun preferences(impl: SettingsNotificationPreferences): NotificationPreferencesSource

    @Binds
    abstract fun conversationInfo(impl: CachedConversationInfo): ConversationInfoSource

    @Binds
    abstract fun tokenSink(impl: RegistrationTokenSink): PushTokenSink

    companion object {
        @Provides
        fun hubConnection(realtime: RealtimeStatusRepository): HubConnectionMonitor =
            HubConnectionMonitor { realtime.status.value.isConnected }
    }
}
