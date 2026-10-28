package com.waivera.waivera.feature.today.screenshot

import com.waivera.waivera.core.data.calendar.CalendarMath
import com.waivera.waivera.core.data.model.ActivityFeed
import com.waivera.waivera.core.data.model.DateRange
import com.waivera.waivera.core.data.model.HandledTally
import com.waivera.waivera.core.data.model.RealtimeStatus
import com.waivera.waivera.core.data.model.ScheduleFeed
import com.waivera.waivera.core.data.model.Synced
import com.waivera.waivera.core.demo.DemoData
import com.waivera.waivera.core.realtime.ConnectionState
import com.waivera.waivera.core.testing.EnglishConversationTitles
import com.waivera.waivera.feature.today.model.TodayAssembler
import com.waivera.waivera.feature.today.model.TodayContext
import com.waivera.waivera.feature.today.model.TodayLocalState
import com.waivera.waivera.feature.today.model.TodaySources
import com.waivera.waivera.feature.today.model.TodayUiState
import java.time.Instant
import java.time.LocalDate

/**
 * Today built from the demo data through the real assembler, at the demo clock (1 November 2026,
 * 10:30 in Chicago, the day DST ends). The screenshots and the layout checks share it, so a
 * check measures the same screen the capture shows.
 */
internal object TodayDemo {
    val now: Instant = DemoData.NOW
    val today: LocalDate = now.atZone(DemoData.ZONE).toLocalDate()
    val range = DateRange(today, today.plusDays(14))
    val connected = RealtimeStatus(ConnectionState.Connected, isOnline = true)

    fun sources(synced: Instant? = now) = TodaySources(
        conversations = Synced(DemoData.conversations(), lastSyncedAt = synced),
        activity = Synced(ActivityFeed(DemoData.activity(), hasMore = false), lastSyncedAt = synced),
        schedule = Synced(
            ScheduleFeed(
                range = range,
                zoneId = DemoData.ZONE.id,
                events = DemoData.events().filter { e ->
                    range.days().any { CalendarMath.occursOn(e, it, DemoData.ZONE) }
                },
            ),
            lastSyncedAt = synced,
        ),
        milestones = Synced(DemoData.milestones(), lastSyncedAt = synced),
        // The demo DM holds a message that wasn't sent, as the demo app does.
        failedSends = setOf(DemoData.DM_PRIYA),
    )

    /** Five things already handled this morning, so the ring reads as it does mid-day. */
    val morningTally = HandledTally(userId = DemoData.ME, day = today, handled = 5, open = emptySet())

    fun state(
        sources: TodaySources = sources(),
        local: TodayLocalState = TodayLocalState(),
        realtime: RealtimeStatus = connected,
        handled: HandledTally? = morningTally,
    ): TodayUiState = TodayAssembler.assemble(
        sources,
        TodayContext(
            accountName = "Alex Rivera",
            myUserId = DemoData.ME,
            zone = DemoData.ZONE,
            now = now,
            realtime = realtime,
            local = local,
            titles = EnglishConversationTitles,
            firstName = "Alex",
            handled = handled,
        ),
    )
}
