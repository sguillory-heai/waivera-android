package com.waivera.waivera.feature.calendar.event

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.waivera.waivera.core.data.model.DateRange
import com.waivera.waivera.core.data.model.MyInvitation
import com.waivera.waivera.core.data.model.currentUser
import com.waivera.waivera.core.data.model.currentUserId
import com.waivera.waivera.core.data.repository.CalendarRepository
import com.waivera.waivera.core.data.repository.ConversationRepository
import com.waivera.waivera.core.data.repository.PeopleRepository
import com.waivera.waivera.core.data.repository.SessionRepository
import com.waivera.waivera.core.network.di.ApplicationScope
import com.waivera.waivera.core.network.error.WaiveraError
import com.waivera.waivera.feature.calendar.data.DeviceZone
import com.waivera.waivera.feature.calendar.data.EventLookup
import com.waivera.waivera.feature.calendar.data.EventLookupResult
import com.waivera.waivera.feature.calendar.model.MilestoneIndex
import com.waivera.waivera.feature.calendar.model.WebLinks
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Event detail (07 §7). Accept and Decline are optimistic: the answer shows at once, an Undo
 * window runs before anything is sent, and a failed save rolls back with a visible Retry. A
 * pending answer is still sent if the screen goes away during the window.
 */
@HiltViewModel(assistedFactory = CalendarEventViewModel.Factory::class)
@Suppress("LongParameterList", "TooManyFunctions")
class CalendarEventViewModel @AssistedInject constructor(
    @Assisted private val eventId: String,
    private val lookup: EventLookup,
    private val calendar: CalendarRepository,
    private val conversations: ConversationRepository,
    private val people: PeopleRepository,
    private val session: SessionRepository,
    private val clock: Clock,
    private val deviceZone: DeviceZone,
    @ApplicationScope private val appScope: CoroutineScope,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(eventId: String): CalendarEventViewModel
    }

    private val now = MutableStateFlow(clock.instant())
    private val optimistic = MutableStateFlow<OptimisticResponse?>(null)
    private val failure = MutableStateFlow<ResponseFailure?>(null)
    private val joinBusy = MutableStateFlow(false)
    private val joinError = MutableStateFlow<JoinError?>(null)
    private val chatBusy = MutableStateFlow(false)
    private val effectChannel = Channel<EventEffect>(Channel.BUFFERED)
    private var commitJob: Job? = null
    private var undoToken = 0L
    private var lastFound: EventLookupResult.Found? = null

    val effects: Flow<EventEffect> = effectChannel.receiveAsFlow()

    private val milestones: Flow<MilestoneIndex> = calendar.observeMilestones()
        .distinctUntilChangedBy { it.data }
        .map { MilestoneIndex(it.data) }

    private val actions = combine(optimistic, failure, joinBusy, joinError, chatBusy) { o, f, jb, je, cb ->
        EventInteraction(o, f, jb, je, cb)
    }

    val uiState: StateFlow<EventUiState> =
        combine(lookup.observe(eventId), milestones, now, actions, session.state) { result, index, instant, a, s ->
            when (result) {
                EventLookupResult.Loading -> EventUiState.Loading
                EventLookupResult.NotFound -> EventUiState.NotFound
                is EventLookupResult.Failed -> EventUiState.Error(result.error)
                is EventLookupResult.Found -> {
                    lastFound = result
                    EventUiState.Loaded(
                        EventRules.details(
                            event = result.event,
                            zone = result.zone,
                            milestones = index,
                            now = instant,
                            myUserId = s.currentUserId,
                            savedZoneId = s.currentUser?.timeZoneId,
                            deviceZone = deviceZone.current(),
                            interaction = a,
                            isRefreshing = result.isRefreshing,
                            refreshError = result.error,
                        ),
                    )
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), EventUiState.Loading)

    /** Called by the screen every so often and on resume, so the join window and buttons stay current. */
    fun tick() {
        now.value = clock.instant()
    }

    fun retry() {
        viewModelScope.launch {
            val zone = lastFound?.zone ?: session.state.value.currentUser?.zone ?: return@launch
            lookup.refresh(eventId, zone)
        }
    }

    /** Accept (true) or Decline (false). Nothing is sent until the undo window closes. */
    fun respond(accept: Boolean) {
        val invitation = currentDetails()?.invitation ?: return
        if (!invitation.canChange || invitation.phase == ResponsePhase.SAVING) return
        if (invitation.response == if (accept) MyInvitation.ACCEPTED else MyInvitation.DECLINED) return
        commitJob?.cancel()
        failure.value = null
        optimistic.value = OptimisticResponse(accept, ResponsePhase.UNDO_WINDOW)
        undoToken += 1
        effectChannel.trySend(EventEffect.OfferUndo(accept, undoToken))
        commitJob = viewModelScope.launch {
            delay(UNDO_WINDOW_MILLIS)
            commit(accept)
        }
    }

    /** Undo tapped: the answer was never sent, so the server's answer simply shows again. */
    fun undo(token: Long) {
        if (token != undoToken || optimistic.value?.phase != ResponsePhase.UNDO_WINDOW) return
        commitJob?.cancel()
        commitJob = null
        optimistic.value = null
    }

    /** The undo snackbar went away without Undo: send now rather than waiting out the timer. */
    fun onUndoWindowClosed(token: Long) {
        if (token != undoToken) return
        val pending = optimistic.value?.takeIf { it.phase == ResponsePhase.UNDO_WINDOW } ?: return
        commitJob?.cancel()
        commitJob = viewModelScope.launch { commit(pending.accept) }
    }

    fun retryResponse() {
        val failed = failure.value ?: return
        failure.value = null
        commitJob?.cancel()
        commitJob = viewModelScope.launch { commit(failed.accept) }
    }

    fun join() {
        val details = currentDetails() ?: return
        val join = details.join ?: return
        if (join.isBusy || join.state != JoinState.OPEN) return
        joinError.value = null
        val event = details.event
        if (!event.isSprout) {
            val link = event.joinLink?.takeIf(WebLinks::isWebUrl)
            if (link == null) joinError.value = JoinError.NoLink else effectChannel.trySend(EventEffect.OpenUrl(link))
            return
        }
        joinBusy.value = true
        viewModelScope.launch {
            calendar.meetingLobbyUrl(event.id)
                .onSuccess { url ->
                    if (WebLinks.isWebUrl(url)) {
                        effectChannel.trySend(EventEffect.OpenUrl(url))
                    } else {
                        joinError.value = JoinError.Failed(null)
                    }
                }
                .onFailure { joinError.value = JoinError.Failed(WaiveraError.from(it)) }
            joinBusy.value = false
        }
    }

    fun dismissJoinError() {
        joinError.value = null
    }

    /** The screen could not hand the URL to a browser. */
    fun onLinkFailed() {
        joinError.value = JoinError.Failed(null)
    }

    fun openChat() {
        val details = currentDetails() ?: return
        val chat = details.chat ?: return
        if (chat.isBusy) return
        chatBusy.value = true
        viewModelScope.launch {
            val result = when (chat.kind) {
                ChatActionKind.MEETING_CHAT -> conversations.conversationForEvent(details.event.id)
                ChatActionKind.MESSAGE_ORGANIZER ->
                    details.event.ownerUserId
                        ?.let { conversations.openConversation(listOf(it)) }
                        ?: Result.failure(WaiveraError.NotFound())
            }
            result
                .onSuccess { effectChannel.trySend(EventEffect.OpenConversation(it)) }
                .onFailure { effectChannel.trySend(EventEffect.ChatFailed(WaiveraError.from(it))) }
            chatBusy.value = false
        }
    }

    suspend fun avatar(userId: String): ByteArray? = people.avatar(userId)

    override fun onCleared() {
        // An answer still inside its undo window is the person's decision: send it anyway.
        val pending = optimistic.value?.takeIf { it.phase == ResponsePhase.UNDO_WINDOW }
        if (pending != null) {
            appScope.launch { calendar.respondToInvitation(eventId, pending.accept) }
        }
        super.onCleared()
    }

    private suspend fun commit(accept: Boolean) {
        optimistic.value = OptimisticResponse(accept, ResponsePhase.SAVING)
        val range: DateRange? = lastFound?.range
        calendar.respondToInvitation(eventId, accept)
            .onSuccess {
                optimistic.value = OptimisticResponse(accept, ResponsePhase.SAVED)
                effectChannel.trySend(EventEffect.ResponseSaved(accept))
                // The feed is the source of truth; refetch so every screen shows the new answer.
                range?.let { calendar.refresh(it) }
            }
            .onFailure {
                optimistic.value = null
                failure.value = ResponseFailure(accept, WaiveraError.from(it))
                effectChannel.trySend(EventEffect.ResponseFailed)
            }
    }

    private fun currentDetails(): EventDetails? = (uiState.value as? EventUiState.Loaded)?.details

    companion object {
        /** Matches `SnackbarDuration.Long`, so the Undo action is offered for the whole window. */
        const val UNDO_WINDOW_MILLIS = 10_000L
        private const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
