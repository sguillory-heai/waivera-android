package com.waivera.waivera.feature.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.waivera.waivera.core.data.calendar.CalendarMath
import com.waivera.waivera.core.data.model.ConversationTitleText
import com.waivera.waivera.core.data.model.DateRange
import com.waivera.waivera.core.data.model.HandledObservation
import com.waivera.waivera.core.data.model.ScheduleFeed
import com.waivera.waivera.core.data.model.SessionState
import com.waivera.waivera.core.data.model.Synced
import com.waivera.waivera.core.data.model.currentUser
import com.waivera.waivera.core.data.model.currentUserId
import com.waivera.waivera.core.data.repository.ActivityRepository
import com.waivera.waivera.core.data.repository.CalendarRepository
import com.waivera.waivera.core.data.repository.ConversationRepository
import com.waivera.waivera.core.data.repository.HandledTodayRepository
import com.waivera.waivera.core.data.repository.RealtimeStatusRepository
import com.waivera.waivera.core.data.repository.SessionRepository
import com.waivera.waivera.core.network.di.ApplicationScope
import com.waivera.waivera.core.network.error.WaiveraError
import com.waivera.waivera.feature.today.model.ActivityFilter
import com.waivera.waivera.feature.today.model.GreetingRules
import com.waivera.waivera.feature.today.model.TodayAssembler
import com.waivera.waivera.feature.today.model.TodayContext
import com.waivera.waivera.feature.today.model.TodayLocalState
import com.waivera.waivera.feature.today.model.TodaySources
import com.waivera.waivera.feature.today.model.TodayUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Duration
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Today: one prioritised list rendered straight from the local cache (Room in live mode), then
 * refreshed. Every write is optimistic and reversible on failure; the screen never blocks on
 * the network. Invitation answers wait out an Undo window first ([InvitationAnswers]).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TodayViewModel @Inject constructor(
    session: SessionRepository,
    private val conversations: ConversationRepository,
    private val activity: ActivityRepository,
    private val calendar: CalendarRepository,
    private val realtimeStatus: RealtimeStatusRepository,
    private val handledToday: HandledTodayRepository,
    private val nowSource: NowSource,
    private val titles: ConversationTitleText,
    @ApplicationScope appScope: CoroutineScope,
) : ViewModel() {
    private val local = MutableStateFlow(TodayLocalState())
    private val pager = ActivityPager(viewModelScope, activity, local)
    private val effectChannel = Channel<TodayEffect>(Channel.BUFFERED)
    private val invitations = InvitationAnswers(viewModelScope, appScope, calendar, local, effectChannel)
    private var wishesJob: Job? = null

    /** Snackbar moments, delivered once. */
    val effects: Flow<TodayEffect> = effectChannel.receiveAsFlow()

    private val zone = calendar.zone.distinctUntilChanged()
    private val ticks = nowSource.ticks.shareIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), 1)

    /**
     * From this week's Sunday (for the "this week" stat) to two weeks out, in the employee's zone;
     * moves at local midnight, not device midnight.
     */
    private val range: Flow<DateRange> = combine(zone, ticks) { z, now -> now.atZone(z).toLocalDate() }
        .distinctUntilChanged()
        .map(::todayScheduleRange)

    /** The latest complete observation of Needs you, saved to the tally by one collector. */
    private val observations = MutableStateFlow<HandledObservation?>(null)

    private val schedule: Flow<Synced<ScheduleFeed>?> = range
        .flatMapLatest<DateRange, Synced<ScheduleFeed>?> { calendar.observeRange(it) }
        .onStart { emit(null) }

    val state: StateFlow<TodayUiState> = combine(
        combine(
            conversations.observeConversations(),
            activity.observeActivity(),
            schedule,
            calendar.observeMilestones(),
            conversations.observeFailedSends(),
        ) { chats, feed, events, milestones, failed -> TodaySources(chats, feed, events, milestones, failed) },
        combine(
            combine(session.state, zone, ticks, realtimeStatus.status, local) { s, z, now, realtime, l ->
                TodayContext(
                    accountName = accountName(s),
                    myUserId = s.currentUserId,
                    zone = z,
                    now = now,
                    realtime = realtime,
                    local = l,
                    titles = titles,
                    firstName = s.currentUser?.let { GreetingRules.firstName(it.firstName, it.displayName) },
                )
            },
            handledToday.tally,
        ) { context, tally -> context.copy(handled = tally) },
    ) { sources, context -> TodayAssembler.assemble(sources, context) }
        .onEach { observations.value = it.handledObservation }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), TodayUiState())

    init {
        refreshStaleSources()
        pager.start()
        recordHandled()
    }

    /**
     * Saves each new observation to the tally. It follows what the screen assembles rather than
     * the screen itself, so the state stays shared only while someone watches it.
     */
    private fun recordHandled() {
        viewModelScope.launch {
            observations.filterNotNull().distinctUntilChanged().collect { handledToday.record(it) }
        }
    }

    fun onAction(action: TodayAction) {
        when (action) {
            TodayAction.Refresh -> refreshAll()
            TodayAction.RetryConnection -> {
                realtimeStatus.retryNow()
                refreshAll()
            }
            is TodayAction.MarkActivityRead -> markRead(action.activityId)
            TodayAction.MarkAllActivityRead -> markAllRead()
            is TodayAction.SetActivityFilter -> local.update { it.copy(filter = action.filter) }
            TodayAction.ToggleUnreadOnly -> local.update { it.copy(unreadOnly = !it.unreadOnly) }
            TodayAction.ShowAllActivity -> local.update { it.copy(filter = ActivityFilter.ALL, unreadOnly = false) }
            TodayAction.LoadMoreActivity -> pager.loadMore()
            TodayAction.ToggleNeedsYouExpanded -> local.update { it.copy(needsYouExpanded = !it.needsYouExpanded) }
            is TodayAction.RespondToInvitation,
            is TodayAction.RetryInvitation,
            is TodayAction.UndoInvitation,
            is TodayAction.InvitationUndoClosed,
            -> onInvitationAction(action)
            is TodayAction.SendWishes -> sendWishes(action.userId)
        }
    }

    private fun onInvitationAction(action: TodayAction) {
        when (action) {
            is TodayAction.RespondToInvitation -> invitations.respond(action.eventId, action.accept)
            is TodayAction.RetryInvitation -> invitations.retry(action.eventId, action.accept)
            is TodayAction.UndoInvitation -> invitations.undo(action.eventId, action.token)
            is TodayAction.InvitationUndoClosed -> invitations.sendNow(action.eventId, action.token)
            else -> Unit
        }
    }

    /**
     * Opens (or starts) the direct conversation with someone celebrating today. The button shows
     * it is opening at once. A second tap while the first is still opening is ignored, so one tap
     * never opens two chats.
     */
    private fun sendWishes(userId: String) {
        if (wishesJob?.isActive == true) return
        local.update { it.copy(openingWishesFor = userId) }
        wishesJob = viewModelScope.launch {
            try {
                conversations.openConversation(listOf(userId))
                    .onSuccess { effectChannel.send(TodayEffect.OpenConversation(it)) }
                    .onFailure { effectChannel.send(TodayEffect.WishesFailed(userId)) }
            } finally {
                local.update { it.copy(openingWishesFor = null) }
            }
        }
    }

    /** On open, refresh only what is older than its freshness window (the calendar refreshes itself). */
    private fun refreshStaleSources() {
        refreshIfStale(conversations.observeConversations(), CHAT_FRESH_FOR) { conversations.refresh() }
        refreshIfStale(activity.observeActivity(), ACTIVITY_FRESH_FOR) { activity.refresh() }
        refreshIfStale(calendar.observeMilestones(), MILESTONES_FRESH_FOR) { calendar.refreshMilestones() }
    }

    private fun refreshIfStale(source: Flow<Synced<*>>, freshFor: Duration, refresh: suspend () -> Result<Unit>) {
        viewModelScope.launch {
            val last = source.first().lastSyncedAt
            if (last == null || Duration.between(last, nowSource.now()) >= freshFor) refresh()
        }
    }

    private fun refreshAll() {
        if (local.value.isRefreshing) return
        local.update { it.copy(isRefreshing = true) }
        viewModelScope.launch {
            val range = currentRange()
            val chats = async { conversations.refresh() }
            val feed = async { activity.refresh() }
            val others = listOf(async { calendar.refresh(range) }, async { calendar.refreshMilestones() })
            val results = listOf(chats.await(), feed.await()) + others.awaitAll()
            pager.reset()
            local.update { it.copy(isRefreshing = false, loadMoreFailed = false) }
            results.firstNotNullOfOrNull { it.exceptionOrNull() }?.let {
                effectChannel.send(TodayEffect.RefreshFailed(WaiveraError.from(it)))
            }
        }
    }

    private fun markRead(activityId: Long) {
        viewModelScope.launch {
            val item = activity.observeActivity().first().data.items.firstOrNull { it.id == activityId }
            if (item == null || item.read) return@launch
            // The repository marks the cached row read before calling the API and rolls it back on failure.
            activity.markRead(activityId).onFailure { effectChannel.send(TodayEffect.MarkReadFailed(activityId)) }
        }
    }

    private fun markAllRead() {
        if (local.value.isMarkingAllRead) return
        viewModelScope.launch {
            if (activity.observeActivity().first().data.items.all { it.read }) return@launch
            local.update { it.copy(isMarkingAllRead = true) }
            // The repository rolls the cached flags back itself when the call fails.
            val result = activity.markAllRead()
            local.update { it.copy(isMarkingAllRead = false) }
            // Success needs no message: the count and the action shrink away together.
            if (result.isFailure) effectChannel.send(TodayEffect.MarkAllReadFailed)
        }
    }

    override fun onCleared() {
        invitations.flush()
    }

    private suspend fun currentRange(): DateRange =
        todayScheduleRange(nowSource.now().atZone(zone.first()).toLocalDate())

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
        val CHAT_FRESH_FOR: Duration = Duration.ofSeconds(60)
        val ACTIVITY_FRESH_FOR: Duration = Duration.ofSeconds(30)
        val MILESTONES_FRESH_FOR: Duration = Duration.ofMinutes(15)
    }
}

/** From the Sunday that starts this week (for the week stat) through the next two weeks. */
private fun todayScheduleRange(today: LocalDate): DateRange =
    DateRange(start = CalendarMath.startOfWeek(today), endExclusive = today.plusDays(SCHEDULE_DAYS))

private const val SCHEDULE_DAYS = 14L

/** The name behind the top bar avatar: display name, else first and last, else email. */
private fun accountName(state: SessionState): String {
    val user = state.currentUser ?: return ""
    return user.displayName?.takeIf(String::isNotBlank)
        ?: listOfNotNull(user.firstName, user.lastName).joinToString(" ").trim().takeIf(String::isNotEmpty)
        ?: user.email.orEmpty()
}
