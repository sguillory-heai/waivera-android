package com.waivera.waivera.feature.today.screenshot

import androidx.compose.runtime.Composable
import com.waivera.waivera.core.data.calendar.CalendarMath
import com.waivera.waivera.core.data.model.ActivityFeed
import com.waivera.waivera.core.data.model.DateRange
import com.waivera.waivera.core.data.model.MyInvitation
import com.waivera.waivera.core.data.model.RealtimeStatus
import com.waivera.waivera.core.data.model.ScheduleFeed
import com.waivera.waivera.core.data.model.SyncStatus
import com.waivera.waivera.core.data.model.Synced
import com.waivera.waivera.core.demo.DemoData
import com.waivera.waivera.core.network.error.WaiveraError
import com.waivera.waivera.core.realtime.ConnectionState
import com.waivera.waivera.core.screenshottesting.ScreenshotHeights
import com.waivera.waivera.core.screenshottesting.ScreenshotSdk
import com.waivera.waivera.core.screenshottesting.captureScreenshots
import com.waivera.waivera.core.testing.EnglishConversationTitles
import com.waivera.waivera.feature.today.activity
import com.waivera.waivera.feature.today.conversation
import com.waivera.waivera.feature.today.model.TodayAssembler
import com.waivera.waivera.feature.today.model.TodayContext
import com.waivera.waivera.feature.today.model.TodayLocalState
import com.waivera.waivera.feature.today.model.TodaySources
import com.waivera.waivera.feature.today.model.TodayUiState
import com.waivera.waivera.feature.today.timedEvent
import com.waivera.waivera.feature.today.ui.TodayContent
import com.waivera.waivera.feature.today.ui.TodayPreviewStates
import com.waivera.waivera.feature.today.ui.TodaySkeleton
import java.time.Instant
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Today in every major state, across the shared matrix (light/dark × small/large phone × 100%/200%
 * font), named `today_<state>`. States are built from the demo data through the real assembler,
 * at the demo clock (1 November 2026, 10:30 in Chicago, the day DST ends).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotSdk.VALUE])
class TodayScreenshotTest {
    private val now: Instant = DemoData.NOW
    private val today = now.atZone(DemoData.ZONE).toLocalDate()
    private val range = DateRange(today, today.plusDays(14))
    private val connected = RealtimeStatus(ConnectionState.Connected, isOnline = true)

    private fun demoSources(synced: Instant? = now) = TodaySources(
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

    private fun state(
        sources: TodaySources = demoSources(),
        local: TodayLocalState = TodayLocalState(),
        realtime: RealtimeStatus = connected,
    ): TodayUiState = TodayAssembler.assemble(
        sources,
        TodayContext("Alex Rivera", DemoData.ME, DemoData.ZONE, now, realtime, local, EnglishConversationTitles),
    )

    private fun capture(name: String, heightDp: Int? = null, content: @Composable () -> Unit) =
        captureScreenshots("today_$name", heightDp = heightDp, content = content)

    private fun captureState(name: String, state: TodayUiState, heightDp: Int? = null) = capture(name, heightDp) {
        TodayContent(state = state, callbacks = TodayPreviewStates.callbacks())
    }

    @Test
    fun populated() = captureState("populated", state())

    @Test
    fun loading() = capture("loading") { TodaySkeleton() }

    @Test
    fun allCaughtUp() {
        val demo = demoSources()
        val calm = demo.copy(
            failedSends = emptySet(),
            conversations = demo.conversations.copy(data = demo.conversations.data.map { it.copy(unreadCount = 0) }),
            activity = demo.activity.copy(
                data = ActivityFeed(demo.activity.data.items.map { it.copy(read = true) }, hasMore = false),
            ),
            schedule = demo.schedule?.let { s ->
                s.copy(data = s.data.copy(events = s.data.events.filter { it.myInvitation != MyInvitation.PENDING }))
            },
        )
        captureState("all_caught_up", state(calm))
    }

    @Test
    fun error() {
        // A server failure: the error copy says Waivera is having trouble, not "you're offline".
        val failed = SyncStatus.Failed(WaiveraError.Server(WaiveraError.HTTP_SERVER_ERROR))
        captureState(
            "error",
            state(
                TodaySources(
                    conversations = Synced(emptyList(), failed),
                    activity = Synced(ActivityFeed(), failed),
                    schedule = Synced(ScheduleFeed(range, DemoData.ZONE.id), failed),
                    milestones = Synced(emptyList(), failed),
                ),
            ),
        )
    }

    @Test
    fun offlineAndStale() = captureState(
        "offline",
        state(
            sources = demoSources(synced = now.minusSeconds(12 * 60)),
            realtime = RealtimeStatus(ConnectionState.Reconnecting(3, 8_000), isOnline = false),
        ),
    )

    @Test
    fun reconnecting() = captureState(
        "reconnecting",
        state(realtime = RealtimeStatus(ConnectionState.Reconnecting(2, 4_000), isOnline = true)),
    )

    @Test
    fun sectionsFailed() {
        // Tall window: the failed Celebrations and Activity rows sit below the first screen.
        val failed = SyncStatus.Failed(WaiveraError.Timeout())
        val demo = demoSources()
        captureState(
            "sections_failed",
            state(
                sources = demo.copy(
                    schedule = Synced(ScheduleFeed(range, DemoData.ZONE.id), failed),
                    milestones = Synced(emptyList(), failed),
                    activity = demo.activity.copy(data = demo.activity.data.copy(hasMore = true)),
                ),
                local = TodayLocalState(loadMoreFailed = true),
            ),
            heightDp = ScreenshotHeights.TALL_DP,
        )
    }

    @Test
    fun activityFilteredEmpty() {
        val demo = demoSources()
        val allRead = demo.activity.copy(
            data = ActivityFeed(demo.activity.data.items.map { it.copy(read = true) }, hasMore = false),
        )
        captureState(
            "activity_filtered_empty",
            state(sources = demo.copy(activity = allRead), local = TodayLocalState(unreadOnly = true)),
            // The filtered-empty block is the last section: a tall window keeps it in the image.
            heightDp = ScreenshotHeights.TALL_DP,
        )
    }

    @Test
    fun longContent() {
        val longName = "Maria-Fernanda Castellanos-Whitaker de la Vega"
        val conversations = (1L..8L).map {
            conversation(
                id = it,
                unread = (it * 7).toInt(),
                otherName = longName,
                text = "Could you swap the Thursday overnight with me? I have a family thing and need to leave early.",
            )
        }
        val items = (1L..6L).map {
            activity(it, summary = "$longName replied in a thread: \"I added the medication and visit notes\"")
        }
        val events = listOf(
            timedEvent(
                id = "long",
                start = "2026-11-01T16:45:00Z",
                end = "2026-11-02T01:00:00Z",
                title = "Quarterly care plan review with the family, the case manager and the night team",
            ),
        )
        val demo = demoSources()
        captureState(
            "long_content",
            state(
                sources = demo.copy(
                    conversations = Synced(conversations, lastSyncedAt = now),
                    activity = Synced(ActivityFeed(items, hasMore = true), lastSyncedAt = now),
                    schedule = Synced(ScheduleFeed(range, DemoData.ZONE.id, events), lastSyncedAt = now),
                ),
                local = TodayLocalState(needsYouExpanded = true, isLoadingMore = true, autoPagingCapped = true),
            ),
        )
    }
}
