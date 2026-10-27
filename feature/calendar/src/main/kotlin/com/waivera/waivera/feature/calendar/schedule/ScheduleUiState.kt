package com.waivera.waivera.feature.calendar.schedule

import androidx.compose.runtime.Immutable
import com.waivera.waivera.core.network.error.WaiveraError
import com.waivera.waivera.feature.calendar.model.AgendaItem
import com.waivera.waivera.feature.calendar.model.DayMarker
import com.waivera.waivera.feature.calendar.model.PeriodTitle
import com.waivera.waivera.feature.calendar.model.ScheduleView
import com.waivera.waivera.feature.calendar.model.ZoneNotice
import java.time.LocalDate
import java.time.ZoneId

/** Everything the schedule screen draws. Dates are literal dates in [zone], the employee's saved zone. */
@Immutable
data class ScheduleUiState(
    val view: ScheduleView,
    val selectedDate: LocalDate,
    val today: LocalDate,
    val zone: ZoneId,
    val zoneNotice: ZoneNotice?,
    val title: PeriodTitle,
    val cells: List<GridCell>,
    val agenda: AgendaState,
    val isRefreshing: Boolean = false,
    /** A refresh failed while older data is on screen; shown as a banner with Retry. */
    val feedError: WaiveraError? = null,
    /** Milestones failed to load; the calendar still renders and a quiet banner says so. */
    val milestonesError: WaiveraError? = null,
    val isOffline: Boolean = false,
) {
    val isTodaySelected: Boolean get() = selectedDate == today

    /**
     * "Jump to today" is offered only when today is out of sight, so it never sits in the header
     * doing nothing on first open (UI2-07).
     */
    val showsJumpToToday: Boolean
        get() = when (view) {
            ScheduleView.DAY -> selectedDate != today
            ScheduleView.WEEK, ScheduleView.MONTH -> cells.none { it.inPeriod && it.isToday }
        }
}

/** One grid cell. [inPeriod] is false for month-grid spill days, which still show their dots. */
@Immutable
data class GridCell(
    val date: LocalDate,
    val inPeriod: Boolean,
    val isToday: Boolean,
    val isSelected: Boolean,
    val marker: DayMarker,
)

@Immutable
sealed interface AgendaState {
    data object Loading : AgendaState

    /** The first load failed and nothing is cached. */
    data class Error(val error: WaiveraError) : AgendaState

    data object Empty : AgendaState

    data class Content(val items: List<AgendaItem>) : AgendaState
}
