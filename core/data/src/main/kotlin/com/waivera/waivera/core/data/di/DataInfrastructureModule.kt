package com.waivera.waivera.core.data.di

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
import com.waivera.waivera.core.data.live.AttachmentCacheDirectory
import com.waivera.waivera.core.data.live.PushUnregisterScheduler
import com.waivera.waivera.core.data.live.WorkManagerPushUnregisterScheduler
import com.waivera.waivera.core.data.outbox.OutboxDirectory
import com.waivera.waivera.core.data.outbox.OutboxScheduler
import com.waivera.waivera.core.data.outbox.WorkManagerOutboxScheduler
import com.waivera.waivera.core.data.realtime.AndroidConnectivityMonitor
import com.waivera.waivera.core.data.realtime.AppForegroundMonitor
import com.waivera.waivera.core.data.realtime.ConnectivityMonitor
import com.waivera.waivera.core.data.realtime.ProcessForegroundMonitor
import com.waivera.waivera.core.data.store.DataStoreSettingsStore
import com.waivera.waivera.core.data.store.SecureStore
import com.waivera.waivera.core.data.store.SettingsStore
import com.waivera.waivera.core.data.store.TinkSecureStore
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.time.Clock
import javax.inject.Singleton

/**
 * Infrastructure shared by live and demo builds: clock, Room, stores, platform signals and the
 * Firebase gateway. Everything here is lazy, so demo mode never opens the database or touches
 * Firebase unless a live repository is actually requested.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class DataInfrastructureModule {
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

    @Suppress("TooManyFunctions")
    companion object {
        @Provides
        @Singleton
        fun clock(): Clock = Clock.systemUTC()

        @Provides
        @Singleton
        fun database(@ApplicationContext context: Context): WaiveraDatabase =
            Room.databaseBuilder(context, WaiveraDatabase::class.java, WaiveraDatabase.NAME)
                // A cache of server state: rebuilding it is always safe.
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()

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

        /** In filesDir, not cacheDir: queued attachments must survive cache trimming. */
        @Provides
        @OutboxDirectory
        fun outboxDirectory(@ApplicationContext context: Context): File = File(context.filesDir, "outbox")

        @Provides
        @AttachmentCacheDirectory
        fun attachmentDirectory(@ApplicationContext context: Context): File = File(context.cacheDir, "attachments")
    }
}
