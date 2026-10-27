package com.waivera.waivera.feature.calendar.data

import com.waivera.waivera.core.data.calendar.CalendarMath
import com.waivera.waivera.core.data.model.DateRange
import com.waivera.waivera.core.data.model.ScheduleEvent
import com.waivera.waivera.core.data.repository.CalendarRepository
import com.waivera.waivera.core.network.error.WaiveraError
import java.time.Clock
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

/** Where an event lookup stands. */
sealed interface EventLookupResult {
    data object Loading : EventLookupResult

    /** [error] is set when the last refresh failed but a cached copy is shown. */
    data class Found(
        val event: ScheduleEvent,
        val range: DateRange,
        val zone: ZoneId,
        val isRefreshing: Boolean,
        val error: WaiveraError?,
    ) : EventLookupResult

    data class Failed(val error: WaiveraError) : EventLookupResult

    data object NotFound : EventLookupResult
}

/**
 * Finds one event by id through the feed API, because `CalendarRepository` has no single-event
 * call yet (docs/requests/calendar.md). An event seen on the schedule screen is watched in the
 * range it came from; a cold id (a push or deep link) is looked up in the month grids around
 * today in the employee's zone.
 */
class EventLookup @Inject constructor(
    private val calendar: CalendarRepository,
    private val store: ScheduleEventStore,
    private val clock: Clock,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observe(eventId: String): Flow<EventLookupResult> = calendar.zone.distinctUntilChanged().flatMapLatest { zone ->
        val ranges = rangesFor(eventId, zone)
        combine(ranges.map(calendar::observeRange)) { feeds ->
            val hit = feeds.withIndex().firstNotNullOfOrNull { (index, synced) ->
                synced.data.events.firstOrNull { it.id == eventId }?.let { index to it }
            }
            when {
                hit != null -> {
                    val synced = feeds[hit.first]
                    EventLookupResult.Found(hit.second, ranges[hit.first], zone, synced.isRefreshing, synced.error)
                }
                feeds.any { it.isInitialLoad } -> EventLookupResult.Loading
                feeds.all { it.error != null } -> EventLookupResult.Failed(requireNotNull(feeds.first().error))
                else -> EventLookupResult.NotFound
            }
        }
    }

    /** Refreshes every range the lookup watches. */
    suspend fun refresh(eventId: String, zone: ZoneId) = coroutineScope {
        rangesFor(eventId, zone).forEach { range -> launch { calendar.refresh(range) } }
    }

    fun rangesFor(eventId: String, zone: ZoneId): List<DateRange> {
        store.entry(eventId)?.let { return listOf(it.range) }
        val month = YearMonth.from(clock.instant().atZone(zone))
        return listOf(month, month.minusMonths(1), month.plusMonths(1)).map(CalendarMath::monthGrid)
    }
}
