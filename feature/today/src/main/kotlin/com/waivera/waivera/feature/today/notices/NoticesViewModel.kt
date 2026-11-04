package com.waivera.waivera.feature.today.notices

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.waivera.waivera.core.data.repository.CalendarRepository
import com.waivera.waivera.core.data.repository.NoticeRepository
import com.waivera.waivera.core.data.repository.RealtimeStatusRepository
import com.waivera.waivera.core.designsystem.component.ConnectionBannerState
import com.waivera.waivera.core.network.error.WaiveraError
import com.waivera.waivera.feature.today.NowSource
import com.waivera.waivera.feature.today.model.TodayAssembler
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class NoticesPhase { Loading, Error, Empty, Content }

@Immutable
data class NoticesUiState(
    val filter: NoticeFilter = NoticeFilter.All,
    val groups: List<NoticeGroup> = emptyList(),
    /** The server's unread count, so it includes notifications older than the first page. */
    val unreadCount: Int = 0,
    val phase: NoticesPhase = NoticesPhase.Loading,
    /** A refresh failed while the last good list stays on screen. */
    val refreshFailed: Boolean = false,
    val isRefreshing: Boolean = false,
    val isMarkingAllRead: Boolean = false,
    val connection: ConnectionBannerState = ConnectionBannerState.Connected,
    /** Notifications loaded before the chip narrows them. */
    val totalCount: Int = 0,
    /** The failure's own client copy, so a server fault does not read as a connection problem. */
    @StringRes val errorMessage: Int? = null,
    /** Error type and HTTP status, never server text; shown only in debuggable builds. */
    val errorCode: String? = null,
) {
    /** Notifications exist, but none of them in the chosen chip. */
    val isFilteredEmpty: Boolean get() = phase == NoticesPhase.Empty && totalCount > 0
}

sealed interface NoticesEffect {
    data class Open(val destination: NoticeDestination) : NoticesEffect

    data object MarkAllReadFailed : NoticesEffect

    /** Nothing in the app or on the web app to open (no web address configured). */
    data object NothingToOpen : NoticesEffect
}

/**
 * The notifications inbox, the same list as the web bell. It refreshes on open; the realtime
 * sync adds new ones live and refetches after a reconnect, so the list stays current while open.
 */
@HiltViewModel
class NoticesViewModel @Inject constructor(
    private val notices: NoticeRepository,
    calendar: CalendarRepository,
    private val realtimeStatus: RealtimeStatusRepository,
    private val links: NoticeLinks,
    private val opener: NoticeOpener,
    nowSource: NowSource,
) : ViewModel() {
    private val local = MutableStateFlow(Local())
    private val effectChannel = Channel<NoticesEffect>(Channel.BUFFERED)
    val effects: Flow<NoticesEffect> = effectChannel.receiveAsFlow()

    val uiState: StateFlow<NoticesUiState> = combine(
        notices.observeNotices(),
        calendar.zone.distinctUntilChanged(),
        nowSource.ticks,
        realtimeStatus.status,
        local,
    ) { synced, zone, now, realtime, l ->
        val feed = synced.data
        val groups = NoticeRules.groups(feed.items, l.filter, links, now, zone)
        val phase = when {
            groups.isNotEmpty() -> NoticesPhase.Content
            synced.lastSyncedAt != null || feed.items.isNotEmpty() -> NoticesPhase.Empty
            synced.error != null -> NoticesPhase.Error
            else -> NoticesPhase.Loading
        }
        NoticesUiState(
            filter = l.filter,
            groups = groups,
            unreadCount = feed.unreadCount,
            phase = phase,
            refreshFailed = synced.error != null && phase != NoticesPhase.Error,
            isRefreshing = synced.isRefreshing,
            isMarkingAllRead = l.isMarkingAllRead,
            connection = if (phase == NoticesPhase.Error) {
                ConnectionBannerState.Connected
            } else {
                TodayAssembler.connectionBanner(realtime)
            },
            totalCount = feed.items.size,
            errorMessage = synced.error?.messageRes,
            errorCode = synced.error?.let(::diagnosticCode),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), NoticesUiState())

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch { notices.refresh() }
    }

    fun retryConnection() {
        realtimeStatus.retryNow()
        refresh()
    }

    fun setFilter(filter: NoticeFilter) = local.update { it.copy(filter = filter) }

    /** Marks the notification read at once and opens where it points. */
    fun open(id: String) {
        viewModelScope.launch {
            opener.open(id) { destination ->
                effectChannel.send(
                    if (destination ==
                        NoticeDestination.None
                    ) {
                        NoticesEffect.NothingToOpen
                    } else {
                        NoticesEffect.Open(destination)
                    },
                )
            }
        }
    }

    fun markAllRead() {
        if (local.value.isMarkingAllRead) return
        local.update { it.copy(isMarkingAllRead = true) }
        viewModelScope.launch {
            val result = notices.markAllRead()
            local.update { it.copy(isMarkingAllRead = false) }
            if (result.isFailure) effectChannel.send(NoticesEffect.MarkAllReadFailed)
        }
    }

    /** For example `NotFound 404` or `Unexpected SerializationException`. */
    private fun diagnosticCode(error: WaiveraError): String = listOfNotNull(
        error::class.simpleName,
        error.httpStatus?.toString(),
        error.cause?.let { it::class.simpleName },
    ).joinToString(" ")

    private data class Local(val filter: NoticeFilter = NoticeFilter.All, val isMarkingAllRead: Boolean = false)

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
