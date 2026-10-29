package com.waivera.waivera.feature.today

import com.waivera.waivera.core.data.model.ActivityFeed
import com.waivera.waivera.core.data.model.ActivityItem
import com.waivera.waivera.core.data.model.Conversation
import com.waivera.waivera.core.data.model.DateRange
import com.waivera.waivera.core.data.model.Milestone
import com.waivera.waivera.core.data.model.MyInvitation
import com.waivera.waivera.core.data.model.RealtimeStatus
import com.waivera.waivera.core.data.model.ScheduleEvent
import com.waivera.waivera.core.data.model.ScheduleFeed
import com.waivera.waivera.core.data.model.SyncStatus
import com.waivera.waivera.core.data.model.Synced
import com.waivera.waivera.core.data.repository.ActivityRepository
import com.waivera.waivera.core.data.repository.CalendarRepository
import com.waivera.waivera.core.data.repository.ConversationRepository
import com.waivera.waivera.core.data.repository.RealtimeStatusRepository
import com.waivera.waivera.core.network.error.WaiveraError
import com.waivera.waivera.core.realtime.ConnectionState
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** Fakes for Today's ViewModel tests. They mirror the live repositories' cache behaviour, not their I/O. */

val TEST_NOW: Instant = Instant.parse("2026-11-01T16:30:00Z")
val TEST_ZONE: ZoneId = ZoneId.of("America/Chicago")

class TestNowSource(now: Instant = TEST_NOW) : NowSource {
    val clock = MutableStateFlow(now)

    override fun now(): Instant = clock.value

    override val ticks: Flow<Instant> = clock
}

const val OPENED_CONVERSATION = 4242L

class FakeConversationRepository(initial: Synced<List<Conversation>> = Synced(emptyList(), lastSyncedAt = TEST_NOW)) :
    ConversationRepository {
    val state = MutableStateFlow(initial)
    var refreshResult: Result<Unit> = Result.success(Unit)
    var refreshes = 0
        private set

    /** What "Send wishes" gets back, and who it asked for. */
    var openResult: Result<Long> = Result.success(OPENED_CONVERSATION)
    val opened = mutableListOf<List<String>>()

    /** When set, opening a conversation waits for it, so a test can see the busy state. */
    var openGate: CompletableDeferred<Unit>? = null

    override fun observeConversations(): Flow<Synced<List<Conversation>>> = state

    override fun observeConversation(conversationId: Long): Flow<Conversation?> =
        state.map { s -> s.data.firstOrNull { it.id == conversationId } }

    override fun observeUnreadTotal(): Flow<Int> = state.map { s -> s.data.sumOf { it.unreadCount } }

    override suspend fun refresh(): Result<Unit> {
        refreshes++
        return refreshResult
    }

    override suspend fun refreshConversation(conversationId: Long): Result<Conversation> = unused()

    override suspend fun openConversation(participantIds: List<String>): Result<Long> {
        opened += participantIds
        openGate?.await()
        return openResult
    }

    override suspend fun conversationForEvent(calendarEventId: String): Result<Long> = unused()

    override suspend fun leave(conversationId: Long): Result<Unit> = unused()

    override suspend fun markRead(conversationId: Long, lastVisibleMessageId: Long): Result<Unit> = unused()

    override fun observeTyping(conversationId: Long): Flow<Set<String>> = emptyFlow()

    override fun observeAllTyping(): Flow<Map<Long, Set<String>>> = emptyFlow()

    override suspend fun callLobbyUrl(conversationId: Long): Result<String> = unused()

    override fun onComposerChanged(conversationId: Long, isEmpty: Boolean) = Unit
}

/**
 * Like `LiveActivityRepository`: mark-read writes the cache first and rolls it back on failure;
 * [pages] are what `loadMore` appends, in order.
 */
