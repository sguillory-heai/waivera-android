package com.waivera.waivera.feature.calendar.data

import com.waivera.waivera.core.data.model.DateRange
import com.waivera.waivera.core.data.model.ScheduleEvent
import com.waivera.waivera.core.data.model.ScheduleFeed
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Remembers which feed range each event was last seen in, so the event screen (which is routed
 * with an id only) can observe that one range instead of guessing. Bounded, in memory only;
 * the feeds themselves stay in the data layer's cache. Remove once `CalendarRepository`
 * offers `observeEvent(id)` (docs/requests/calendar.md).
 */
@Singleton
class ScheduleEventStore @Inject constructor() {
    data class Entry(val event: ScheduleEvent, val range: DateRange)

    private val entries = object : LinkedHashMap<String, Entry>(INITIAL_CAPACITY, LOAD_FACTOR, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>?): Boolean = size > MAX_ENTRIES
    }

    @Synchronized
    fun remember(feed: ScheduleFeed) {
        feed.events.forEach { entries[it.id] = Entry(it, feed.range) }
    }

    @Synchronized
    fun entry(eventId: String): Entry? = entries[eventId]

    private companion object {
        const val MAX_ENTRIES = 500
        const val INITIAL_CAPACITY = 64
        const val LOAD_FACTOR = 0.75f
    }
}
