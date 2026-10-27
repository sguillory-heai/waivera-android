package com.waivera.waivera.feature.calendar.event

import com.waivera.waivera.core.data.model.Attendee
import com.waivera.waivera.core.data.model.Milestone
import com.waivera.waivera.core.data.model.MyInvitation
import com.waivera.waivera.core.data.model.ScheduleEvent
import com.waivera.waivera.core.network.dto.AttendeeType
import com.waivera.waivera.core.network.dto.InviteStatus
import com.waivera.waivera.core.network.dto.MilestoneType
import com.waivera.waivera.feature.calendar.model.MilestoneIndex
import java.time.Instant
import java.time.ZoneId

/** Sample event for `@Preview`s only; screenshot tests use the demo data. */
@Suppress("MagicNumber")
internal object EventPreviewData {
    private val zone: ZoneId = ZoneId.of("America/Chicago")
    private val now: Instant = Instant.parse("2026-11-01T16:30:00Z")
    private const val ME = "usr_me"

    private val event = ScheduleEvent(
        id = "preview_invite",
        title = "Care plan review",
        description = "Bring the updated goals and the last two weeks of notes.",
        online = true,
        meetingDetails = "https://meet.example.test/care-plan-review",
        startAt = Instant.parse("2026-11-02T15:00:00Z"),
        endAt = Instant.parse("2026-11-02T16:00:00Z"),
        ownerUserId = "usr_priya",
        ownerName = "Priya Raman",
        taskTitle = "Quarterly care plan",
        attendees = listOf(
            Attendee(
                "1",
                AttendeeType.EMPLOYEE,
                "usr_priya",
                displayName = "Priya Raman",
                inviteStatus = InviteStatus.ACCEPTED,
            ),
            Attendee("2", AttendeeType.EMPLOYEE, ME, displayName = "Alex Rivera", inviteStatus = InviteStatus.INVITED),
            Attendee("3", AttendeeType.WAIVER_PARTICIPANT, "p-17", displayName = "Morgan Hughes"),
            Attendee(
                "4",
                AttendeeType.EXTERNAL,
                email = "guardian@example.test",
                inviteStatus = InviteStatus.TENTATIVE,
            ),
        ),
        myInvitation = MyInvitation.PENDING,
    )

    private val milestones = MilestoneIndex(
        listOf(Milestone(MilestoneType.BIRTHDAY, "usr_priya", "Priya Raman", month = 11, day = 1)),
    )

    fun invited(): EventDetails = EventRules.details(
        event = event,
        zone = zone,
        milestones = milestones,
        now = now,
        myUserId = ME,
        savedZoneId = zone.id,
        deviceZone = zone,
    )
}
