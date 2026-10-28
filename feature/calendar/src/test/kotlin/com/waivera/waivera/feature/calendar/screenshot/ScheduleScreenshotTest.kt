package com.waivera.waivera.feature.calendar.screenshot

import com.waivera.waivera.core.data.model.ScheduleFeed
import com.waivera.waivera.core.data.model.SyncStatus
import com.waivera.waivera.core.data.model.Synced
import com.waivera.waivera.core.demo.DemoData
import com.waivera.waivera.core.network.error.WaiveraError
import com.waivera.waivera.core.screenshottesting.ScreenshotSdk
import com.waivera.waivera.core.screenshottesting.captureScreenshots
import com.waivera.waivera.feature.calendar.ScheduleActions
import com.waivera.waivera.feature.calendar.ScheduleContent
import com.waivera.waivera.feature.calendar.model.MilestoneIndex
import com.waivera.waivera.feature.calendar.model.SchedulePeriods
import com.waivera.waivera.feature.calendar.model.ScheduleView
import com.waivera.waivera.feature.calendar.schedule.SchedulePeriod
import com.waivera.waivera.feature.calendar.schedule.ScheduleStateBuilder
import com.waivera.waivera.feature.calendar.schedule.ScheduleUiState
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The schedule tab on the demo data: Sunday 1 November 2026 in Chicago, the fall-back day, so
 * the overnight shift, the repeated hour and today's anniversary are all in frame.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotSdk.VALUE])
class ScheduleScreenshotTest {
    private val zone = DemoData.ZONE
    private val today: LocalDate = DemoData.NOW.atZone(zone).toLocalDate()
    private val milestones = MilestoneIndex(DemoData.milestones())

    private fun feed(view: ScheduleView, date: LocalDate) = ScheduleFeed(
        range = SchedulePeriods.rangeFor(view, date),
        zoneId = zone.id,
        events = DemoData.events(),
        timeOff = DemoData.timeOff(),
        holidays = DemoData.holidays(),
    )

    @Suppress("LongParameterList")
    private fun state(
        view: ScheduleView = ScheduleView.MONTH,
        date: LocalDate = today,
        synced: Synced<ScheduleFeed>? = Synced(feed(view, date), lastSyncedAt = DemoData.NOW),
        milestonesError: WaiveraError? = null,
        savedZoneId: String? = zone.id,
        deviceZone: ZoneId = zone,
        isOffline: Boolean = false,
    ): ScheduleUiState = ScheduleStateBuilder.build(
        // The demo clock, so today's agenda shows the "Now" divider and its ended items (UI4-12).
        period = SchedulePeriod(view = view, date = date, today = today, zone = zone, now = DemoData.NOW),
        feed = synced,
        milestones = milestones,
        milestonesError = milestonesError,
        savedZoneId = savedZoneId,
        deviceZone = deviceZone,
        isOffline = isOffline,
        isRefreshing = false,
    )

    private fun capture(name: String, state: ScheduleUiState) =
        captureScreenshots(name) { ScheduleContent(state = state, actions = ScheduleActions()) }

    @Test
    fun month() = capture("schedule_month", state())

    @Test
    fun week() = capture("schedule_week", state(view = ScheduleView.WEEK))

    @Test
    fun day() = capture("schedule_day", state(view = ScheduleView.DAY))

    @Test
    fun loading() = capture("schedule_loading", state(synced = null))

    @Test
    fun empty() {
        val date = LocalDate.of(2026, 11, 18)
        val range = SchedulePeriods.rangeFor(ScheduleView.DAY, date)
        capture(
            "schedule_empty",
            state(ScheduleView.DAY, date, Synced(ScheduleFeed(range, zone.id), lastSyncedAt = DemoData.NOW)),
        )
    }

    @Test
    fun error() {
        val range = SchedulePeriods.rangeFor(ScheduleView.MONTH, today)
        val failed = Synced(ScheduleFeed(range, zone.id), SyncStatus.Failed(WaiveraError.Server(SERVER_ERROR)))
        capture("schedule_error", state(synced = failed))
    }

    /** The first load failed while offline: one error banner above the grid, no offline banner. */
    @Test
    fun errorWhileOffline() {
        val range = SchedulePeriods.rangeFor(ScheduleView.MONTH, today)
        val failed = Synced(ScheduleFeed(range, zone.id), SyncStatus.Failed(WaiveraError.Network()))
        capture("schedule_errorOffline", state(synced = failed, isOffline = true))
    }

    /** Online, the refresh failed, the saved week is still shown under the banner. */
    @Test
    fun refreshFailed() {
        val stale = Synced(
            feed(ScheduleView.WEEK, today),
            SyncStatus.Failed(WaiveraError.Server(SERVER_ERROR)),
            lastSyncedAt = DemoData.NOW.minusSeconds(STALE_SECONDS),
        )
        capture("schedule_refreshFailed", state(view = ScheduleView.WEEK, synced = stale))
    }

    @Test
    fun offlineWithStaleData() {
        val stale = Synced(
            feed(ScheduleView.WEEK, today),
            SyncStatus.Failed(WaiveraError.Network()),
            lastSyncedAt = DemoData.NOW.minusSeconds(STALE_SECONDS),
        )
        capture(
            "schedule_offline",
            state(view = ScheduleView.WEEK, synced = stale, milestonesError = WaiveraError.Network(), isOffline = true),
        )
    }

    /**
     * Another day picked in a week whose events carry owner colours: today is the bold violet
     * number, the picked day the solid violet circle, and each day's dots take their owners' colours.
     */
    @Test
    fun weekWithOwnerColours() {
        val date = today.plusDays(1)
        val base = feed(ScheduleView.WEEK, date)
        val coloured = base.copy(
            events = base.events.mapIndexed { index, event ->
                event.copy(ownerColor = OWNER_COLOURS[index % OWNER_COLOURS.size])
            },
        )
        capture(
            "schedule_week_colours",
            state(view = ScheduleView.WEEK, date = date, synced = Synced(coloured, lastSyncedAt = DemoData.NOW)),
        )
    }

    /** The month with another day picked, so today's bold number and the picked circle show side by side. */
    @Test
    fun monthOtherDay() = capture("schedule_month_other_day", state(date = today.plusDays(4)))

    @Test
    fun zoneFallback() =
        capture("schedule_zoneFallback", state(savedZoneId = null, deviceZone = ZoneId.of("Asia/Tokyo")))

    private companion object {
        const val STALE_SECONDS = 3_600L
        const val SERVER_ERROR = 503

        /** Sample owner colours from the web calendar's palette, plus one event with none. */
        val OWNER_COLOURS = listOf("#D9468F", "#0E9F6E", null, "#E8590C")
    }
}
