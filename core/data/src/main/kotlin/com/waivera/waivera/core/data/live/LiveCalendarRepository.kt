package com.waivera.waivera.core.data.live

import com.waivera.waivera.core.data.calendar.CalendarMath
import com.waivera.waivera.core.data.model.DateRange
import com.waivera.waivera.core.data.model.Milestone
import com.waivera.waivera.core.data.model.MyInvitation
import com.waivera.waivera.core.data.model.ScheduleEvent
import com.waivera.waivera.core.data.model.ScheduleFeed
import com.waivera.waivera.core.data.model.Synced
import com.waivera.waivera.core.data.model.currentUser
import com.waivera.waivera.core.data.model.currentUserId
import com.waivera.waivera.core.data.repository.CalendarRepository
import com.waivera.waivera.core.data.repository.SessionRepository
import com.waivera.waivera.core.network.NetworkConfig
import com.waivera.waivera.core.network.api.CalendarApi
import com.waivera.waivera.core.network.api.VideoApi
import com.waivera.waivera.core.network.di.ApplicationScope
import com.waivera.waivera.core.network.dto.CreateMeetingRoomRequest
import com.waivera.waivera.core.network.dto.InternalInvitationResponseReq
import com.waivera.waivera.core.network.dto.InviteStatus
import com.waivera.waivera.core.network.error.WaiveraError
import com.waivera.waivera.core.network.error.apiCall
import com.waivera.waivera.core.network.error.catchingWaivera
import com.waivera.waivera.core.network.error.requireData
import com.waivera.waivera.core.network.time.WaiveraTime
import java.time.Clock
import java.time.Duration
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

/**
 * Schedule feed (07-calendar.md). Ranges are whole days in the employee's saved zone (dates.md
 * §2, fallback America/Chicago, never the device zone), and month views fetch the full 42-cell
 * grid so spill days show their events. Each range is cached separately.
 */
