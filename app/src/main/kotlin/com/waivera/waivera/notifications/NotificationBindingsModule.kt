package com.waivera.waivera.notifications

import com.waivera.waivera.core.data.live.ConversationReadListener
import com.waivera.waivera.core.data.live.PushTokenRevoker
import com.waivera.waivera.core.data.live.SignOutCleaner
import com.waivera.waivera.core.data.repository.RealtimeStatusRepository
import com.waivera.waivera.core.notifications.action.ChatNotificationActions
import com.waivera.waivera.core.notifications.presentation.ConversationInfoSource
import com.waivera.waivera.core.notifications.presentation.LiveConversationMonitor
import com.waivera.waivera.core.notifications.presentation.NotificationPreferencesSource
import com.waivera.waivera.core.notifications.presentation.Notifier
import com.waivera.waivera.core.notifications.presentation.PushSessionGate
import com.waivera.waivera.core.notifications.presentation.ThreadRootLookup
import com.waivera.waivera.core.notifications.token.PushTokenSink
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

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

    @Binds
    abstract fun sessionGate(impl: SessionPushGate): PushSessionGate

    @Binds
    abstract fun threadRoots(impl: CachedThreadRoots): ThreadRootLookup

    @Binds
    abstract fun tokenRevoker(impl: FirebasePushTokenRevoker): PushTokenRevoker

    companion object {
        /** Foreground suppression asks the data layer: hub connected and that conversation on screen. */
        @Provides
        fun liveConversation(realtime: RealtimeStatusRepository): LiveConversationMonitor =
            LiveConversationMonitor { conversationId -> realtime.shouldSuppressNotification(conversationId) }

        /** A conversation read here or on another device leaves the shade too (IF-07). */
        @Provides
        fun conversationReads(notifier: Notifier): ConversationReadListener =
            ConversationReadListener(notifier::cancelConversation)

        /** A session end clears the shade too, even with no screen alive to do it (AS-02). */
        @Provides
        @IntoSet
        fun notificationCleaner(notifier: Notifier): SignOutCleaner = SignOutCleaner { notifier.cancelAll() }
    }
}
