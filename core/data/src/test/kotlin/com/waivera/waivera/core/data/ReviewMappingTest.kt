package com.waivera.waivera.core.data

import com.waivera.waivera.core.data.live.ReviewMapping
import com.waivera.waivera.core.data.model.ApprovalMode
import com.waivera.waivera.core.data.model.ApprovalOutcome
import com.waivera.waivera.core.data.model.ApprovalSend
import com.waivera.waivera.core.data.model.FieldType
import com.waivera.waivera.core.data.model.StepStatus
import com.waivera.waivera.core.network.WaiveraJson
import com.waivera.waivera.core.network.dto.ApprovalErrorResponse
import com.waivera.waivera.core.network.dto.ApprovalRequirementsResponse
import com.waivera.waivera.core.network.dto.ApproveTaskStageBody
import com.waivera.waivera.core.network.dto.ComplianceReasonResponse
import com.waivera.waivera.core.network.dto.LateContextResponse
import com.waivera.waivera.core.network.dto.WorkflowStepResponse
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class ReviewMappingTest {
    private val requirementsJson =
        """
        {"taskId":7,"approvalMode":"AllRemaining","willCompleteTask":true,"previousDriveFilingDate":"2026-03-08",
         "requirementsVersion":"r9","workflowVersion":"w4","canApproveAllRemaining":true,"remainingStageCount":2,
         "requirements":[{"ruleId":1,"actionId":5,"requirementKey":"profile","actionType":"participant_profile_change",
           "title":" Update plan dates ","fields":[
             {"key":"cpocDate","label":"CPOC start","fieldType":"date","required":true,"defaultValue":"2027-01-01T00:00:00"},
             {"key":"photo","fieldType":"file","required":false}]}],
         "somethingNew":1}
        """.trimIndent()

    @Test
    fun the_plan_reads_the_server_mode_versions_and_fields() {
        val plan = ReviewMapping.plan(
            WaiveraJson.decodeFromString<ApprovalRequirementsResponse>(requirementsJson),
            ApprovalMode.CurrentStage,
        )
        assertEquals(ApprovalMode.AllRemaining, plan.mode)
        assertTrue(plan.willComplete)
        // A literal date: the spring-forward day stays the same day in any zone.
        assertEquals(LocalDate.of(2026, 3, 8), plan.previousFiling)
        val requirement = plan.requirements.single()
        assertEquals("Update plan dates", requirement.title)
        assertTrue(requirement.isPlanDates)
        assertEquals(listOf(FieldType.Date, FieldType.Unsupported), requirement.fields.map { it.type })
        assertEquals("photo", requirement.fields[1].label)
    }

    @Test
    fun the_approval_body_sends_only_filled_answers_and_the_filing_date_when_kept() {
        val plan = ReviewMapping.plan(
            WaiveraJson.decodeFromString<ApprovalRequirementsResponse>(requirementsJson),
            ApprovalMode.CurrentStage,
        )
        val send = ApprovalSend(
            plan = plan,
            idempotencyKey = "k1",
            answers = mapOf(5 to mapOf("cpocDate" to "2027-01-05", "photo" to " ")),
            acknowledgeWarnings = true,
            discardDrafts = true,
            useOriginalSection = true,
        )
        val body = ReviewMapping.body(send)
        assertEquals("AllRemaining", body.mode)
        assertEquals("w4", body.expectedWorkflowVersion)
        assertEquals("r9", body.requirementsVersion)
        val values = body.requirementResponses.single().values
        assertEquals("2027-01-05", values["cpocDate"]?.jsonPrimitive?.content)
        assertNull(values["photo"])
        assertEquals("2026-03-08", body.expectedPreviousDriveFilingDate)
        assertNull(ReviewMapping.body(send.copy(useOriginalSection = false)).expectedPreviousDriveFilingDate)
        // The wire names match the C# request.
        val wire = WaiveraJson.encodeToJsonElement(ApproveTaskStageBody.serializer(), body).jsonObject
        assertTrue(
            wire.keys.containsAll(
                listOf("closeUnfinalizedDrafts", "acknowledgeRelationshipWarnings", "requirementResponses"),
            ),
        )
    }

    @Test
    fun refusals_map_to_the_step_the_person_sees() {
        fun refusal(json: String) = ReviewMapping.refusal(WaiveraJson.decodeFromString<ApprovalErrorResponse>(json))
        val blocked = refusal(
            """{"code":"TASK_COMPLETION_BLOCKED","message":"raw server text","completion":{"blockers":[
               {"sourceTaskId":31,"sourceTaskFriendlyId":988,"organizationAbbreviation":"HEA","sourceTaskTitle":"Annual assessment",
                "relationshipName":"Depends on"}]}}""",
        )
        val related = assertIs<ApprovalOutcome.Blocked>(blocked).related.single()
        assertEquals("HEA-988", related.key)
        assertEquals("Depends on", related.relationship)
        assertIs<ApprovalOutcome.NeedsAcknowledgement>(refusal("""{"code":"TASK_COMPLETION_WARNING_REQUIRED"}"""))
        val changed = refusal(
            """{"code":"WORKFLOW_REQUIREMENTS_CHANGED","requirementsVersion":"r10","requirements":[]}""",
        )
        assertEquals("r10", assertIs<ApprovalOutcome.RequirementsChanged>(changed).requirementsVersion)
        assertEquals(ApprovalOutcome.Stale, refusal("""{"code":"WORKFLOW_REQUIREMENTS_NOT_SATISFIED"}"""))
        assertEquals(ApprovalOutcome.Stale, refusal("""{"code":"WORKFLOW_VERSION_MISMATCH"}"""))
    }

    @Test
    fun steps_are_ordered_and_unknown_statuses_do_not_fail() {
        val steps = ReviewMapping.steps(
            WaiveraJson.decodeFromString<List<WorkflowStepResponse>>(
                """[{"stepOrder":2,"stepRole":"Supervisor","status":"ACTIVE"},
                    {"stepOrder":1,"stepRole":" ","status":"COMPLETED","actualApprover":{"firstName":"Chris","lastName":"Patel"}},
                    {"stepOrder":3,"status":"SKIPPED"}]""",
            ),
        )
        assertEquals(listOf(1, 2, 3), steps.map { it.order })
        assertNull(steps[0].role)
        assertEquals("Chris Patel", steps[0].approverName)
        assertEquals(listOf(StepStatus.Completed, StepStatus.Active, StepStatus.Unknown), steps.map { it.status })
    }

    @Test
    fun steps_name_the_person_and_word_the_role_for_people() {
        val steps = ReviewMapping.steps(
            WaiveraJson.decodeFromString<List<WorkflowStepResponse>>(
                """[{"stepOrder":1,"stepRole":"[CASE_WORKER]","status":"COMPLETED",
                     "actualApprover":{"displayName":"Priya Raman"},"projectedApprover":{"routingRule":"Completed"}},
                    {"stepOrder":2,"stepRole":"Supervisor","status":"ACTIVE",
                     "projectedApprover":{"exactUser":{"displayName":"Jordan Ellis"}}},
                    {"stepOrder":3,"stepRole":"Director","status":"FUTURE",
                     "projectedApprover":{"routingRule":"Pending Discovery: Director"}}]""",
            ),
            assignee = "Sam Lee",
        )
        assertEquals("Case worker", steps[0].role)
        assertNull(steps[0].waitingOn)
        assertEquals("Jordan Ellis", steps[1].waitingOn)
        // Nobody found for the role: the note falls back to the role.
        assertNull(steps[2].waitingOn)
        assertEquals("Director", steps[2].role)
    }

    @Test
    fun a_case_worker_stage_with_nobody_assigned_waits_on_the_tasks_assignee() {
        val items = WaiveraJson.decodeFromString<List<WorkflowStepResponse>>(
            """[{"stepOrder":1,"stepRole":"[CASE_WORKER]","status":"ACTIVE","projectedApprover":{}}]""",
        )
        assertEquals("Sam Lee", ReviewMapping.steps(items, assignee = "Sam Lee").single().waitingOn)
        assertNull(ReviewMapping.steps(items).single().waitingOn)
        assertEquals("Site lead", ReviewMapping.humanRole("[SITE_LEAD]"))
        // A pool's first pick is not who it waits on.
        val pool = WaiveraJson.decodeFromString<List<WorkflowStepResponse>>(
            """[{"stepOrder":1,"stepRole":"Supervisor","status":"ACTIVE",
                 "projectedApprover":{"exactUser":{"displayName":"Ana Ruiz"},"isPool":true}}]""",
        )
        assertNull(ReviewMapping.steps(pool).single().waitingOn)
    }

    @Test
    fun late_reasons_follow_the_web_filter() {
        val reasons = ReviewMapping.lateReasons(
            WaiveraJson.decodeFromString<List<ComplianceReasonResponse>>(
                """[{"id":1,"name":"System shown","isSystemCode":true,"isActive":false,"canBeUsedForLateJustification":true,
                     "presetExplanation":"Preset."},
                    {"id":2,"name":"System hidden","isSystemCode":true,"isHiddenFromUserSelection":true,"canBeUsedForLateJustification":true},
                    {"id":3,"name":"Agency active","isActive":true,"canBeUsedForLateJustification":true,"presetExplanation":"  "},
                    {"id":4,"name":"Agency inactive","isActive":false,"canBeUsedForLateJustification":true},
                    {"id":5,"name":"Delay only","isActive":true,"canBeUsedForDelay":true}]""",
            ),
        )
        assertEquals(listOf(1L, 3L), reasons.map { it.id })
        assertEquals("Preset.", reasons[0].preset)
        assertNull(reasons[1].preset)
    }

    @Test
    fun a_returned_late_reason_is_marked_and_reviews_read_their_decision() {
        val late = ReviewMapping.late(
            WaiveraJson.decodeFromString<LateContextResponse>(
                """{"canSubmit":true,"canReview":false,"satisfiesSubmission":false,
                    "justification":{"id":81,"reasonCodeId":1,"explanation":"In the hospital.","status":"Rejected"},
                    "reviews":[{"reviewerName":"Priya","decision":"ReturnForClarification","note":" Add dates. "}]}""",
            ),
        )
        assertTrue(late.justification?.returned == true)
        assertEquals(false, late.reviews.single().agreed)
        assertEquals("Add dates.", late.reviews.single().note)
    }

    @Test
    fun closing_with_merged_pdfs_uses_the_merge_mode() {
        assertEquals("CloseAndMergePdfs", ReviewMapping.closureMode(listOf("a", "b")))
        assertEquals("CloseTask", ReviewMapping.closureMode(null))
    }
}
