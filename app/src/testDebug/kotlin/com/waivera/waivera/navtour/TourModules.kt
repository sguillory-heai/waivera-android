package com.waivera.waivera.navtour

import android.content.Context
import androidx.room.Room
import com.waivera.waivera.core.data.auth.AuthGateway
import com.waivera.waivera.core.data.auth.FirebaseAuthGateway
import com.waivera.waivera.core.data.db.ActivityDao
import com.waivera.waivera.core.data.db.BookmarkDao
import com.waivera.waivera.core.data.db.ConversationDao
import com.waivera.waivera.core.data.db.DocumentDao
import com.waivera.waivera.core.data.db.MessageDao
import com.waivera.waivera.core.data.db.OutboxDao
import com.waivera.waivera.core.data.db.PersonDao
import com.waivera.waivera.core.data.db.TransactionRunner
import com.waivera.waivera.core.data.db.WaiveraDatabase
import com.waivera.waivera.core.data.di.DataInfrastructureModule
import com.waivera.waivera.core.data.di.HandledTodayModule
import com.waivera.waivera.core.data.image.AuthenticatedImageLoader
import com.waivera.waivera.core.data.live.AttachmentCacheDirectory
import com.waivera.waivera.core.data.live.ChatCache
import com.waivera.waivera.core.data.live.LiveAttachmentRepository
import com.waivera.waivera.core.data.live.LivePeopleRepository
import com.waivera.waivera.core.data.live.PushUnregisterScheduler
import com.waivera.waivera.core.data.live.SignOutCleaner
import com.waivera.waivera.core.data.live.WorkManagerPushUnregisterScheduler
import com.waivera.waivera.core.data.outbox.OutboxDirectory
import com.waivera.waivera.core.data.outbox.OutboxScheduler
import com.waivera.waivera.core.data.outbox.OutboxStore
import com.waivera.waivera.core.data.outbox.WorkManagerOutboxScheduler
import com.waivera.waivera.core.data.realtime.AndroidConnectivityMonitor
import com.waivera.waivera.core.data.realtime.AppForegroundMonitor
import com.waivera.waivera.core.data.realtime.ConnectivityMonitor
import com.waivera.waivera.core.data.realtime.ProcessForegroundMonitor
import com.waivera.waivera.core.data.repository.ActivityRepository
import com.waivera.waivera.core.data.repository.AttachmentRepository
import com.waivera.waivera.core.data.repository.BookmarkRepository
import com.waivera.waivera.core.data.repository.CalendarRepository
import com.waivera.waivera.core.data.repository.ConversationRepository
import com.waivera.waivera.core.data.repository.DataLayerStarter
import com.waivera.waivera.core.data.repository.DurableMessageSender
import com.waivera.waivera.core.data.repository.HandledTodayRepository
import com.waivera.waivera.core.data.repository.MessageRepository
import com.waivera.waivera.core.data.repository.PeopleRepository
import com.waivera.waivera.core.data.repository.ProfileRepository
import com.waivera.waivera.core.data.repository.PushRegistrationRepository
import com.waivera.waivera.core.data.repository.RealtimeStatusRepository
import com.waivera.waivera.core.data.repository.SessionRepository
import com.waivera.waivera.core.data.repository.SettingsRepository
import com.waivera.waivera.core.data.repository.ThreadRepository
import com.waivera.waivera.core.data.store.DataStoreSettingsStore
import com.waivera.waivera.core.data.store.SecureStore
import com.waivera.waivera.core.data.store.SettingsStore
import com.waivera.waivera.core.data.store.TinkSecureStore
import com.waivera.waivera.core.demo.DemoActivityRepository
import com.waivera.waivera.core.demo.DemoAttachmentRepository
import com.waivera.waivera.core.demo.DemoBackend
import com.waivera.waivera.core.demo.DemoBookmarkRepository
import com.waivera.waivera.core.demo.DemoConversationRepository
import com.waivera.waivera.core.demo.DemoMessageRepository
import com.waivera.waivera.core.demo.DemoPeopleRepository
import com.waivera.waivera.core.demo.DemoProfileRepository
import com.waivera.waivera.core.demo.DemoPushRegistrationRepository
import com.waivera.waivera.core.demo.DemoRealtimeClient
import com.waivera.waivera.core.demo.DemoRealtimeStatusRepository
import com.waivera.waivera.core.demo.DemoSettingsRepository
import com.waivera.waivera.core.demo.DemoThreadRepository
import com.waivera.waivera.core.demo.di.DemoSwitchModule
import com.waivera.waivera.core.network.AuthTokenProvider
import com.waivera.waivera.core.notifications.PushMessageHandler
import com.waivera.waivera.core.notifications.channel.NotificationChannelInstaller
import com.waivera.waivera.core.realtime.RealtimeClient
import com.waivera.waivera.core.testing.InMemoryHandledTodayRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import dagger.multibindings.IntoSet
import dagger.multibindings.Multibinds
import java.io.File
import java.time.Clock
import javax.inject.Singleton

/**
 * The real app graph with the demo data layer, as a debug build in demo mode has it, except:
 * the session is [TourSessionRepository] (every gate is reachable), the calendar is
 * [TourCalendarRepository] (Sprout meetings get a lobby URL) and the scripted demo hub never
 * starts, so no typing, mention or reaction lands in the middle of a path. Debug unit tests only:
 * release builds have no `DemoSwitchModule`.
 */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [DemoSwitchModule::class])
@Suppress("TooManyFunctions")
abstract class TourRepositoriesModule {
    @Binds
    abstract fun session(impl: TourSessionRepository): SessionRepository

    @Binds
    abstract fun conversations(impl: DemoConversationRepository): ConversationRepository

