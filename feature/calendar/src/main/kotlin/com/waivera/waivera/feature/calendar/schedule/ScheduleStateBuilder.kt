package com.waivera.waivera.feature.calendar.schedule

import com.waivera.waivera.core.data.model.ScheduleFeed
import com.waivera.waivera.core.data.model.Synced
import com.waivera.waivera.core.network.error.WaiveraError
import com.waivera.waivera.feature.calendar.model.Agenda
import com.waivera.waivera.feature.calendar.model.AgendaNowRules
import com.waivera.waivera.feature.calendar.model.MilestoneIndex
import com.waivera.waivera.feature.calendar.model.SchedulePeriods
import com.waivera.waivera.feature.calendar.model.ScheduleView
import com.waivera.waivera.feature.calendar.model.ZoneNotice
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * The view and dates the screen is on, all literal dates in [zone]. [now] places the "Now"
 * divider in today's agenda; null leaves it out.
 */
internal data class SchedulePeriod(
    val view: ScheduleView,
    val date: LocalDate,
    val today: LocalDate,
    val zone: ZoneId,
    val now: Instant? = null,
)

/** Pure mapping from repository values to [ScheduleUiState], shared by the ViewModel and previews. */
internal object ScheduleStateBuilder {
    /**
     * @param feed the feed for exactly this period's range and zone, or null while it is not known.
     * @param deviceZone null until the screen knows enough to decide on the zone notice.
     */
    @Suppress("LongParameterList")
    fun build(
        period: SchedulePeriod,
        feed: Synced<ScheduleFeed>?,
        milestones: MilestoneIndex,
        milestonesError: WaiveraError?,
        savedZoneId: String?,
        deviceZone: ZoneId?,
        isOffline: Boolean,
        isRefreshing: Boolean,
    ): ScheduleUiState {
        val range = SchedulePeriods.rangeFor(period.view, period.date)
        val feedData = feed?.data ?: ScheduleFeed(range, period.zone.id)
        val markers = Agenda.markers(
            feedData,
            period.zone,
            milestones,
            SchedulePeriods.cellsFor(period.view, period.date),
        )
        val month = YearMonth.from(period.date)
        val cells = markers.map { (date, marker) ->
            GridCell(
                date = date,
                inPeriod = period.view != ScheduleView.MONTH || YearMonth.from(date) == month,
                isToday = date == period.today,
                isSelected = date == period.date,
                marker = marker,
            )
        }
        val agenda = agendaState(feed, feedData, period, milestones)
        val agendaNow = (agenda as? AgendaState.Content)?.let { content ->
            period.now?.let { now -> AgendaNowRules.of(content.items, now, period.zone, period.date) }
        }
        return ScheduleUiState(
            view = period.view,
            selectedDate = period.date,
            today = period.today,
            zone = period.zone,
            zoneNotice = deviceZone?.let { ZoneNotice.of(period.zone, savedZoneId, it) },
            title = SchedulePeriods.title(period.view, period.date),
            cells = cells,
            agenda = agenda,
            agendaNow = agendaNow,
            isRefreshing = isRefreshing,
            feedError = feed?.error?.takeIf { agenda !is AgendaState.Error },
            milestonesError = milestonesError,
            isOffline = isOffline,
        )
    }

    private fun agendaState(
        synced: Synced<ScheduleFeed>?,
        feed: ScheduleFeed,
        period: SchedulePeriod,
        milestones: MilestoneIndex,
    ): AgendaState {
        val hasData = feed.events.isNotEmpty() || feed.timeOff.isNotEmpty() || feed.holidays.isNotEmpty()
        val error = synced?.error
        return when {
            synced == null -> AgendaState.Loading
            !hasData && error != null && synced.lastSyncedAt == null -> AgendaState.Error(error)
            !hasData && synced.isInitialLoad -> AgendaState.Loading
            else -> Agenda.forDay(feed, period.zone, milestones, period.date).let {
                if (it.isEmpty()) AgendaState.Empty else AgendaState.Content(it)
            }
        }
    }
}
