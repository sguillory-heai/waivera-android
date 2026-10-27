package com.waivera.waivera.core.data

import com.waivera.waivera.core.data.live.ChatCache
import com.waivera.waivera.core.data.live.LiveActivityRepository
import com.waivera.waivera.core.data.live.LiveBookmarkRepository
import com.waivera.waivera.core.data.live.LiveCalendarRepository
import com.waivera.waivera.core.data.live.LiveConversationRepository
import com.waivera.waivera.core.data.live.LiveMessageRepository
import com.waivera.waivera.core.data.live.LivePeopleRepository
import com.waivera.waivera.core.data.live.LiveProfileRepository
import com.waivera.waivera.core.data.live.LivePushRegistrationRepository
import com.waivera.waivera.core.data.live.LiveSettingsRepository
import com.waivera.waivera.core.data.live.LiveThreadRepository
import com.waivera.waivera.core.data.live.MessageIngest
import com.waivera.waivera.core.data.live.PushUnregisterScheduler
import com.waivera.waivera.core.data.live.SyncTracker
import com.waivera.waivera.core.data.live.TypingTracker
import com.waivera.waivera.core.data.live.VisibleConversation
import com.waivera.waivera.core.data.outbox.OutboxProcessor
import com.waivera.waivera.core.data.outbox.OutboxStore
import com.waivera.waivera.core.data.sync.RealtimeSync
import com.waivera.waivera.core.network.api.AuthenticationApi
import com.waivera.waivera.core.network.api.CalendarApi
import com.waivera.waivera.core.network.api.ChatApi
import com.waivera.waivera.core.network.api.EmployeeApi
import com.waivera.waivera.core.network.api.FileApi
import com.waivera.waivera.core.network.api.MobilePushApi
import com.waivera.waivera.core.network.api.TimeOffApi
import com.waivera.waivera.core.network.api.VideoApi
import com.waivera.waivera.core.testing.FakeConnectivityMonitor
import com.waivera.waivera.core.testing.FakeRealtimeClient
import com.waivera.waivera.core.testing.FixtureServer
import com.waivera.waivera.core.testing.InMemoryDatabase
import com.waivera.waivera.core.testing.InMemorySecureStore
import com.waivera.waivera.core.testing.InMemorySettingsStore
import com.waivera.waivera.core.testing.RecordingOutboxScheduler
import com.waivera.waivera.core.testing.TestClock
import com.waivera.waivera.core.testing.TestSessionRepository
import java.nio.file.Files
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

/** Live repositories wired to a fixture server and in-memory storage, as Hilt would wire them. */
class TestGraph(val scope: CoroutineScope, connected: Boolean = true) : AutoCloseable {
    val server = FixtureServer()
    private val retrofit = server.retrofit()
    val chatApi: ChatApi = retrofit.create(ChatApi::class.java)
    val authApi: AuthenticationApi = retrofit.create(AuthenticationApi::class.java)
    val calendarApi: CalendarApi = retrofit.create(CalendarApi::class.java)
    val employeeApi: EmployeeApi = retrofit.create(EmployeeApi::class.java)
    val timeOffApi: TimeOffApi = retrofit.create(TimeOffApi::class.java)
    val fileApi: FileApi = retrofit.create(FileApi::class.java)
    val videoApi: VideoApi = retrofit.create(VideoApi::class.java)
    val pushApi: MobilePushApi = retrofit.create(MobilePushApi::class.java)

    val clock = TestClock()
    val db = InMemoryDatabase()
    val secureStore = InMemorySecureStore()
    val settingsStore = InMemorySettingsStore()
    val session = TestSessionRepository()
    val realtime = FakeRealtimeClient(connected)
    val scheduler = RecordingOutboxScheduler()
    val visible = VisibleConversation()

    /** Conversations whose notifications the data layer asked to clear. */
    val readsCleared = mutableListOf<Long>()
    val outboxDir = Files.createTempDirectory("outbox").toFile()

    /** The Firebase ID token the push repository captures; null once Firebase has signed out. */
    var idToken: String? = "test-token"
    var unregisterRetriesScheduled = 0
        private set
    private val unregisterScheduler = PushUnregisterScheduler { unregisterRetriesScheduled++ }

    val cache =
        ChatCache(
            db.conversations,
            db.messages,
            db.activity,
            db.bookmarks,
            db.people,
            db.documents,
            db.transactions,
            clock,
        )
    val sync = SyncTracker(db.documents, clock)
    val outbox = OutboxStore(db.outbox)
    val ingest = MessageIngest(cache, outbox)
    val typing = TypingTracker(realtime, clock, scope)
    val conversations =
        LiveConversationRepository(chatApi, videoApi, server.config, cache, sync, session, typing, visible, clock) {
            readsCleared += it
        }
    val connectivity = FakeConnectivityMonitor()
    val processor = OutboxProcessor(outbox, chatApi, realtime, ingest, session, connectivity)
    val messages = LiveMessageRepository(
        chatApi, cache, sync, ingest, outbox, processor, scheduler, realtime, session, conversations, typing, clock,
        outboxDir, Dispatchers.IO, scope,
    )
    val threads = LiveThreadRepository(chatApi, cache, sync, ingest, outbox, realtime, session, scope)
    val bookmarks = LiveBookmarkRepository(chatApi, cache, sync)
    val activity = LiveActivityRepository(chatApi, cache, sync)
    val people = LivePeopleRepository(fileApi, authApi, cache, sync, clock)
    val calendar = LiveCalendarRepository(calendarApi, videoApi, cache, sync, session, server.config, clock, scope)
    val profile = LiveProfileRepository(employeeApi, timeOffApi, authApi, cache, sync, session)
    val settings = LiveSettingsRepository(settingsStore)
    val push = LivePushRegistrationRepository(
        pushApi, secureStore, session, settings, { idToken }, unregisterScheduler, clock, Dispatchers.IO,
    )
    val realtimeSync =
        RealtimeSync(cache, ingest, conversations, messages, threads, bookmarks, activity, calendar, typing, session)

    override fun close() {
        server.close()
        outboxDir.deleteRecursively()
    }
}