    @Binds
    abstract fun messages(impl: DemoMessageRepository): MessageRepository

    @Binds
    abstract fun durableSends(impl: DemoMessageRepository): DurableMessageSender

    @Binds
    abstract fun threads(impl: DemoThreadRepository): ThreadRepository

    @Binds
    abstract fun attachments(impl: DemoAttachmentRepository): AttachmentRepository

    @Binds
    abstract fun bookmarks(impl: DemoBookmarkRepository): BookmarkRepository

    @Binds
    abstract fun activity(impl: DemoActivityRepository): ActivityRepository

    @Binds
    abstract fun people(impl: DemoPeopleRepository): PeopleRepository

    @Binds
    abstract fun calendar(impl: TourCalendarRepository): CalendarRepository

    @Binds
    abstract fun profile(impl: DemoProfileRepository): ProfileRepository

    @Binds
    abstract fun settings(impl: DemoSettingsRepository): SettingsRepository

    @Binds
    abstract fun push(impl: DemoPushRegistrationRepository): PushRegistrationRepository

    @Binds
    abstract fun realtimeStatus(impl: DemoRealtimeStatusRepository): RealtimeStatusRepository

    @Binds
    abstract fun realtime(impl: DemoRealtimeClient): RealtimeClient

    companion object {
        @Provides
        @Singleton
        fun backend(clock: TourClock): DemoBackend = DemoBackend(clock)

        @Provides
        fun tokens(): AuthTokenProvider = AuthTokenProvider { null }

        @Provides
        fun starter(): DataLayerStarter = DataLayerStarter { }
    }
}

/**
 * `DataInfrastructureModule` with [TourClock] as the clock, so "now" is the demo world's fixed
 * moment. Everything else is the same binding; none of it is built in demo mode except the clock.
 */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [DataInfrastructureModule::class])
abstract class TourInfrastructureModule {
    @Binds
    abstract fun secureStore(store: TinkSecureStore): SecureStore

    @Binds
    abstract fun settingsStore(store: DataStoreSettingsStore): SettingsStore

    @Binds
    abstract fun connectivity(monitor: AndroidConnectivityMonitor): ConnectivityMonitor

    @Binds
    abstract fun foreground(monitor: ProcessForegroundMonitor): AppForegroundMonitor

    @Binds
    abstract fun authGateway(gateway: FirebaseAuthGateway): AuthGateway

    @Binds
    abstract fun outboxScheduler(scheduler: WorkManagerOutboxScheduler): OutboxScheduler

    @Binds
    abstract fun pushUnregisterScheduler(scheduler: WorkManagerPushUnregisterScheduler): PushUnregisterScheduler

    @Multibinds
    abstract fun signOutCleaners(): Set<SignOutCleaner>

    @Binds
    @IntoSet
    abstract fun attachmentCleaner(repository: LiveAttachmentRepository): SignOutCleaner

    @Binds
    @IntoSet
    abstract fun avatarCleaner(repository: LivePeopleRepository): SignOutCleaner

    @Binds
    @IntoSet
    abstract fun imageCleaner(loader: AuthenticatedImageLoader): SignOutCleaner

    @Suppress("TooManyFunctions")
    companion object {
        @Provides
        @Singleton
        fun clock(tour: TourClock): Clock = tour

        @Provides
        @Singleton
        fun database(@ApplicationContext context: Context): WaiveraDatabase =
            Room.inMemoryDatabaseBuilder(context, WaiveraDatabase::class.java).build()

        @Provides
        fun transactionRunner(db: WaiveraDatabase): TransactionRunner = db.transactionRunner()

        @Provides
        fun conversationDao(db: WaiveraDatabase): ConversationDao = db.conversations()

        @Provides
        fun messageDao(db: WaiveraDatabase): MessageDao = db.messages()

        @Provides
        fun outboxDao(db: WaiveraDatabase): OutboxDao = db.outbox()

        @Provides
        fun activityDao(db: WaiveraDatabase): ActivityDao = db.activity()

        @Provides
        fun bookmarkDao(db: WaiveraDatabase): BookmarkDao = db.bookmarks()

        @Provides
        fun personDao(db: WaiveraDatabase): PersonDao = db.people()

        @Provides
        fun documentDao(db: WaiveraDatabase): DocumentDao = db.documents()

        @Provides
        @OutboxDirectory
        fun outboxDirectory(@ApplicationContext context: Context): File = File(context.filesDir, "outbox")

        @Provides
        @AttachmentCacheDirectory
        fun attachmentDirectory(@ApplicationContext context: Context): File = File(context.cacheDir, "attachments")

        @Provides
        @IntoSet
        fun cacheCleaner(cache: ChatCache): SignOutCleaner = SignOutCleaner { cache.clear() }

        @Provides
        @IntoSet
        fun outboxCleaner(store: OutboxStore, @OutboxDirectory directory: File): SignOutCleaner =
            SignOutCleaner { store.clear(directory) }
    }
}

/**
 * The handled-today tally in memory: the DataStore file outlives a Robolectric test in the same
 * process, so each tour starts at "0 handled" and its captures stay deterministic.
 */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [HandledTodayModule::class])
object TourHandledTodayModule {
    @Provides
    @Singleton
    fun handledToday(): HandledTodayRepository = InMemoryHandledTodayRepository()
}

/** What a tour test reaches into: the shared demo state, the session gates, the clock and push. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface TourWorld {
    fun backend(): DemoBackend

    fun session(): TourSessionRepository

    fun clock(): TourClock

    fun pushHandler(): PushMessageHandler

    fun channels(): NotificationChannelInstaller

    fun imageLoader(): AuthenticatedImageLoader
}
