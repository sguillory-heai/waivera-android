package com.waivera.waivera.feature.profile

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.waivera.waivera.core.data.model.Person
import com.waivera.waivera.core.data.model.currentUserId
import com.waivera.waivera.core.data.repository.CalendarRepository
import com.waivera.waivera.core.data.repository.PeopleRepository
import com.waivera.waivera.core.data.repository.RealtimeStatusRepository
import com.waivera.waivera.core.data.repository.SessionRepository
import com.waivera.waivera.core.designsystem.component.ConnectionBannerState
import com.waivera.waivera.core.network.error.WaiveraError
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Immutable
internal data class PersonRow(val userId: String, val name: String, val detail: String?, val celebration: Celebration?)

/** Which block the list area shows. */
internal enum class ListPhase { Loading, Error, Empty, Content }

@Immutable
internal data class PeopleUiState(
    val query: String = "",
    val rows: List<PersonRow> = emptyList(),
    /** People in the directory before the search, so the empty state can say which case it is. */
    val totalCount: Int = 0,
    val phase: ListPhase = ListPhase.Loading,
    val error: WaiveraError? = null,
    val isRefreshing: Boolean = false,
    val connection: ConnectionBannerState = ConnectionBannerState.Connected,
)

/** Directory rules from spec 06 §C1, kept pure so they are unit tested. */
internal object PeopleFilter {
    /**
     * Drops entries without an id or a name, and the signed-in person (compared by backend id
     * only: PLAN.md identity rule), then matches [query] case-insensitively against name and email.
     */
    fun apply(people: List<Person>, myUserId: String?, query: String): List<Person> {
        val needle = query.trim().lowercase(Locale.ROOT)
        return people.asSequence()
            .filter { it.id.isNotBlank() && it.name.isNotBlank() }
            .filter { it.id != myUserId }
            .filter { needle.isEmpty() || searchText(it).contains(needle) }
            .distinctBy { it.id }
            .toList()
    }

    fun directorySize(people: List<Person>, myUserId: String?): Int = apply(people, myUserId, "").size

    private fun searchText(person: Person): String =
        listOfNotNull(person.name, person.email).joinToString(" ").lowercase(Locale.ROOT)
}

/** The staff directory. Reachable from the account menu; rows open the person's profile. */
@HiltViewModel
internal class PeopleViewModel @Inject constructor(
    private val people: PeopleRepository,
    session: SessionRepository,
    calendar: CalendarRepository,
    private val realtime: RealtimeStatusRepository,
    clock: Clock,
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val refreshing = MutableStateFlow(false)
    private val refreshError = MutableStateFlow<WaiveraError?>(null)
    private var refreshJob: Job? = null

    private val celebrations = combine(calendar.observeMilestones(), calendar.zone) { milestones, zone ->
        Celebration.index(milestones.data, LocalDate.now(clock.withZone(zone)))
    }

    private val directory = combine(people.observeDirectory(), session.state.map { it.currentUserId }) { synced, me ->
        synced to me
    }

    val uiState: StateFlow<PeopleUiState> = combine(
        directory,
        query,
        combine(refreshing, refreshError) { busy, error -> busy to error },
        celebrations,
        realtime.status,
    ) { (synced, me), text, (busy, failed), parties, status ->
        val matches = PeopleFilter.apply(synced.data, me, text)
        val total = PeopleFilter.directorySize(synced.data, me)
        val error = synced.error ?: failed
        PeopleUiState(
            query = text,
            rows = matches.map { PersonRow(it.id, it.name, it.email?.takeIf(String::isNotBlank), parties[it.id]) },
            totalCount = total,
            phase = when {
                total > 0 -> if (matches.isEmpty()) ListPhase.Empty else ListPhase.Content
                error != null -> ListPhase.Error
                synced.lastSyncedAt == null || busy -> ListPhase.Loading
                else -> ListPhase.Empty
            },
            error = error,
            isRefreshing = busy,
            connection = status.toBannerState(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), PeopleUiState())

    init {
        refresh()
        viewModelScope.launch { realtime.status.reconnections().collect { refresh() } }
        viewModelScope.launch {
            if (calendar.observeMilestones().first().lastSyncedAt == null) calendar.refreshMilestones()
        }
    }

    fun onQueryChange(value: String) {
        query.value = value
    }

    /** Header refresh and pull-to-refresh; a refresh already running is not restarted. */
    fun refresh() {
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            refreshing.value = true
            refreshError.value = people.refresh().exceptionOrNull()?.asWaivera()
            refreshing.value = false
        }
    }

    fun retryConnection() = realtime.retryNow()

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
