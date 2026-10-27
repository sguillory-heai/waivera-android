package com.waivera.waivera.feature.calendar.schedule

import com.waivera.waivera.core.data.model.Holiday
import com.waivera.waivera.core.data.model.Milestone
import com.waivera.waivera.core.data.model.MyInvitation
import com.waivera.waivera.core.data.model.ScheduleEvent
import com.waivera.waivera.core.data.model.ScheduleFeed
import com.waivera.waivera.core.data.model.Synced
import com.waivera.waivera.core.data.model.TimeOffEntry
import com.waivera.waivera.core.network.dto.MilestoneType
import com.waivera.waivera.feature.calendar.model.MilestoneIndex
import com.waivera.waivera.feature.calendar.model.SchedulePeriods
import com.waivera.waivera.feature.calendar.model.ScheduleView
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Sample schedule for `@Preview`s only (screenshot tests build states from the demo data
 * instead). Sunday 1 November 2026 in Chicago: the fall-back day.
 */
@Suppress("MagicNumber")
internal object SchedulePreviewData {
    val zone: ZoneId = ZoneId.of("America/Chicago")
    val today: LocalDate = LocalDate.of(2026, 11, 1)

    private val events = listOf(
        ScheduleEvent(
            id = "preview_overnight",
            title = "Overnight respite shift",
            location = "Hughes residence",
            startAt = Instant.parse("2026-11-01T03:00:00Z"),
            endAt = Instant.parse("2026-11-01T12:00:00Z"),
            ownerName = "Alex Rivera",
            myInvitation = MyInvitation.ORGANIZER,
        ),
        ScheduleEvent(
            id = "preview_visit",
            title = "Client home visit",
            location = "1200 W Lake St",
            startAt = Instant.parse("2026-11-01T17:00:00Z"),
            endAt = Instant.parse("2026-11-01T18:00:00Z"),
            ownerName = "Priya Raman",
        ),
        ScheduleEvent(
            id = "preview_review",
            title = "Care plan review with the family and the regional coordinator",
            online = true,
            startAt = Instant.parse("2026-11-03T15:00:00Z"),
            endAt = Instant.parse("2026-11-03T16:00:00Z"),
            ownerName = "Priya Raman",
        ),
    )

    private val milestones = listOf(
        Milestone(MilestoneType.WORK_ANNIVERSARY, "usr_jordan", "Jordan Lee", month = 11, day = 1, startYear = 2021),
        Milestone(MilestoneType.BIRTHDAY, "usr_priya", "Priya Raman", month = 11, day = 2),
    )

    private val holidays = listOf(Holiday(9, "Thanksgiving", LocalDate.of(2026, 11, 26), LocalDate.of(2026, 11, 26)))

    private val timeOff = listOf(
        TimeOffEntry(
            id = 4,
            startAt = Instant.parse("2026-11-01T05:00:00Z"),
            endAt = Instant.parse("2026-11-01T17:00:00Z"),
            leaveType = "Personal leave",
            requestType = "HALF_DAY_AM",
            status = "APPROVED",
        ),
    )

    fun month(): ScheduleUiState = state(ScheduleView.MONTH)

    fun week(): ScheduleUiState = state(ScheduleView.WEEK)

    fun state(view: ScheduleView, date: LocalDate = today): ScheduleUiState {
        val range = SchedulePeriods.rangeFor(view, date)
        return ScheduleStateBuilder.build(
            period = SchedulePeriod(view, date, today, zone),
            feed = Synced(ScheduleFeed(range, zone.id, events, timeOff, holidays), lastSyncedAt = Instant.EPOCH),
            milestones = MilestoneIndex(milestones),
            milestonesError = null,
            savedZoneId = zone.id,
            deviceZone = zone,
            isOffline = false,
            isRefreshing = false,
        )
    }
}
