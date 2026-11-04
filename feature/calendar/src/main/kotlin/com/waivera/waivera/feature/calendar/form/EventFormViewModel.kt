package com.waivera.waivera.feature.calendar.form

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.waivera.waivera.core.data.calendar.EventPayload
import com.waivera.waivera.core.data.model.AttendeeOption
import com.waivera.waivera.core.data.model.EventDraft
import com.waivera.waivera.core.data.model.ScheduleEvent
import com.waivera.waivera.core.data.model.SyncStatus
import com.waivera.waivera.core.data.repository.CalendarRepository
import com.waivera.waivera.core.network.NetworkConfig
import com.waivera.waivera.core.network.dto.AttendeeType
import com.waivera.waivera.core.network.time.WaiveraTime
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * What the sheet opens for: a new event on [date], or an edit of [eventId]. [session] changes on
 * every open, so each open gets a fresh form instead of the last one's fields.
 */
data class EventFormRequest(val eventId: String? = null, val date: LocalDate? = null, val session: Int = 0)

enum class FormPhase { Loading, Ready, Unavailable }

@Immutable
data class EventFormUiState(
    val phase: FormPhase = FormPhase.Loading,
    val isEdit: Boolean = false,
    val title: String = "",
    val date: LocalDate = LocalDate.MIN,
    val start: LocalTime = LocalTime.of(DEFAULT_HOUR, 0),
    val end: LocalTime = LocalTime.of(DEFAULT_HOUR, DEFAULT_MINUTES),
    val allDay: Boolean = false,
    val waiveraVideo: Boolean = true,
    val location: String = "",
    val description: String = "",
    val zone: ZoneId = WaiveraTime.FallbackZone,
    val invitees: List<AttendeeOption> = emptyList(),
    /** Email the invited participants; only offered once a participant is invited. */
    val notifyParticipants: Boolean = false,
    val query: String = "",
    /** People matching [query] who are not invited yet; empty until the person types. */
    val matches: List<AttendeeOption> = emptyList(),
    val directoryFailed: Boolean = false,
    val showErrors: Boolean = false,
    val isSaving: Boolean = false,
    val saveFailed: Boolean = false,
) {
    val titleMissing: Boolean get() = title.isBlank()
    val endBeforeStart: Boolean get() = !allDay && !end.isAfter(start)
    val isValid: Boolean get() = !titleMissing && !endBeforeStart
    val hasParticipants: Boolean get() = invitees.any { it.participant }

    companion object {
        const val DEFAULT_HOUR = 9
        const val DEFAULT_MINUTES = 30
    }
}

sealed interface EventFormEffect {
    data class Saved(val created: Boolean) : EventFormEffect

    data class OpenWeb(val url: String) : EventFormEffect

    data object WebUnavailable : EventFormEffect
}

/**
 * The quick event form (create, or edit your own one-off event). Times are wall-clock values in
 * the employee's saved zone; the repository turns them into UTC instants when it sends them.
 */