class FakeActivityRepository(
    items: List<ActivityItem> = emptyList(),
    hasMore: Boolean = false,
    lastSyncedAt: Instant? = TEST_NOW,
) : ActivityRepository {
    val state = MutableStateFlow(Synced(ActivityFeed(items, hasMore), lastSyncedAt = lastSyncedAt))
    val pages = ArrayDeque<List<ActivityItem>>()
    var refreshResult: Result<Unit> = Result.success(Unit)
    var markReadResult: Result<Unit> = Result.success(Unit)
    var markAllResult: Result<Unit> = Result.success(Unit)
    var refreshes = 0
        private set
    var loadMoreCalls = 0
        private set
    val markReadCalls = mutableListOf<Long>()
    var markAllCalls = 0
        private set

    override fun observeActivity(): Flow<Synced<ActivityFeed>> = state

    override suspend fun refresh(): Result<Unit> {
        refreshes++
        return refreshResult
    }

    override suspend fun loadMore(): Result<Boolean> {
        loadMoreCalls++
        val page = pages.removeFirstOrNull().orEmpty()
        val hasMore = page.size >= PAGE
        state.update { it.copy(data = ActivityFeed(it.data.items + page, hasMore)) }
        return Result.success(hasMore)
    }

    override suspend fun markRead(activityId: Long): Result<Unit> {
        markReadCalls += activityId
        return markOptimistically(markReadResult) { it.id == activityId }
    }

    override suspend fun markAllRead(): Result<Unit> {
        markAllCalls++
        return markOptimistically(markAllResult) { true }
    }

    /** A hub `ReceiveChatActivity`: the row lands in the cache and the flow re-emits. */
    fun receive(item: ActivityItem) =
        state.update { it.copy(data = it.data.copy(items = listOf(item) + it.data.items)) }

    /** Like the real repository: the cache flips at once and rolls back when the call fails. */
    private fun markOptimistically(result: Result<Unit>, predicate: (ActivityItem) -> Boolean): Result<Unit> {
        val before = state.value.data.items
        markLocally(predicate)
        if (result.isFailure) state.update { it.copy(data = it.data.copy(items = before)) }
        return result
    }

    private fun markLocally(predicate: (ActivityItem) -> Boolean) = state.update { s ->
        s.copy(data = s.data.copy(items = s.data.items.map { if (predicate(it)) it.copy(read = true) else it }))
    }

    companion object {
        const val PAGE = 30
    }
}

class FakeCalendarRepository(
    feed: (DateRange) -> ScheduleFeed = { ScheduleFeed(it, TEST_ZONE.id) },
    milestones: List<Milestone> = emptyList(),
) : CalendarRepository {
    val zoneState = MutableStateFlow(TEST_ZONE)
    override val zone: Flow<ZoneId> = zoneState
    val feedFor = MutableStateFlow(feed)
    var scheduleStatus: SyncStatus = SyncStatus.Idle
    var scheduleSyncedAt: Instant? = TEST_NOW
    val milestoneState = MutableStateFlow(Synced(milestones, lastSyncedAt = TEST_NOW))
    var respondResult: Result<Unit> = Result.success(Unit)
    val responses = mutableListOf<Pair<String, Boolean>>()
    val refreshedRanges = mutableListOf<DateRange>()
    val observedRanges = mutableListOf<DateRange>()

    /** When set, invitation answers wait for it, so tests can see the optimistic state. */
    var respondGate: CompletableDeferred<Unit>? = null
    var milestoneRefreshes = 0
        private set

    override fun observeRange(range: DateRange): Flow<Synced<ScheduleFeed>> {
        observedRanges += range
        return feedFor.map { Synced(it(range), scheduleStatus, scheduleSyncedAt) }
    }

    override suspend fun refresh(range: DateRange): Result<Unit> {
        refreshedRanges += range
        return Result.success(Unit)
    }

    override fun observeMilestones(): Flow<Synced<List<Milestone>>> = milestoneState

    override suspend fun refreshMilestones(): Result<Unit> {
        milestoneRefreshes++
        return Result.success(Unit)
    }

    override suspend fun respondToInvitation(eventId: String, accept: Boolean): Result<Unit> {
        responses += eventId to accept
        respondGate?.await()
        respondResult.onSuccess {
            feedFor.update { previous ->
                { range ->
                    val feed = previous(range)
                    feed.copy(
                        events = feed.events.map {
                            if (it.id == eventId) {
                                it.copy(myInvitation = if (accept) MyInvitation.ACCEPTED else MyInvitation.DECLINED)
                            } else {
                                it
                            }
                        },
                    )
                }
            }
        }
        return respondResult
    }

    override suspend fun meetingLobbyUrl(eventId: String): Result<String> = unused()

    override fun observeEvent(eventId: String): Flow<Synced<ScheduleEvent?>> = emptyFlow()

    override suspend fun refreshEvent(eventId: String): Result<Unit> = unused()
}

class FakeRealtimeStatusRepository(
    initial: RealtimeStatus = RealtimeStatus(ConnectionState.Connected, isOnline = true),
) : RealtimeStatusRepository {
    val mutableStatus = MutableStateFlow(initial)
    override val status: StateFlow<RealtimeStatus> = mutableStatus
    var retries = 0
        private set

    override fun retryNow() {
        retries++
    }

    override fun setVisibleConversation(conversationId: Long?, threadRootMessageId: Long?) = Unit

    override fun shouldSuppressNotification(conversationId: Long): Boolean = false
}

private fun <T> unused(): Result<T> = Result.failure(WaiveraError.NotFound())
