package com.waivera.waivera.core.data

import com.waivera.waivera.core.data.live.ApprovalMapping
import com.waivera.waivera.core.data.live.LiveRequestRepository
import com.waivera.waivera.core.data.live.RequestMapping
import com.waivera.waivera.core.data.model.ChangeRequest
import com.waivera.waivera.core.data.model.LeaveBalance
import com.waivera.waivera.core.data.model.ProfileField
import com.waivera.waivera.core.data.model.RequestKind
import com.waivera.waivera.core.data.model.RequestStatus
import com.waivera.waivera.core.data.model.TrainingBlock
import com.waivera.waivera.core.data.model.TrainingDecision
import com.waivera.waivera.core.network.WaiveraJson
import com.waivera.waivera.core.network.api.AuthenticationApi
import com.waivera.waivera.core.network.api.RequestApi
import com.waivera.waivera.core.network.dto.AdminRequestResponse
import com.waivera.waivera.core.network.dto.ParticipantChangeResponse
import com.waivera.waivera.core.network.dto.ParticipantProfileResponse
import com.waivera.waivera.core.network.dto.TimeOffDetailsResponse
import com.waivera.waivera.core.network.dto.TrainingCreditResponse
import com.waivera.waivera.core.testing.FakeForegroundMonitor
import com.waivera.waivera.core.testing.FixtureServer
import com.waivera.waivera.core.testing.TestSessionRepository
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest

/** Every request kind is decided through the route sprout_web uses for it, and read the way it reads it. */
class RequestRepositoryTest {
    private val server = FixtureServer()

    @AfterTest
    fun tearDown() = server.close()

    private fun TestScope.repository() = LiveRequestRepository(
        server.service<RequestApi>(),
        server.service<AuthenticationApi>(),
        TestSessionRepository(),
        WaiveraJson,
        // In the background nothing loads by itself, so only the calls under test reach the server.
        FakeForegroundMonitor(foreground = false),
        backgroundScope,
    )

    private fun request(id: Int, kind: RequestKind) = ChangeRequest(
        id = id,
        kind = kind,
        typeName = kind.name,
        status = RequestStatus.Pending,
        requesterId = "usr_maya",
        requesterName = "Maya Thompson",
        approverName = null,
        requestedAt = null,
        decidedAt = null,
        taskId = null,
        taskTitle = null,
        reason = null,
        explanation = null,
    )

    private fun body(path: String) = server.requestsTo(path).single().body?.utf8().orEmpty()

    @Test
    fun each_kind_goes_to_the_route_the_web_uses() = runTest {
        server.json("POST /api/TimeOff/approve-reject/1", """{"success":true,"statusCode":200}""")
        server.json("POST /api/TimeOff/approve-reject/2", """{"success":true,"statusCode":200}""")
        server.json("POST /api/admin/request/approve-reject/3", """{"success":true,"statusCode":200}""")
        server.json("PUT /api/admin/request/approve/4", """{"success":true}""")
        server.json("PUT /api/admin/request/reject/5", """{"success":true}""")
        server.json("POST /api/training/external/requests/6/decision", """{"success":true}""")
        val repository = repository()

        assertTrue(repository.decide(request(1, RequestKind.TimeOff), approve = true).isSuccess)
        assertTrue(repository.decide(request(2, RequestKind.TimeOffCancellation), approve = false).isSuccess)
        assertTrue(repository.decide(request(3, RequestKind.ProfileChange), approve = true).isSuccess)
        assertTrue(repository.decide(request(4, RequestKind.Reactivation), approve = true).isSuccess)
        assertTrue(repository.decide(request(5, RequestKind.AttachmentRemoval), approve = false).isSuccess)
        assertTrue(
            repository.decide(
                request(6, RequestKind.TrainingCredit),
                approve = false,
                training = TrainingDecision(revision = 3, reason = "  The date is unreadable. "),
            ).isSuccess,
        )

        assertEquals(
            """{"action":"approve","reason":"Approved via Admin Panel"}""",
            body("/api/TimeOff/approve-reject/1"),
        )
        assertEquals(
            """{"action":"reject","reason":"Rejected via Admin Panel"}""",
            body("/api/TimeOff/approve-reject/2"),
        )
        assertEquals(
            """{"action":"approve","reason":"Approved via Admin Panel"}""",
            body("/api/admin/request/approve-reject/3"),
        )
        assertEquals(1, server.requestsTo("/api/admin/request/approve/4").size)
        assertEquals(1, server.requestsTo("/api/admin/request/reject/5").size)
        assertEquals(
            """{"revision":3,"approve":false,"reason":"The date is unreadable."}""",
            body("/api/training/external/requests/6/decision"),
        )
    }

