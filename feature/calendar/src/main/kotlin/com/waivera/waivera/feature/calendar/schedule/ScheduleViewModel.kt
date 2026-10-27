package com.waivera.waivera.feature.calendar.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.waivera.waivera.core.data.model.DateRange
import com.waivera.waivera.core.data.model.Milestone
import com.waivera.waivera.core.data.model.RealtimeStatus
import com.waivera.waivera.core.data.model.ScheduleFeed
import com.waivera.waivera.core.data.model.Synced
import com.waivera.waivera.core.data.model.currentUser
import com.waivera.waivera.core.data.repository.CalendarRepository
import com.waivera.waivera.core.data.repository.RealtimeStatusRepository
import com.waivera.waivera.core.data.repository.SessionRepository
import com.waivera.waivera.core.network.time.WaiveraTime
import com.waivera.waivera.feature.calendar.data.DeviceZone
import com.waivera.waivera.feature.calendar.model.MilestoneIndex
import com.waivera.waivera.feature.calendar.model.SchedulePeriods
import com.waivera.waivera.feature.calendar.model.ScheduleView
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Schedule tab (07-calendar.md §1–3, §8). Every boundary — the fetched range, the agenda day,
 * the "today" marker and milestone matching — is computed in the employee's saved zone from
 * [CalendarRepository.zone], never the device zone.
 */
@HiltViewModel
@Suppress("LongParameterList")
class ScheduleViewModel @Inject constructor(
    private val calendar: CalendarRepository,
    private val session: SessionRepository,
    private val realtime: RealtimeStatusRepository,
    private val clock: Clock,
    private val deviceZone: DeviceZone,
) : ViewModel() {
    private val view = MutableStateFlow(ScheduleView.MONTH)

    /** The picked date; null follows "today" in the employee's zone. */
    private val selection = MutableStateFlow<LocalDate?>(null)
    private val now = MutableStateFlow(clock.instant())
    private val pullRefreshing = MutableStateFlow(false)

    private val initialZone: ZoneId = session.state.value.currentUser?.zone ?: WaiveraTime.FallbackZone
    private val zone: StateFlow<ZoneId> =
        calendar.zone.distinctUntilChanged().stateIn(viewModelScope, SharingStarted.Eagerly, initialZone)

    private val period: StateFlow<Period> = combine(view, selection, zone, now) { v, picked, z, instant ->
        val today = today(instant, z)
        val date = picked ?: today
        Period(v, date, today, z, SchedulePeriods.rangeFor(v, date))
    }.stateIn(viewModelScope, SharingStarted.Eagerly, initialPeriod())

    @OptIn(ExperimentalCoroutinesApi::class)
    private val feed: Flow<FeedSnapshot?> = period
        .map { it.range to it.zone }
        .distinctUntilChanged()
        .flatMapLatest { (range, zone) ->
            calendar.observeRange(range).map { FeedSnapshot(range, zone, it) }
        }

    private val milestones: Flow<MilestoneState> = calendar.observeMilestones()
        .distinctUntilChangedBy { it.data to it.error }
        .map { MilestoneState(MilestoneIndex(it.data), it) }

    private val chrome = combine(realtime.status, pullRefreshing, session.state) { status, refreshing, state ->
        Chrome(status, refreshing, state.currentUser?.timeZoneId)
    }

    val uiState: StateFlow<ScheduleUiState> = combine(period, feed, milestones, chrome) { p, f, m, c ->
        buildState(p, f, m, c)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = buildState(initialPeriod(), null, null, null),
    )

    init {
        viewModelScope.launch { refreshMilestonesIfStale() }
        viewModelScope.launch { refreshAfterReconnect() }
    }

    /** Switching views keeps the selected date. */
    fun selectView(newView: ScheduleView) {
        view.value = newView
    }

    fun selectDate(date: LocalDate) {
        selection.value = date
    }

    /** Previous (-1) or next (+1) period. */
    fun step(direction: Int) {
        val current = period.value
        selection.value = SchedulePeriods.step(current.view, current.date, direction)
    }

    /** "Today": the current date in the employee's saved zone. */
    fun goToToday() {
        now.value = clock.instant()
        selection.value = null
    }

    /** Pull to refresh or the Refresh action: feed and milestones together. */
    fun refresh() {
        if (pullRefreshing.value) return
        viewModelScope.launch {
            pullRefreshing.value = true
            try {
                refreshAll()
            } finally {
                pullRefreshing.value = false
            }
        }
    }

    fun retry() {
        viewModelScope.launch { refreshAll() }
    }

    fun retryMilestones() {
        viewModelScope.launch { calendar.refreshMilestones() }
    }

    /** The screen calls this when it resumes, so "today" moves on after midnight or a long pause. */
    fun onResume() {
        now.value = clock.instant()
    }

    private suspend fun refreshAll() = coroutineScope {
        launch { calendar.refresh(period.value.range) }
        launch { calendar.refreshMilestones() }
    }

    private suspend fun refreshMilestonesIfStale() {
        val current = calendar.observeMilestones().first()
        val last = current.lastSyncedAt
        if (last == null || Duration.between(last, clock.instant()) > MILESTONES_STALE) {
            calendar.refreshMilestones()
        }
    }

    /** Calendar has no realtime events (gaps.md G-07), so a reconnect or coming back online refetches. */
    private suspend fun refreshAfterReconnect() {
        var seenUp = false
        var lostSinceUp = false
        realtime.status.map { it.isOnline && it.isConnected }.distinctUntilChanged().collect { up ->
            if (up) {
                if (lostSinceUp) refreshAll()
                seenUp = true
                lostSinceUp = false
            } else if (seenUp) {
                lostSinceUp = true
            }
        }
    }

    private fun initialPeriod(): Period {
        val today = today(clock.instant(), initialZone)
        val range = SchedulePeriods.rangeFor(ScheduleView.MONTH, today)
        return Period(ScheduleView.MONTH, today, today, initialZone, range)
    }

    private fun buildState(p: Period, f: FeedSnapshot?, m: MilestoneState?, c: Chrome?): ScheduleUiState =
        ScheduleStateBuilder.build(
            period = SchedulePeriod(p.view, p.date, p.today, p.zone),
            feed = f?.takeIf { it.range == p.range && it.zone == p.zone }?.synced,
            milestones = m?.index ?: MilestoneIndex.Empty,
            milestonesError = m?.synced?.error,
            savedZoneId = c?.savedZoneId,
            deviceZone = c?.let { deviceZone.current() },
            isOffline = c?.status?.isOnline == false,
            isRefreshing = c?.pullRefreshing == true,
        )

    private data class Period(
        val view: ScheduleView,
        val date: LocalDate,
        val today: LocalDate,
        val zone: ZoneId,
        val range: DateRange,
    )

    private data class FeedSnapshot(val range: DateRange, val zone: ZoneId, val synced: Synced<ScheduleFeed>)

    private data class MilestoneState(val index: MilestoneIndex, val synced: Synced<List<Milestone>>)

    private data class Chrome(val status: RealtimeStatus, val pullRefreshing: Boolean, val savedZoneId: String?)

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
        val MILESTONES_STALE: Duration = Duration.ofMinutes(15)

        fun today(instant: Instant, zone: ZoneId): LocalDate = instant.atZone(zone).toLocalDate()
    }
}
