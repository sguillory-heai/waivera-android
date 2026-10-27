package com.waivera.waivera.feature.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.waivera.waivera.core.data.model.ConversationTitleText
import com.waivera.waivera.core.data.model.DateRange
import com.waivera.waivera.core.data.model.ScheduleFeed
import com.waivera.waivera.core.data.model.SessionState
import com.waivera.waivera.core.data.model.Synced
import com.waivera.waivera.core.data.model.currentUser
import com.waivera.waivera.core.data.model.currentUserId
import com.waivera.waivera.core.data.repository.ActivityRepository
import com.waivera.waivera.core.data.repository.CalendarRepository
import com.waivera.waivera.core.data.repository.ConversationRepository
import com.waivera.waivera.core.data.repository.RealtimeStatusRepository
import com.waivera.waivera.core.data.repository.SessionRepository
import com.waivera.waivera.core.network.error.WaiveraError
import com.waivera.waivera.feature.today.model.ActivityFilter
import com.waivera.waivera.feature.today.model.TodayAssembler
import com.waivera.waivera.feature.today.model.TodayContext
import com.waivera.waivera.feature.today.model.TodayLocalState
import com.waivera.waivera.feature.today.model.TodaySources
import com.waivera.waivera.feature.today.model.TodayUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Duration
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Today: one prioritised list rendered straight from the local cache (Room in live mode), then
 * refreshed. Every write is optimistic and reversible on failure; the screen never blocks on
 * the network.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TodayViewModel @Inject constructor(
    session: SessionRepository,
    private val conversations: ConversationRepository,
    private val activity: ActivityRepository,
    private val calendar: CalendarRepository,
    private val realtimeStatus: RealtimeStatusRepository,
    private val nowSource: NowSource,
    private val titles: ConversationTitleText,
) : ViewModel() {
    private val local = MutableStateFlow(TodayLocalState())
    private val pager = ActivityPager(viewModelScope, activity, local)
    private val effectChannel = Channel<TodayEffect>(Channel.BUFFERED)

    /** Snackbar and haptic moments, delivered once. */
    val effects: Flow<TodayEffect> = effectChannel.receiveAsFlow()

    private val zone = calendar.zone.distinctUntilChanged()
    private val ticks = nowSource.ticks.shareIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), 1)

    /** Today and the next two weeks in the employee's zone; moves at local midnight, not device midnight. */
    private val range: Flow<DateRange> = combine(zone, ticks) { z, now -> now.atZone(z).toLocalDate() }
        .distinctUntilChanged()
        .map { today -> DateRange(today, today.plusDays(SCHEDULE_DAYS)) }

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
        combine(session.state, zone, ticks, realtimeStatus.status, local) { s, z, now, realtime, l ->
            TodayContext(accountName(s), s.currentUserId, z, now, realtime, l, titles)
        },
    ) { sources, context -> TodayAssembler.assemble(sources, context) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), TodayUiState())

    init {
        refreshStaleSources()
        pager.start()
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
            is TodayAction.RespondToInvitation -> respond(action.eventId, action.accept)
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
            effectChannel.send(if (result.isSuccess) TodayEffect.MarkedAllRead else TodayEffect.MarkAllReadFailed)
        }
    }

    private fun respond(eventId: String, accept: Boolean) {
        local.update { it.copy(answeredInvitations = it.answeredInvitations + eventId) }
        viewModelScope.launch {
            calendar.respondToInvitation(eventId, accept)
                .onSuccess { effectChannel.send(TodayEffect.InvitationAnswered(accept)) }
                .onFailure {
                    local.update { it.copy(answeredInvitations = it.answeredInvitations - eventId) }
                    effectChannel.send(TodayEffect.InvitationFailed(eventId, accept))
                }
        }
    }

    private suspend fun currentRange(): DateRange {
        val today = nowSource.now().atZone(zone.first()).toLocalDate()
        return DateRange(today, today.plusDays(SCHEDULE_DAYS))
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val SCHEDULE_DAYS = 14L
        val CHAT_FRESH_FOR: Duration = Duration.ofSeconds(60)
        val ACTIVITY_FRESH_FOR: Duration = Duration.ofSeconds(30)
        val MILESTONES_FRESH_FOR: Duration = Duration.ofMinutes(15)
    }
}

/** The name behind the top bar avatar: display name, else first and last, else email. */
private fun accountName(state: SessionState): String {
    val user = state.currentUser ?: return ""
    return user.displayName?.takeIf(String::isNotBlank)
        ?: listOfNotNull(user.firstName, user.lastName).joinToString(" ").trim().takeIf(String::isNotEmpty)
        ?: user.email.orEmpty()
}
