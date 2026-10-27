package com.waivera.waivera.core.data.live

import com.waivera.waivera.core.data.calendar.CalendarMath
import com.waivera.waivera.core.data.model.DateRange
import com.waivera.waivera.core.data.model.Milestone
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
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

    override fun observeRange(range: DateRange): Flow<Synced<ScheduleFeed>> {
        val key = feedKey(range)
        val empty = ScheduleFeed(range, currentZone().id)
        val data = cache.observeDocument(key, ScheduleFeed.serializer()).map { it ?: empty }
        return sync.synced(key, data).onStart {
            scope.launch {
                val last = sync.lastSyncedNow(key)
                if (last == null || Duration.between(last, clock.instant()) > FEED_STALE) refresh(range)
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
        cache.putDocument(feedKey(range), ScheduleFeed.serializer(), feed)
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

    companion object {
        private const val MILESTONES = "milestones"
        private val FEED_STALE: Duration = Duration.ofMinutes(1)
    }
}
