package com.waivera.waivera.core.demo.di

import com.waivera.waivera.core.data.auth.GatewayTokenProvider
import com.waivera.waivera.core.data.live.LiveActivityRepository
import com.waivera.waivera.core.data.live.LiveAttachmentRepository
import com.waivera.waivera.core.data.live.LiveBookmarkRepository
import com.waivera.waivera.core.data.live.LiveCalendarRepository
import com.waivera.waivera.core.data.live.LiveConversationRepository
import com.waivera.waivera.core.data.live.LiveMessageRepository
import com.waivera.waivera.core.data.live.LivePeopleRepository
import com.waivera.waivera.core.data.live.LiveProfileRepository
import com.waivera.waivera.core.data.live.LivePushRegistrationRepository
import com.waivera.waivera.core.data.live.LiveRealtimeStatusRepository
import com.waivera.waivera.core.data.live.LiveSessionRepository
import com.waivera.waivera.core.data.live.LiveSettingsRepository
import com.waivera.waivera.core.data.live.LiveThreadRepository
import com.waivera.waivera.core.data.repository.ActivityRepository
import com.waivera.waivera.core.data.repository.AttachmentRepository
import com.waivera.waivera.core.data.repository.BookmarkRepository
import com.waivera.waivera.core.data.repository.CalendarRepository
import com.waivera.waivera.core.data.repository.ConversationRepository
import com.waivera.waivera.core.data.repository.DataLayerStarter
import com.waivera.waivera.core.data.repository.DurableMessageSender
import com.waivera.waivera.core.data.repository.MessageRepository
import com.waivera.waivera.core.data.repository.PeopleRepository
import com.waivera.waivera.core.data.repository.ProfileRepository
import com.waivera.waivera.core.data.repository.PushRegistrationRepository
import com.waivera.waivera.core.data.repository.RealtimeStatusRepository
import com.waivera.waivera.core.data.repository.SessionRepository
import com.waivera.waivera.core.data.repository.SettingsRepository
import com.waivera.waivera.core.data.repository.ThreadRepository
import com.waivera.waivera.core.data.sync.LiveDataLayerStarter
import com.waivera.waivera.core.demo.DemoActivityRepository
import com.waivera.waivera.core.demo.DemoAttachmentRepository
import com.waivera.waivera.core.demo.DemoBackend
import com.waivera.waivera.core.demo.DemoBookmarkRepository
import com.waivera.waivera.core.demo.DemoCalendarRepository
import com.waivera.waivera.core.demo.DemoConversationRepository
import com.waivera.waivera.core.demo.DemoHub
import com.waivera.waivera.core.demo.DemoMessageRepository
import com.waivera.waivera.core.demo.DemoMode
import com.waivera.waivera.core.demo.DemoPeopleRepository
import com.waivera.waivera.core.demo.DemoProfileRepository
import com.waivera.waivera.core.demo.DemoPushRegistrationRepository
import com.waivera.waivera.core.demo.DemoRealtimeClient
import com.waivera.waivera.core.demo.DemoRealtimeStatusRepository
import com.waivera.waivera.core.demo.DemoSessionRepository
import com.waivera.waivera.core.demo.DemoSettingsRepository
import com.waivera.waivera.core.demo.DemoThreadRepository
import com.waivera.waivera.core.network.AuthTokenProvider
import com.waivera.waivera.core.realtime.DefaultRealtimeClient
import com.waivera.waivera.core.realtime.RealtimeClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Provider
import javax.inject.Singleton

/**
 * Debug builds: chooses live or demo for every repository once per process, per [DemoMode].
 * Providers keep the unused side from ever being constructed, so demo mode opens no database,
 * socket or Firebase app. Release builds do not have this module (core:demo is a
 * debugImplementation dependency) and include `LiveRepositoriesModule` instead.
 */
@Module
@InstallIn(SingletonComponent::class)
@Suppress("TooManyFunctions", "LongParameterList")
object DemoSwitchModule {
    private fun <T> pick(mode: DemoMode, live: Provider<out T>, demo: Provider<out T>): T =
        if (mode.isEnabled) demo.get() else live.get()