    @Test
    fun a_training_decision_without_its_revision_is_never_sent() = runTest {
        val result = repository().decide(request(6, RequestKind.TrainingCredit), approve = true)
        assertTrue(result.isFailure)
        assertTrue(server.requests.isEmpty())
    }

    @Test
    fun a_refused_decision_fails_even_inside_a_200() = runTest {
        server.json("POST /api/TimeOff/approve-reject/1", """{"success":false,"statusCode":400}""")
        assertTrue(repository().decide(request(1, RequestKind.TimeOff), approve = true).isFailure)
    }

    @Test
    fun a_reviewer_who_may_not_see_balances_gets_none() = runTest {
        server.status("GET /api/leave-management/employees/usr_maya/balances", 403, """{"success":false}""")
        assertNull(repository().leaveBalance("usr_maya", 7).getOrThrow())
    }

    @Test
    fun the_balance_for_the_requested_leave_type() = runTest {
        server.json(
            "GET /api/leave-management/employees/usr_maya/balances",
            """{"success":true,"data":{"balances":[
                {"id":6,"name":"Sick","tracksBalance":true,"availableMinutes":60},
                {"id":7,"name":"Vacation","tracksBalance":true,"availableMinutes":960}]}}""",
        )
        assertEquals(LeaveBalance(7, true, 960), repository().leaveBalance("usr_maya", 7).getOrThrow())
    }

    @Test
    fun the_leave_zone_is_read_from_settings_and_bad_zones_are_ignored() = runTest {
        server.json("GET /api/leave-management/settings", """{"success":true,"data":{"timeZone":"America/New_York"}}""")
        assertEquals(ZoneId.of("America/New_York"), repository().leaveZone())
        server.json(
            "GET /api/leave-management/settings",
            """{"success":true,"data":{"timeZone":"Central Standard Time"}}""",
        )
        assertNull(repository().leaveZone())
    }

    @Test
    fun full_days_are_utc_dates_and_partial_days_keep_their_instants() {
        val full = ApprovalMapping.timeOff(
            TimeOffDetailsResponse(
                timeOffStartDate = "2026-11-24T00:00:00",
                timeOffEndDate = "2026-11-26T00:00:00",
                typeOfLeave = " Vacation ",
                typeOfLeaveId = 7,
                requestType = "FULL_DAY",
                durationMinutes = 1440,
            ),
        )
        assertEquals(LocalDate.of(2026, 11, 24), full.startDate)
        assertEquals(LocalDate.of(2026, 11, 26), full.endDate)
        assertEquals("Vacation", full.leaveType)
        assertFalse(full.partialDay)
        assertNull(full.acknowledged)

        // 1 Nov 2026 is the day US clocks fall back; the instants carry the right hour on either side.
        val partial = ApprovalMapping.timeOff(
            TimeOffDetailsResponse(
                timeOffStartDate = "2026-11-01T06:30:00",
                timeOffEndDate = "2026-11-01T19:00:00Z",
                requestType = "PARTIAL_DAY",
                durationMinutes = 750,
            ),
        )
        assertTrue(partial.partialDay)
        assertEquals(Instant.parse("2026-11-01T06:30:00Z"), partial.start)
        assertEquals(Instant.parse("2026-11-01T19:00:00Z"), partial.end)
        val chicago = ZoneId.of("America/Chicago")
        assertEquals(1, partial.start?.atZone(chicago)?.hour)
        assertEquals(13, partial.end?.atZone(chicago)?.hour)
    }