@Singleton
@Suppress("LongParameterList")
class LiveCalendarRepository @Inject constructor(
    private val calendarApi: CalendarApi,
    private val videoApi: VideoApi,
    private val cache: ChatCache,
    private val sync: SyncTracker,
    private val session: SessionRepository,
    private val config: NetworkConfig,
    private val clock: Clock,
    @ApplicationScope private val scope: CoroutineScope,
) : CalendarRepository {
    override val zone: Flow<ZoneId> = session.state.map {
        currentZone(it.currentUser?.timeZoneId)
    }.distinctUntilChanged()

    private fun currentZone(raw: String? = session.state.value.currentUser?.timeZoneId): ZoneId =
        WaiveraTime.employeeZone(raw)

    /** Ranges with a collector right now, refetched after a reconnect (see [RealtimeSync.resync]). */
    private val openRanges = MutableStateFlow<Map<DateRange, Int>>(emptyMap())

    /** Feed documents written by this process, patched when an invitation answer is saved. */
    private val writtenFeeds = MutableStateFlow<Set<String>>(emptySet())

    val activeRanges: Set<DateRange> get() = openRanges.value.keys

    override fun observeRange(range: DateRange): Flow<Synced<ScheduleFeed>> = flow {
        openRanges.update { it + (range to (it[range] ?: 0) + 1) }
        try {
            val key = feedKey(range)
            val empty = ScheduleFeed(range, currentZone().id)
            val data = cache.observeDocument(key, ScheduleFeed.serializer()).map { it ?: empty }
            emitAll(
                sync.synced(key, data).onStart {
                    scope.launch {
                        val last = sync.lastSyncedNow(key)
                        if (last == null || Duration.between(last, clock.instant()) > FEED_STALE) refresh(range)
                    }
                },
            )
        } finally {
            openRanges.update { current ->
                val count = (current[range] ?: 1) - 1
                if (count <= 0) current - range else current + (range to count)
            }
        }
    }

    override suspend fun refresh(range: DateRange): Result<Unit> = sync.track(feedKey(range)) {
        val zone = currentZone()
        val (start, end) = CalendarMath.window(range, zone)
        val dto = apiCall {
            calendarApi.getAllEvents(
                startTime = WaiveraTime.formatQueryInstant(start),
                endTime = WaiveraTime.formatQueryInstant(end),
                calendarStartDate = range.start.toString(),
                calendarEndDateExclusive = range.endExclusive.toString(),
            )
        }.requireData()
        val feed = CalendarMath.feed(dto, range, zone, session.state.value.currentUserId)
        val key = feedKey(range)
        cache.putDocument(key, ScheduleFeed.serializer(), feed)
        writtenFeeds.update { it + key }
    }

    /**
     * One event by id, for screens opened from a push or link. The cached copy shows at once;
     * a cold or stale copy refreshes on first collection. `data == null` with a sync time means
     * the server answered 404 (deleted, or in another organization).
     */
    override fun observeEvent(eventId: String): Flow<Synced<ScheduleEvent?>> {
        val key = eventKey(eventId)
        val data = cache.observeDocument(key, CachedEvent.serializer()).map { it?.event }
        return sync.synced(key, data).onStart {
            scope.launch {
                val last = sync.lastSyncedNow(key)
                if (last == null || Duration.between(last, clock.instant()) > FEED_STALE) refreshEvent(eventId)
            }
        }
    }

    override suspend fun refreshEvent(eventId: String): Result<Unit> = sync.track(eventKey(eventId)) {
        val event = try {
            apiCall { calendarApi.getEventById(eventId) }.requireData()
                .let { CalendarMath.event(it, session.state.value.currentUserId) }
        } catch (_: WaiveraError.NotFound) {
            null
        }
        cache.putDocument(eventKey(eventId), CachedEvent.serializer(), CachedEvent(event))
    }

    override fun observeMilestones(): Flow<Synced<List<Milestone>>> {
        val data = cache.observeDocument(MILESTONES, ListSerializer(Milestone.serializer())).map { it.orEmpty() }
        return sync.synced(MILESTONES, data)
    }

    override suspend fun refreshMilestones(): Result<Unit> = sync.track(MILESTONES) {
        val list = apiCall { calendarApi.getMilestones() }.requireData().mapNotNull(CalendarMath::milestone)
        cache.putDocument(MILESTONES, ListSerializer(Milestone.serializer()), list)
    }

    override suspend fun respondToInvitation(eventId: String, accept: Boolean): Result<Unit> = catchingWaivera {
        val userId = session.state.value.currentUserId ?: throw WaiveraError.NotSignedIn()
        val ok = apiCall {
            calendarApi.respondToInvitation(
                InternalInvitationResponseReq(calendarEventId = eventId, isInvitationAccept = accept, userId = userId),
            )
        }.data
        if (ok != true) throw WaiveraError.Unexpected()
        applyInvitation(eventId, if (accept) MyInvitation.ACCEPTED else MyInvitation.DECLINED, userId)
        // The server stays authoritative: settle the single-event copy with its answer.
        scope.launch { refreshEvent(eventId) }
    }

    /** The saved answer, written into every cached copy of the event so all screens agree at once. */
    private suspend fun applyInvitation(eventId: String, answer: MyInvitation, userId: String) {
        val status = if (answer == MyInvitation.ACCEPTED) InviteStatus.ACCEPTED else InviteStatus.DECLINED
        val now = clock.instant()
        fun ScheduleEvent.answered(): ScheduleEvent = copy(
            myInvitation = answer,
            attendees = attendees.map {
                if (it.userId == userId) it.copy(inviteStatus = status, respondedAt = now) else it
            },
        )
        cache.document(eventKey(eventId), CachedEvent.serializer())?.event?.let { event ->
            cache.putDocument(eventKey(eventId), CachedEvent.serializer(), CachedEvent(event.answered()))
        }
        writtenFeeds.value.forEach { key ->
            val feed = cache.document(key, ScheduleFeed.serializer()) ?: return@forEach
            if (feed.events.none { it.id == eventId }) return@forEach
            val events = feed.events.map { if (it.id == eventId) it.answered() else it }
            cache.putDocument(key, ScheduleFeed.serializer(), feed.copy(events = events))
        }
    }

    override suspend fun meetingLobbyUrl(eventId: String): Result<String> = catchingWaivera {
        val web = config.webBase ?: throw WaiveraError.Configuration(listOf("waivera.webBaseUrl"))
        val meeting = apiCall {
            videoApi.createMeetingRoom(CreateMeetingRoomRequest(calendarEventId = eventId))
        }.requireData()
        val roomId = meeting.roomId ?: throw WaiveraError.Unexpected()
        MeetingHandoff.lobbyUrl(
            web,
            roomId,
            MeetingHandoff.State(calenderEventId = eventId, isPublisher = meeting.isPublisher, isOneToOneCall = false),
        )
    }

    private fun feedKey(range: DateRange) = "calendar:${currentZone().id}:${range.key}"

    private fun eventKey(eventId: String) = "calendar:event:$eventId"

    /** Wraps the event so "looked up and not found" (null) is stored, not just absent. */
    @Serializable
    private data class CachedEvent(val event: ScheduleEvent? = null)

    companion object {
        private const val MILESTONES = "milestones"
        private val FEED_STALE: Duration = Duration.ofMinutes(1)
    }
}
