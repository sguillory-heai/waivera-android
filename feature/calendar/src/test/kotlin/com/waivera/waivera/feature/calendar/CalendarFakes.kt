package com.waivera.waivera.feature.calendar

import com.waivera.waivera.core.data.model.Conversation
import com.waivera.waivera.core.data.model.DateRange
import com.waivera.waivera.core.data.model.Milestone
import com.waivera.waivera.core.data.model.Person
import com.waivera.waivera.core.data.model.RealtimeStatus
import com.waivera.waivera.core.data.model.ScheduleEvent
import com.waivera.waivera.core.data.model.ScheduleFeed
import com.waivera.waivera.core.data.model.SyncStatus
import com.waivera.waivera.core.data.model.Synced
import com.waivera.waivera.core.data.repository.CalendarRepository
import com.waivera.waivera.core.data.repository.ConversationRepository
import com.waivera.waivera.core.data.repository.PeopleRepository
import com.waivera.waivera.core.data.repository.RealtimeStatusRepository
import com.waivera.waivera.core.network.error.WaiveraError
import com.waivera.waivera.core.realtime.ConnectionState
import java.time.Clock
import java.time.ZoneId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onStart

/**
 * A calendar backend in memory. Every range serves [events] (the fake server ignores the range),
 * a cold range refreshes on first collection like the real repository, and [refreshGate] holds
 * refreshes open so tests can see the loading state.
 */
class FakeCalendarRepository(private val clock: Clock, zone: ZoneId = ZoneId.of("America/Chicago")) :
    CalendarRepository {
    val zoneState = MutableStateFlow(zone)
    override val zone: Flow<ZoneId> = zoneState

    var events: List<ScheduleEvent> = emptyList()
    var refreshError: WaiveraError? = null
    var refreshGate: CompletableDeferred<Unit>? = null

    val observedRanges = mutableListOf<DateRange>()
    val refreshedRanges = mutableListOf<DateRange>()
    private val feeds = mutableMapOf<DateRange, MutableStateFlow<Synced<ScheduleFeed>>>()

    val milestoneState = MutableStateFlow(Synced(emptyList<Milestone>()))
    var milestones: List<Milestone> = emptyList()
    var milestoneError: WaiveraError? = null
    var milestoneRefreshes = 0
        private set

    val responses = mutableListOf<Pair<String, Boolean>>()
    var respondResult: Result<Unit> = Result.success(Unit)
    var lobbyResult: Result<String> = Result.success("https://meet.example.test/lobby/1")
    val lobbyRequests = mutableListOf<String>()

    fun feed(range: DateRange): MutableStateFlow<Synced<ScheduleFeed>> =
        feeds.getOrPut(range) { MutableStateFlow(Synced(ScheduleFeed(range, zoneState.value.id))) }

    override fun observeRange(range: DateRange): Flow<Synced<ScheduleFeed>> = feed(range).onStart {
        observedRanges += range
        if (feed(range).value.lastSyncedAt == null) refresh(range)
    }

    override suspend fun refresh(range: DateRange): Result<Unit> {
        refreshedRanges += range
        val state = feed(range)
        state.value = state.value.copy(status = SyncStatus.Refreshing)
        refreshGate?.await()
        val error = refreshError
        return if (error != null) {
            state.value = state.value.copy(status = SyncStatus.Failed(error))
            Result.failure(error)
        } else {
            state.value = Synced(ScheduleFeed(range, zoneState.value.id, events), SyncStatus.Idle, clock.instant())
            Result.success(Unit)
        }
    }

    override fun observeMilestones(): Flow<Synced<List<Milestone>>> = milestoneState

    override suspend fun refreshMilestones(): Result<Unit> {
        milestoneRefreshes++
        val error = milestoneError
        return if (error != null) {
            milestoneState.value = milestoneState.value.copy(status = SyncStatus.Failed(error))
            Result.failure(error)
        } else {
            milestoneState.value = Synced(milestones, SyncStatus.Idle, clock.instant())
            Result.success(Unit)
        }
    }

    override suspend fun respondToInvitation(eventId: String, accept: Boolean): Result<Unit> {
        responses += eventId to accept
        return respondResult
    }

    override suspend fun meetingLobbyUrl(eventId: String): Result<String> {
        lobbyRequests += eventId
        return lobbyResult
    }
}

class FakeRealtimeStatusRepository(
    initial: RealtimeStatus = RealtimeStatus(ConnectionState.Connected, isOnline = true),
) : RealtimeStatusRepository {
    val mutableStatus = MutableStateFlow(initial)
    override val status: StateFlow<RealtimeStatus> = mutableStatus

    override fun retryNow() = Unit

    override fun setVisibleConversation(conversationId: Long?) = Unit

    override fun shouldSuppressNotification(conversationId: Long): Boolean = false
}

/** Only the two calls the event screen makes are recorded; the rest are inert. */
class FakeConversationRepository : ConversationRepository {
    val opened = mutableListOf<List<String>>()
    val eventChats = mutableListOf<String>()
    var openResult: Result<Long> = Result.success(OPENED_CONVERSATION_ID)

    override fun observeConversations(): Flow<Synced<List<Conversation>>> = flowOf(Synced(emptyList()))

    override fun observeConversation(conversationId: Long): Flow<Conversation?> = flowOf(null)

    override fun observeUnreadTotal(): Flow<Int> = flowOf(0)

    override suspend fun refresh(): Result<Unit> = Result.success(Unit)

    override suspend fun refreshConversation(conversationId: Long): Result<Conversation> =
        Result.failure(WaiveraError.NotFound())

    override suspend fun openConversation(participantIds: List<String>): Result<Long> {
        opened += participantIds
        return openResult
    }

    override suspend fun conversationForEvent(calendarEventId: String): Result<Long> {
        eventChats += calendarEventId
        return openResult
    }

    override suspend fun leave(conversationId: Long): Result<Unit> = Result.success(Unit)

    override suspend fun markRead(conversationId: Long, lastVisibleMessageId: Long): Result<Unit> = Result.success(Unit)

    override fun observeTyping(conversationId: Long): Flow<Set<String>> = emptyFlow()

    override fun onComposerChanged(conversationId: Long, isEmpty: Boolean) = Unit

    companion object {
        const val OPENED_CONVERSATION_ID = 4_242L
    }
}

class FakePeopleRepository : PeopleRepository {
    override fun observeDirectory(): Flow<Synced<List<Person>>> = flowOf(Synced(emptyList()))

    override fun observePerson(userId: String): Flow<Person?> = flowOf(null)

    override suspend fun refresh(): Result<Unit> = Result.success(Unit)

    override suspend fun avatar(userId: String): ByteArray? = null
}