    @Test
    fun the_task_review_prefers_the_current_one_and_says_when_it_changed() {
        val saved = """{"overdueCount":0,"dueDuringLeaveCount":1,"warnings":[]}"""
        val current = """{"overdueCount":1,"dueDuringLeaveCount":2,"warnings":[
            {"code":"SHORT_NOTICE_VACATION","message":"Short notice"},
            {"code":"SUPERVISION_GAP","message":"Hidden"},
            {"code":"FUTURE_TASK_REVIEW","message":"Hidden too"},
            {"code":"COVERAGE","message":"Coverage is thin that week."}]}"""
        val review = ApprovalMapping.timeOff(
            TimeOffDetailsResponse(
                intelligenceSnapshotJson = saved,
                currentIntelligenceJson = current,
                reviewRevision = 2,
                impactAcknowledgedRevision = 1,
                impactAcknowledgedAt = "2026-11-03T10:00:00Z",
            ),
        )
        val impact = review.impact
        assertEquals(1, impact?.dueBefore)
        assertEquals(2, impact?.dueDuring)
        assertEquals(true, impact?.shortNotice)
        assertEquals(listOf("Coverage is thin that week."), impact?.notices)
        assertTrue(review.impactChanged)
        // Acknowledged an older revision: not this review.
        assertEquals(false, review.acknowledged)
    }

    @Test
    fun profile_changes_list_changed_fields_and_never_carry_ssn_digits() {
        val fields = ApprovalMapping.profile(
            ParticipantChangeResponse(
                orignalProfile = ParticipantProfileResponse(
                    firstName = "Luis",
                    mobileNumber = "(337) 555-0142",
                    participantSsnLast4 = "1234",
                    waiverId = 0,
                    cpocEndDate = "2027-01-31T00:00:00",
                ),
                newProfileChanegs = ParticipantProfileResponse(
                    firstName = "Luis",
                    mobileNumber = "(337) 555-0199",
                    participantSsnLast4 = "9876",
                    waiverId = 0,
                    cpocEndDate = "2027-03-31T00:00:00",
                ),
            ),
        )
        assertEquals(
            listOf(
                ProfileField(ProfileField.Field.FirstName, "Luis", "Luis", changed = false),
                ProfileField(ProfileField.Field.Mobile, "(337) 555-0142", "(337) 555-0199", changed = true),
                ProfileField(ProfileField.Field.Ssn, "••••", "••••", changed = true),
                ProfileField(ProfileField.Field.CpocEnd, "2027-01-31", "2027-03-31", changed = true),
            ),
            fields,
        )
        assertFalse(fields.any { "1234" in it.previous.orEmpty() || "9876" in it.requested.orEmpty() })
    }

    @Test
    fun request_types_map_by_the_servers_names() {
        fun kind(name: String) = RequestMapping.request(AdminRequestResponse(id = 1, transactionType = name)).kind
        assertEquals(RequestKind.TimeOff, kind("Time-Off Request"))
        assertEquals(RequestKind.TimeOffCancellation, kind("Time-Off Cancellation"))
        assertEquals(RequestKind.ProfileChange, kind("Profile Change Request"))
        assertEquals(RequestKind.Reactivation, kind("Task Reactivation"))
        assertEquals(RequestKind.AttachmentRemoval, kind("Required Attachment Removal Request"))
        assertEquals(RequestKind.TrainingCredit, kind("External Training Credit"))
        assertEquals(RequestKind.Other, kind("Exception Period Request"))
    }

    @Test
    fun training_credit_blocks_are_worded_by_the_phone() {
        fun blocked(reason: String?) = ApprovalMapping.training(
            1,
            TrainingCreditResponse(status = "Pending", approvalBlockedReason = reason),
        ).blocked
        assertEquals(
            TrainingBlock.OwnCredit,
            blocked("Another authorized person must review your own training credit."),
        )
        assertEquals(TrainingBlock.NoReviewer, blocked("No eligible reviewer is configured. Ask an owner."))
        assertEquals(TrainingBlock.NotReviewer, blocked("Ask an authorized reviewer to approve this certificate."))
        assertNull(blocked(null))
        val standalone = ApprovalMapping.training(
            1,
            TrainingCreditResponse(status = "Pending", canApprove = true, revision = 2, standaloneCreditId = 9),
        )
        assertTrue(standalone.reviewOnWeb)
        assertFalse(standalone.decidable)
    }
}
