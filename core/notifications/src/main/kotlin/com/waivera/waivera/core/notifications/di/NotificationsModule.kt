package com.waivera.waivera.core.notifications.di

import com.waivera.waivera.core.notifications.PushMessageHandler
import com.waivera.waivera.core.notifications.action.ChatNotificationActions
import com.waivera.waivera.core.notifications.presentation.AndroidNotifier
import com.waivera.waivera.core.notifications.presentation.Notifier
import com.waivera.waivera.core.notifications.token.FirebasePushTokenProvider
import com.waivera.waivera.core.notifications.token.PushTokenProvider
import com.waivera.waivera.core.notifications.token.PushTokenSink
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Scope for work started by the FCM service and notification receivers, which outlive their callers. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class NotificationsScope

@Module
@InstallIn(SingletonComponent::class)
internal abstract class NotificationsModule {
    @Binds
    abstract fun notifier(impl: AndroidNotifier): Notifier

    @Binds
    abstract fun tokenProvider(impl: FirebasePushTokenProvider): PushTokenProvider

    companion object {
        @Provides
        @Singleton
        @NotificationsScope
        fun scope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}

/**
 * Entry point for framework-created classes (FCM service, broadcast receivers), so they get
 * dependencies without `lateinit` field injection.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface NotificationsEntryPoint {
    fun pushHandler(): PushMessageHandler

    fun tokenSink(): PushTokenSink

    fun notifier(): Notifier

    fun chatActions(): ChatNotificationActions

    @NotificationsScope
    fun scope(): CoroutineScope
}
