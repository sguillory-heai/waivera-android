package com.waivera.waivera.feature.profile

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.waivera.waivera.core.data.model.CurrentUser
import com.waivera.waivera.core.data.model.EmployeeFile
import com.waivera.waivera.core.data.model.EmployeeProfile
import com.waivera.waivera.core.data.model.Synced
import com.waivera.waivera.core.data.model.TimeOffSummary
import com.waivera.waivera.core.data.model.currentUser
import com.waivera.waivera.core.data.repository.CalendarRepository
import com.waivera.waivera.core.data.repository.ProfileRepository
import com.waivera.waivera.core.data.repository.RealtimeStatusRepository
import com.waivera.waivera.core.data.repository.SessionRepository
import com.waivera.waivera.core.designsystem.component.ConnectionBannerState
import com.waivera.waivera.core.network.error.WaiveraError
import com.waivera.waivera.core.network.time.WaiveraTime
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One independently loading part of a screen. */
@Immutable
internal sealed interface Section<out T> {
    data object Loading : Section<Nothing>

    data class Failed(val error: WaiveraError) : Section<Nothing>

    data class Ready<T>(val value: T) : Section<T>
}

@Immutable
internal sealed interface ProfileUiState {
    /** No backend session id: one clear state instead of a screen of dashes (spec 08 §1). */
    data object Unavailable : ProfileUiState

    data class Ready(
        val user: CurrentUser,
        val details: Section<EmployeeProfile>,
        /** A refresh failed while cached details are on screen: shown inline, the content stays. */
        val detailsRefreshError: WaiveraError?,
        val timeOff: Section<TimeOffSummary>,
        val files: Section<List<EmployeeFile>>,
        val organization: Section<OrganizationInfo?>,
        val celebration: Celebration?,
        val isRefreshing: Boolean,
        val connection: ConnectionBannerState,
    ) : ProfileUiState {
        /** The employee's saved zone: the profile's when set, else the session's (never the device's). */
        val zone: ZoneId
            get() = WaiveraTime.zoneOrNull((details as? Section.Ready)?.value?.timeZoneId) ?: user.zone
    }
}

/**
 * The signed-in person's profile. Four requests (details, time off, files, organization) load
 * independently so one slow or failing section never blocks the others; Refresh and Retry
 * re-issue all four and show a busy state until every one settles.
 */
@HiltViewModel
internal class ProfileViewModel @Inject constructor(
    private val session: SessionRepository,
    private val profiles: ProfileRepository,
    private val organizations: OrganizationSource,
    private val calendar: CalendarRepository,
    private val realtime: RealtimeStatusRepository,
    private val clock: Clock,
) : ViewModel() {
    private val timeOff = MutableStateFlow<Section<TimeOffSummary>>(Section.Loading)
    private val files = MutableStateFlow<Section<List<EmployeeFile>>>(Section.Loading)
    private val organization = MutableStateFlow<Section<OrganizationInfo?>>(Section.Loading)
    private val detailsError = MutableStateFlow<WaiveraError?>(null)
    private val refreshing = MutableStateFlow(false)
    private var refreshJob: Job? = null

    private val user = session.state.map { it.currentUser }.distinctUntilChanged()

    private val sections = combine(timeOff, files, organization) { t, f, o -> Triple(t, f, o) }

    private val celebration = combine(user, calendar.observeMilestones(), calendar.zone) { me, milestones, zone ->
        me?.let { Celebration.forUser(it.userId, milestones.data, LocalDate.now(clock.withZone(zone))) }
    }

    val uiState: StateFlow<ProfileUiState> = combine(
        combine(user, profiles.observeMyProfile(), detailsError) { me, details, error ->
            DetailsInputs(me, details, error)
        },
        sections,
        celebration,
        refreshing,
        realtime.status,
    ) { inputs, (t, f, o), party, busy, status ->
        val me = inputs.user ?: return@combine ProfileUiState.Unavailable
        val failure = inputs.error ?: inputs.details.error
        ProfileUiState.Ready(
            user = me,
            details = inputs.details.toSection(inputs.error),
            detailsRefreshError = failure.takeIf { inputs.details.data != null },
            timeOff = t,
            files = f,
            organization = o,
            celebration = party,
            isRefreshing = busy,
            connection = status.toBannerState(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), initialState())

    init {
        refresh()
        viewModelScope.launch { realtime.status.reconnections().collect { refresh() } }
        // Celebrations need milestones; Today and Schedule usually loaded them already.
        viewModelScope.launch {
            if (calendar.observeMilestones().first().lastSyncedAt == null) calendar.refreshMilestones()
        }
    }

    private fun initialState(): ProfileUiState = session.state.value.currentUser?.let { me ->
        ProfileUiState.Ready(
            user = me,
            details = Section.Loading,
            detailsRefreshError = null,
            timeOff = Section.Loading,
            files = Section.Loading,
            organization = Section.Loading,
            celebration = null,
            isRefreshing = true,
            connection = ConnectionBannerState.Connected,
        )
    } ?: ProfileUiState.Unavailable

    fun retryConnection() = realtime.retryNow()

    /** Re-issues all four requests; ignored while a refresh is already running. */
    fun refresh() {
        if (refreshJob?.isActive == true) return
        val me = session.state.value.currentUser ?: return
        refreshJob = viewModelScope.launch {
            refreshing.value = true
            detailsError.value = null
            timeOff.update { if (it is Section.Ready) it else Section.Loading }
            files.update { if (it is Section.Ready) it else Section.Loading }
            organization.update { if (it is Section.Ready) it else Section.Loading }
            try {
                coroutineScope {
                    listOf(
                        async { profiles.refresh().onFailure { detailsError.value = it.asWaivera() } },
                        async { timeOff.value = profiles.timeOff(me.userId).toSection(timeOff.value) },
                        async {
                            files.value = profiles.files(me.userId, FIRST_PAGE)
                                .map { it.items.take(VISIBLE_FILES) }
                                .toSection(files.value)
                        },
                        async { organization.value = organizations.current().toSection(organization.value) },
                    ).awaitAll()
                }
            } finally {
                refreshing.value = false
            }
        }
    }

    private data class DetailsInputs(
        val user: CurrentUser?,
        val details: Synced<EmployeeProfile?>,
        val error: WaiveraError?,
    )

    companion object {
        private const val STOP_TIMEOUT_MILLIS = 5_000L
        private const val FIRST_PAGE = 1

        /** The profile screen shows at most this many files (spec 08 §4). */
        const val VISIBLE_FILES = 5
    }
}

internal fun Throwable.asWaivera(): WaiveraError = this as? WaiveraError ?: WaiveraError.Unexpected()

/** A failed refresh keeps the last good value on screen. */
private fun <T> Result<T>.toSection(previous: Section<T>): Section<T> = fold(
    onSuccess = { Section.Ready(it) },
    onFailure = { error -> if (previous is Section.Ready) previous else Section.Failed(error.asWaivera()) },
)

private fun Synced<EmployeeProfile?>.toSection(refreshError: WaiveraError?): Section<EmployeeProfile> {
    val value = data
    val failure = error ?: refreshError
    return when {
        value != null -> Section.Ready(value)
        failure != null -> Section.Failed(failure)
        else -> Section.Loading
    }
}