@HiltViewModel(assistedFactory = EventFormViewModel.Factory::class)
@Suppress("TooManyFunctions") // One small handler per form field.
class EventFormViewModel @AssistedInject constructor(
    @Assisted private val request: EventFormRequest,
    private val calendar: CalendarRepository,
    private val config: NetworkConfig,
    private val clock: Clock,
) : ViewModel() {
    private val state = MutableStateFlow(EventFormUiState())
    val uiState: StateFlow<EventFormUiState> = state.asStateFlow()
    private val effectChannel = Channel<EventFormEffect>(Channel.BUFFERED)
    val effects: Flow<EventFormEffect> = effectChannel.receiveAsFlow()
    private var directory: List<AttendeeOption> = emptyList()
    private var editing: ScheduleEvent? = null

    init {
        viewModelScope.launch { start() }
    }

    private suspend fun start() {
        val zone = calendar.zone.first()
        val eventId = request.eventId
        if (eventId == null) startNew(zone) else startEdit(eventId, zone)
        loadDirectory()
    }

    private fun startNew(zone: ZoneId) {
        val now = clock.instant().atZone(zone)
        val date = request.date ?: now.toLocalDate()
        val start = if (date == now.toLocalDate() && now.hour < LAST_DEFAULT_HOUR) {
            nextHalfHour(now.toLocalTime())
        } else {
            LocalTime.of(EventFormUiState.DEFAULT_HOUR, 0)
        }
        state.update {
            it.copy(phase = FormPhase.Ready, zone = zone, date = date, start = start, end = start.plus(DEFAULT_LENGTH))
        }
    }

    private suspend fun startEdit(eventId: String, zone: ZoneId) {
        val event = calendar.observeEvent(eventId)
            .filter { it.lastSyncedAt != null || it.data != null || it.status is SyncStatus.Failed }
            .first()
            .data
        if (event == null) {
            state.update { it.copy(phase = FormPhase.Unavailable) }
            return
        }
        editing = event
        val startAt = event.startAt?.atZone(zone)
        val endAt = event.endAt?.atZone(zone)
        val invited = EventPayload.invitees(event).toSet()
        val participants = EventPayload.participants(event).toSet()
        state.update {
            it.copy(
                phase = FormPhase.Ready,
                isEdit = true,
                zone = zone,
                title = event.title.orEmpty(),
                date = event.allDayStart ?: startAt?.toLocalDate() ?: clock.instant().atZone(zone).toLocalDate(),
                start = startAt?.toLocalTime() ?: it.start,
                end = endAt?.toLocalTime() ?: it.end,
                allDay = event.isAllDay,
                waiveraVideo = !event.isSprout,
                location = if (event.isSprout) event.location.orEmpty() else "",
                description = event.description.orEmpty(),
                invitees = event.attendees
                    .mapNotNull { a ->
                        val participant = a.type == AttendeeType.WAIVER_PARTICIPANT && a.userId in participants
                        val coworker = a.type == AttendeeType.EMPLOYEE && a.userId in invited
                        if (!participant && !coworker) return@mapNotNull null
                        AttendeeOption(a.userId.orEmpty(), a.displayName ?: a.email.orEmpty(), a.email, participant)
                    }
                    .distinctBy { a -> a.key },
            )
        }
    }

    fun loadDirectory() {
        viewModelScope.launch {
            calendar.attendeeOptions(request.eventId)
                .onSuccess { people ->
                    directory = people
                    state.update { it.copy(directoryFailed = false).withMatches() }
                }
                .onFailure { state.update { it.copy(directoryFailed = true) } }
        }
    }

    fun setTitle(text: String) = edit { it.copy(title = text.take(MAX_TITLE)) }

    fun setAllDay(allDay: Boolean) = edit { it.copy(allDay = allDay) }

    fun setDate(date: LocalDate) = edit { it.copy(date = date) }

    /** Moving the start keeps the event's length, as calendars do. */
    fun setStart(time: LocalTime) = edit {
        val length = Duration.between(it.start, it.end).takeIf { d -> !d.isNegative && !d.isZero } ?: DEFAULT_LENGTH
        val end = time.plus(length).takeIf { e -> e.isAfter(time) } ?: LocalTime.MAX.withSecond(0).withNano(0)
        it.copy(start = time, end = end)
    }

    fun setEnd(time: LocalTime) = edit { it.copy(end = time) }

    fun setVideo(video: Boolean) = edit { it.copy(waiveraVideo = video) }

    fun setLocation(text: String) = edit { it.copy(location = text.take(MAX_LOCATION)) }

    fun setDescription(text: String) = edit { it.copy(description = text.take(MAX_DESCRIPTION)) }

    fun setQuery(text: String) = state.update { it.copy(query = text).withMatches() }

    fun invite(person: AttendeeOption) = edit {
        if (it.invitees.any { p -> p.key == person.key }) it else it.copy(invitees = it.invitees + person, query = "")
    }

    fun uninvite(person: AttendeeOption) = edit { it.copy(invitees = it.invitees.filter { p -> p.key != person.key }) }

    fun setNotifyParticipants(notify: Boolean) = edit { it.copy(notifyParticipants = notify) }

    fun save() {
        val current = state.value
        if (current.phase != FormPhase.Ready || current.isSaving) return
        if (!current.isValid) {
            state.update { it.copy(showErrors = true) }
            return
        }
        state.update { it.copy(isSaving = true, saveFailed = false) }
        val draft = EventDraft(
            title = current.title,
            date = current.date,
            start = current.start,
            end = current.end,
            allDay = current.allDay,
            inviteeIds = current.invitees.filter { !it.participant }.map(AttendeeOption::id),
            participantIds = current.invitees.filter { it.participant }.map(AttendeeOption::id),
            notifyParticipants = current.notifyParticipants && current.hasParticipants,
            waiveraVideo = current.waiveraVideo,
            location = current.location.takeIf { !current.waiveraVideo },
            description = current.description,
        )
        viewModelScope.launch {
            val event = editing
            val result = if (event == null) calendar.createEvent(draft) else calendar.updateEvent(event, draft)
            if (result.isSuccess) {
                state.update { it.copy(isSaving = false) }
                effectChannel.send(EventFormEffect.Saved(created = event == null))
            } else {
                state.update { it.copy(isSaving = false, saveFailed = true) }
            }
        }
    }

    /** Recurrence and linked tasks live in the web calendar. */
    fun openWeb() {
        val url = config.webBase?.resolve(WEB_CALENDAR_PATH)?.toString()
        viewModelScope.launch {
            effectChannel.send(url?.let(EventFormEffect::OpenWeb) ?: EventFormEffect.WebUnavailable)
        }
    }

    private fun edit(transform: (EventFormUiState) -> EventFormUiState) = state.update {
        if (it.isSaving) it else transform(it).copy(saveFailed = false).withMatches()
    }

    private fun EventFormUiState.withMatches(): EventFormUiState {
        val q = query.trim()
        if (q.isEmpty()) return copy(matches = emptyList())
        val invited = invitees.map(AttendeeOption::key).toSet()
        val found = directory.asSequence()
            .filter { it.key !in invited }
            .filter { it.name.contains(q, ignoreCase = true) || it.email?.contains(q, ignoreCase = true) == true }
            .take(MAX_MATCHES)
            .toList()
        return copy(matches = found)
    }

    private fun nextHalfHour(time: LocalTime): LocalTime {
        val minutes = time.hour * MINUTES_PER_HOUR + time.minute
        val next = (minutes / HALF_HOUR + 1) * HALF_HOUR
        return LocalTime.of(next / MINUTES_PER_HOUR, next % MINUTES_PER_HOUR)
    }

    @AssistedFactory
    interface Factory {
        fun create(request: EventFormRequest): EventFormViewModel
    }

    private companion object {
        const val WEB_CALENDAR_PATH = "/calendar"
        val DEFAULT_LENGTH: Duration = Duration.ofMinutes(30)
        const val LAST_DEFAULT_HOUR = 23
        const val MINUTES_PER_HOUR = 60
        const val HALF_HOUR = 30
        const val MAX_MATCHES = 6
        const val MAX_TITLE = 200
        const val MAX_LOCATION = 500
        const val MAX_DESCRIPTION = 4_000
    }
}