    @Provides
    @Singleton
    fun backend(): DemoBackend = DemoBackend()

    @Provides
    fun session(
        mode: DemoMode,
        live: Provider<LiveSessionRepository>,
        demo: Provider<DemoSessionRepository>,
    ): SessionRepository = pick(mode, live, demo)

    @Provides
    fun conversations(
        mode: DemoMode,
        live: Provider<LiveConversationRepository>,
        demo: Provider<DemoConversationRepository>,
    ): ConversationRepository = pick(mode, live, demo)

    @Provides
    fun messages(
        mode: DemoMode,
        live: Provider<LiveMessageRepository>,
        demo: Provider<DemoMessageRepository>,
    ): MessageRepository = pick(mode, live, demo)

    @Provides
    fun durableSends(
        mode: DemoMode,
        live: Provider<LiveMessageRepository>,
        demo: Provider<DemoMessageRepository>,
    ): DurableMessageSender = pick(mode, live, demo)

    @Provides
    fun threads(
        mode: DemoMode,
        live: Provider<LiveThreadRepository>,
        demo: Provider<DemoThreadRepository>,
    ): ThreadRepository = pick(mode, live, demo)

    @Provides
    fun attachments(
        mode: DemoMode,
        live: Provider<LiveAttachmentRepository>,
        demo: Provider<DemoAttachmentRepository>,
    ): AttachmentRepository = pick(mode, live, demo)

    @Provides
    fun bookmarks(
        mode: DemoMode,
        live: Provider<LiveBookmarkRepository>,
        demo: Provider<DemoBookmarkRepository>,
    ): BookmarkRepository = pick(mode, live, demo)

    @Provides
    fun activity(
        mode: DemoMode,
        live: Provider<LiveActivityRepository>,
        demo: Provider<DemoActivityRepository>,
    ): ActivityRepository = pick(mode, live, demo)

    @Provides
    fun people(
        mode: DemoMode,
        live: Provider<LivePeopleRepository>,
        demo: Provider<DemoPeopleRepository>,
    ): PeopleRepository = pick(mode, live, demo)

    @Provides
    fun calendar(
        mode: DemoMode,
        live: Provider<LiveCalendarRepository>,
        demo: Provider<DemoCalendarRepository>,
    ): CalendarRepository = pick(mode, live, demo)

    @Provides
    fun profile(
        mode: DemoMode,
        live: Provider<LiveProfileRepository>,
        demo: Provider<DemoProfileRepository>,
    ): ProfileRepository = pick(mode, live, demo)

    @Provides
    fun settings(
        mode: DemoMode,
        live: Provider<LiveSettingsRepository>,
        demo: Provider<DemoSettingsRepository>,
    ): SettingsRepository = pick(mode, live, demo)

    @Provides
    fun push(
        mode: DemoMode,
        live: Provider<LivePushRegistrationRepository>,
        demo: Provider<DemoPushRegistrationRepository>,
    ): PushRegistrationRepository = pick(mode, live, demo)

    @Provides
    fun realtimeStatus(
        mode: DemoMode,
        live: Provider<LiveRealtimeStatusRepository>,
        demo: Provider<DemoRealtimeStatusRepository>,
    ): RealtimeStatusRepository = pick(mode, live, demo)

    @Provides
    fun realtime(
        mode: DemoMode,
        live: Provider<DefaultRealtimeClient>,
        demo: Provider<DemoRealtimeClient>,
    ): RealtimeClient = pick(mode, live, demo)

    @Provides
    fun tokens(mode: DemoMode, live: Provider<GatewayTokenProvider>): AuthTokenProvider =
        if (mode.isEnabled) AuthTokenProvider { null } else live.get()

    @Provides
    fun starter(mode: DemoMode, live: Provider<LiveDataLayerStarter>, demo: Provider<DemoHub>): DataLayerStarter =
        pick(mode, live, demo)
}
