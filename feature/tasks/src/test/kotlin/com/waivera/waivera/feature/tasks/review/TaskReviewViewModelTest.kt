package com.waivera.waivera.feature.tasks.review

import com.waivera.waivera.core.data.model.ApprovalMode
import com.waivera.waivera.core.data.model.ApprovalOutcome
import com.waivera.waivera.core.data.model.ClosurePdf
import com.waivera.waivera.core.data.model.EmployeeClosure
import com.waivera.waivera.core.data.model.FieldType
import com.waivera.waivera.core.data.model.FormDraft
import com.waivera.waivera.core.data.model.LateJustification
import com.waivera.waivera.core.data.model.PlanDates
import com.waivera.waivera.core.data.model.RelatedTask
import com.waivera.waivera.core.data.model.RequirementField
import com.waivera.waivera.core.data.model.SendBackOption
import com.waivera.waivera.core.data.model.StageRequirement
import com.waivera.waivera.core.data.model.SuggestedDelay
import com.waivera.waivera.core.data.model.TaskDetail
import com.waivera.waivera.core.data.model.TaskReviewAccess
import com.waivera.waivera.core.demo.DemoGraph
import com.waivera.waivera.core.network.error.WaiveraError
import com.waivera.waivera.core.testing.MainDispatcherRule
import com.waivera.waivera.core.testing.TestClock
import com.waivera.waivera.feature.tasks.FakeTaskUploads
import com.waivera.waivera.feature.tasks.FakeTasks
import com.waivera.waivera.feature.tasks.Loadable
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule

class TaskReviewViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private class Harness(val vm: TaskReviewViewModel, val effects: MutableList<TaskReviewEffect>)

    private fun TestScope.harness(
        review: FakeTaskReview,
        uploads: FakeTaskUploads = FakeTaskUploads(),
        clock: TestClock = TestClock(),
    ): Harness {
        val vm = TaskReviewViewModel(7, review, uploads, DemoGraph(backgroundScope).calendar, clock)
        val effects = mutableListOf<TaskReviewEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.effects.toList(effects) }
        runCurrent()
        return Harness(vm, effects)
    }

    private val reviewer =
        TaskReviewAccess(canApprove = true, canApproveAll = true, remainingStages = 3, canReject = true, ready = true)

    /** Due in the future unless a test says otherwise, so the late check stays out of the way. */
    private fun task(due: LocalDate = LocalDate.of(2026, 12, 1), client: Boolean = true): TaskDetail =
        FakeTasks.task(due = due).copy(review = reviewer, isClientTask = client)

    @Test
    fun approving_with_no_extra_steps_sends_once_and_reloads() = runTest {
        val review = FakeTaskReview()
        val h = harness(review)
        h.vm.start(ReviewAction.Approve, task())
        runCurrent()
        val send = review.sent.single()
        assertEquals(ApprovalMode.CurrentStage, send.plan.mode)
        assertFalse(send.discardDrafts)
        assertEquals(listOf(TaskReviewEffect.Done(ReviewAction.Approve, false), TaskReviewEffect.Reload), h.effects)
        assertEquals(ReviewUiState(), h.vm.uiState.value)
    }

    @Test
    fun approve_all_asks_first_then_checks_unfinished_forms() = runTest {
        val review = FakeTaskReview().apply { drafts = Result.success(listOf(FormDraft(1, "Home visit note"))) }
        val h = harness(review)
        h.vm.start(ReviewAction.ApproveAll, task())
        runCurrent()
        assertEquals(ReviewStep.ConfirmAll(stages = 3, submit = false), h.vm.uiState.value.step)
        h.vm.confirmAll()
        runCurrent()
        assertEquals(ReviewStep.Drafts(listOf("Home visit note")), h.vm.uiState.value.step)
        assertTrue(review.sent.isEmpty())
        h.vm.discardDrafts()
        runCurrent()
        val send = review.sent.single()
        assertEquals(ApprovalMode.AllRemaining, send.plan.mode)
        assertTrue(send.discardDrafts)
    }

    @Test
    fun closing_any_sheet_stops_the_chain_without_sending() = runTest {
        val review = FakeTaskReview()
        val h = harness(review)
        h.vm.start(ReviewAction.ApproveAll, task())
        runCurrent()
        h.vm.dismiss()
        runCurrent()
        assertTrue(review.sent.isEmpty())
        assertEquals(ReviewUiState(), h.vm.uiState.value)
        // A fresh start after closing begins again.
        h.vm.start(ReviewAction.ApproveAll, task())
        runCurrent()
        assertIs<ReviewStep.ConfirmAll>(h.vm.uiState.value.step)
    }

    @Test
    fun a_reopened_task_asks_which_drive_section() = runTest {
        val filed = LocalDate.of(2026, 10, 1)
        val review = FakeTaskReview().apply {
            plan = { Result.success(FakeTaskReview.plan(it, willComplete = true).copy(previousFiling = filed)) }
        }
        val h = harness(review)
        h.vm.start(ReviewAction.Approve, task())
        runCurrent()
        assertEquals(ReviewStep.DriveSection(filed, useOriginal = true), h.vm.uiState.value.step)
        h.vm.chooseDrive(false)
        h.vm.confirmDrive()
        runCurrent()
        assertFalse(review.sent.single().useOriginalSection)
    }

    @Test
    fun an_employee_task_with_pdfs_to_merge_saves_the_order_before_approving() = runTest {
        val pdfs =
            listOf(ClosurePdf("a", "card.pdf", 1), ClosurePdf("b", "page.pdf", 1), ClosurePdf("c", "extra.pdf", 1))
        val review = FakeTaskReview().apply {
            plan = { Result.success(FakeTaskReview.plan(it, willComplete = true)) }
            closure =
                Result.success(
                    EmployeeClosure(needsExpiration = false, nextExpiration = null, canMerge = true, pdfs = pdfs),
                )
        }
        val h = harness(review)
        h.vm.start(ReviewAction.Approve, task(client = false))
        runCurrent()
        assertIs<ReviewStep.Closure>(h.vm.uiState.value.step)
        h.vm.setMerge(true)
        h.vm.togglePdf("a")
        h.vm.togglePdf("c")
        h.vm.saveClosure()
        runCurrent()
        // One PDF can't be merged: nothing is saved yet.
        assertTrue(review.closureChoices.isEmpty())
        assertTrue(assertIs<ReviewStep.Closure>(h.vm.uiState.value.step).form.showErrors)
        h.vm.togglePdf("c")
        h.vm.movePdf("c", up = true)
        h.vm.saveClosure()
        runCurrent()
        assertEquals(listOf<List<String>?>(listOf("c", "b")), review.closureChoices.map { it.first })
        assertEquals(1, review.sent.size)
    }

    @Test
    fun an_employee_task_with_nothing_to_choose_closes_plainly() = runTest {
        val review = FakeTaskReview().apply { plan = { Result.success(FakeTaskReview.plan(it, willComplete = true)) } }
        val h = harness(review)
        h.vm.start(ReviewAction.Approve, task(client = false))
        runCurrent()
        assertEquals(listOf<Pair<List<String>?, LocalDate?>>(null to null), review.closureChoices)
        assertEquals(1, review.sent.size)
    }

    private val planDates = StageRequirement(
        actionId = 5,
        key = "profile",
        actionType = StageRequirement.PLAN_DATES,
        title = "Update plan dates",
        description = null,
        fields = listOf(
            RequirementField(
                StageRequirement.PLAN_START,
                "CPOC start date",
                FieldType.Date,
                required = true,
                defaultValue = null,
            ),
            RequirementField(
                StageRequirement.PLAN_END,
                "CPOC end date",
                FieldType.Date,
                required = true,
                defaultValue = null,
            ),
        ),
    )

    @Test
    fun stage_requirements_check_the_dates_and_warn_once_before_sending() = runTest {
        val review = FakeTaskReview().apply {
            plan = { Result.success(FakeTaskReview.plan(it).copy(requirements = listOf(planDates))) }
        }
        val h = harness(review)
        h.vm.start(
            ReviewAction.Approve,
            task().copy(planDates = PlanDates(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31))),
        )
        runCurrent()
        h.vm.submitRequirements()
        var form = assertIs<ReviewStep.Requirements>(h.vm.uiState.value.step).form
        assertEquals(IssueKind.Required, form.errors[5]?.get(StageRequirement.PLAN_START)?.kind)

        // Not the day after the last plan, and not a year long: warnings, then a second tap sends.
        h.vm.setRequirement(5, StageRequirement.PLAN_START, "2027-01-05")
        h.vm.setRequirement(5, StageRequirement.PLAN_END, "2027-06-30")
        h.vm.submitRequirements()
        form = assertIs<ReviewStep.Requirements>(h.vm.uiState.value.step).form
        assertTrue(form.confirmWarnings)
        assertEquals(LocalDate.of(2027, 1, 1), form.warnings[5]?.get(StageRequirement.PLAN_START)?.date)
        assertEquals(LocalDate.of(2028, 1, 4), form.warnings[5]?.get(StageRequirement.PLAN_END)?.date)
        assertTrue(review.sent.isEmpty())
        h.vm.submitRequirements()
        runCurrent()
        assertEquals(
            mapOf(5 to mapOf("cpocDate" to "2027-01-05", "cpocEndDate" to "2027-06-30")),
            review.sent.single().answers,
        )
    }

    @Test
    fun an_end_date_before_the_start_is_an_error_not_a_warning() = runTest {
        val review = FakeTaskReview().apply {
            plan = { Result.success(FakeTaskReview.plan(it).copy(requirements = listOf(planDates))) }
        }
        val h = harness(review)
        h.vm.start(ReviewAction.Approve, task())
        runCurrent()
        h.vm.setRequirement(5, StageRequirement.PLAN_START, "2027-02-01")
        h.vm.setRequirement(5, StageRequirement.PLAN_END, "2026-01-31")
        h.vm.submitRequirements()
        val form = assertIs<ReviewStep.Requirements>(h.vm.uiState.value.step).form
        assertEquals(IssueKind.EndBeforeStart, form.errors[5]?.get(StageRequirement.PLAN_END)?.kind)
        assertTrue(form.warnings.isEmpty())
    }

    @Test
    fun a_related_task_warning_sends_again_acknowledged_with_the_same_key() = runTest {
        val related = RelatedTask(31, "HEA-988", "Annual assessment", "IN_PROGRESS", null)
        val review = FakeTaskReview().apply {
            outcomes += Result.success(ApprovalOutcome.NeedsAcknowledgement(listOf(related)))
        }
        val h = harness(review)
        h.vm.start(ReviewAction.Approve, task())
        runCurrent()
        assertEquals(ReviewStep.Related(blocked = false, related = listOf(related)), h.vm.uiState.value.step)
        h.vm.completeAnyway()
        runCurrent()
        assertEquals(2, review.sent.size)
        assertFalse(review.sent[0].acknowledgeWarnings)
        assertTrue(review.sent[1].acknowledgeWarnings)
        assertEquals(review.sent[0].idempotencyKey, review.sent[1].idempotencyKey)
        assertIs<TaskReviewEffect.Done>(h.effects.first())
    }

    @Test
    fun a_blocking_related_task_can_be_opened_but_not_completed() = runTest {
        val related = RelatedTask(31, "HEA-988", "Annual assessment", "IN_PROGRESS", null)
        val review = FakeTaskReview().apply { outcomes += Result.success(ApprovalOutcome.Blocked(listOf(related))) }
        val h = harness(review)
        h.vm.start(ReviewAction.Approve, task())
        runCurrent()
        h.vm.completeAnyway()
        runCurrent()
        assertEquals(1, review.sent.size)
        h.vm.openRelated(31)
        runCurrent()
        assertEquals(TaskReviewEffect.OpenTask(31), h.effects.single())
        assertNull(h.vm.uiState.value.step)
    }

    @Test
    fun a_workflow_that_moved_on_reloads_and_says_so() = runTest {
        val review = FakeTaskReview().apply { outcomes += Result.success(ApprovalOutcome.Stale) }
        val h = harness(review)
        h.vm.start(ReviewAction.Approve, task())
        runCurrent()
        assertEquals(listOf(TaskReviewEffect.Changed, TaskReviewEffect.Reload), h.effects)
    }

    @Test
    fun a_failed_requirements_check_stops_with_a_message_and_a_retry_works() = runTest {
        val review = FakeTaskReview().apply { plan = { Result.failure(WaiveraError.Network()) } }
        val h = harness(review)
        h.vm.start(ReviewAction.Approve, task())
        runCurrent()
        assertEquals(listOf<TaskReviewEffect>(TaskReviewEffect.Failed), h.effects)
        assertEquals(ReviewUiState(), h.vm.uiState.value)

        // Reconnected: the same button runs the chain again.
        review.plan = { Result.success(FakeTaskReview.plan(it)) }
        h.vm.start(ReviewAction.Approve, task())
        runCurrent()
        assertEquals(1, review.sent.size)
    }

    @Test
    fun a_send_that_fails_inside_a_sheet_keeps_the_answers_for_a_retry() = runTest {
        val review = FakeTaskReview().apply {
            plan = { Result.success(FakeTaskReview.plan(it).copy(requirements = listOf(planDates))) }
            outcomes += Result.failure(WaiveraError.Network())
        }
        val h = harness(review)
        h.vm.start(ReviewAction.Approve, task())
        runCurrent()
        h.vm.setRequirement(5, StageRequirement.PLAN_START, "2027-01-01")
        h.vm.setRequirement(5, StageRequirement.PLAN_END, "2027-12-31")
        h.vm.submitRequirements()
        runCurrent()
        val form = assertIs<ReviewStep.Requirements>(h.vm.uiState.value.step).form
        assertTrue(form.failed)
        assertFalse(form.isSaving)
        h.vm.submitRequirements()
        runCurrent()
        assertEquals(2, review.sent.size)
        assertIs<TaskReviewEffect.Done>(h.effects.first())
    }

    // ------------------------------------------------------------- late reason

    private val pastDue = LocalDate.of(2026, 3, 7)

    /** Mar 8 2026 is the US spring-forward day; the late check reads the saved zone's day. */
    private val afterMidnightChicago = Instant.parse("2026-03-08T06:30:00Z")

    @Test
    fun a_task_due_yesterday_in_the_saved_zone_is_late_even_across_the_dst_change() = runTest {
        val review = FakeTaskReview().apply {
            late = Result.success(FakeTaskReview.satisfied().copy(satisfied = false))
        }
        // 17:30 in Chicago on the due day (still the due day in UTC too): not asked yet.
        val clock = TestClock(Instant.parse("2026-03-07T23:30:00Z"))
        val h = harness(review, clock = clock)
        h.vm.start(ReviewAction.Submit, task(due = pastDue))
        runCurrent()
        assertNull(h.vm.uiState.value.step)
        assertEquals(1, review.sent.size)

        // 00:30 the next day in Chicago (06:30 UTC, hours before the clocks change): late.
        clock.now = afterMidnightChicago
        h.vm.start(ReviewAction.Submit, task(due = pastDue))
        runCurrent()
        assertIs<ReviewStep.LateReason>(h.vm.uiState.value.step)
        assertEquals(1, review.sent.size)
    }

    @Test
    fun the_late_reason_uploads_its_evidence_then_the_chain_goes_on() = runTest {
        val review = FakeTaskReview().apply {
            late = Result.success(FakeTaskReview.satisfied().copy(satisfied = false))
        }
        val uploads = FakeTaskUploads()
        val h = harness(review, uploads, TestClock(afterMidnightChicago))
        h.vm.start(ReviewAction.Submit, task(due = pastDue))
        runCurrent()
        h.vm.setLateReason(1)
        var form = assertIs<ReviewStep.LateReason>(h.vm.uiState.value.step).form
        assertEquals("Participant was unavailable.", form.explanation)
        h.vm.submitLate()
        form = assertIs<ReviewStep.LateReason>(h.vm.uiState.value.step).form
        assertTrue(form.showErrors)
        assertTrue(form.evidenceMissing)

        h.vm.pickEvidence(listOf("content://picked/discharge.pdf"))
        runCurrent()
        h.vm.submitLate()
        runCurrent()
        assertEquals("discharge.pdf", uploads.evidence.single().name)
        assertEquals(
            FakeTaskReview.Justified(1, "Participant was unavailable.", FakeTaskUploads.EVIDENCE_ID, null),
            review.justified.single(),
        )
        assertEquals(1, review.sent.size)
    }

    @Test
    fun typed_text_is_kept_when_another_reason_is_picked() = runTest {
        val review = FakeTaskReview().apply {
            late = Result.success(FakeTaskReview.satisfied().copy(satisfied = false))
        }
        val h = harness(review, clock = TestClock(afterMidnightChicago))
        h.vm.start(ReviewAction.Submit, task(due = pastDue))
        runCurrent()
        h.vm.setLateExplanation("Dana was in the hospital.")
        h.vm.setLateReason(1)
        assertEquals(
            "Dana was in the hospital.",
            assertIs<ReviewStep.LateReason>(h.vm.uiState.value.step).form.explanation,
        )
    }

    @Test
    fun a_reported_delay_fills_the_late_reason_until_dropped() = runTest {
        val delay = SuggestedDelay(44, 2, "Waiting on outside agency", "Waiting on the county.", evidenceId = 9)
        val review = FakeTaskReview().apply {
            late = Result.success(FakeTaskReview.satisfied().copy(satisfied = false, suggestedDelay = delay))
        }
        val h = harness(review, clock = TestClock(afterMidnightChicago))
        h.vm.start(ReviewAction.Submit, task(due = pastDue))
        runCurrent()
        h.vm.submitLate()
        runCurrent()
        assertEquals(FakeTaskReview.Justified(2, "Waiting on the county.", 9, 44), review.justified.single())
    }

    @Test
    fun a_late_reason_only_the_assignee_can_send_stops_with_a_message() = runTest {
        val review = FakeTaskReview().apply {
            late = Result.success(FakeTaskReview.satisfied().copy(satisfied = false, canSubmit = false))
        }
        val h = harness(review, clock = TestClock(afterMidnightChicago))
        h.vm.start(ReviewAction.Approve, task(due = pastDue))
        runCurrent()
        assertEquals(listOf<TaskReviewEffect>(TaskReviewEffect.LateNeedsAssignee), h.effects)
        assertTrue(review.sent.isEmpty())
    }

    private val submitted = LateJustification(
        id = 81,
        reasonId = 1,
        reasonName = "Participant unavailable",
        explanation = "In the hospital.",
        evidenceId = 12,
        delayId = null,
        submittedBy = "Chris Patel",
        returned = false,
    )

    @Test
    fun a_reviewer_agrees_with_the_late_reason_and_the_approval_goes_on() = runTest {
        val review = FakeTaskReview().apply {
            late = Result.success(FakeTaskReview.satisfied().copy(canReview = true, justification = submitted))
        }
        val h = harness(review, clock = TestClock(afterMidnightChicago))
        h.vm.start(ReviewAction.Approve, task(due = pastDue))
        runCurrent()
        assertIs<ReviewStep.LateReview>(h.vm.uiState.value.step)
        h.vm.setLateNote("Checked with the hospital.")
        h.vm.agreeLate()
        runCurrent()
        assertEquals(Triple(81L, "Checked with the hospital.", null), review.agreed.single())
        assertEquals(1, review.sent.size)
    }

    @Test
    fun a_reviewer_corrects_the_late_reason_keeping_its_evidence() = runTest {
        val review = FakeTaskReview().apply {
            late = Result.success(FakeTaskReview.satisfied().copy(canReview = true, justification = submitted))
        }
        val h = harness(review, clock = TestClock(afterMidnightChicago))
        h.vm.start(ReviewAction.Approve, task(due = pastDue))
        runCurrent()
        h.vm.editLate()
        runCurrent()
        h.vm.setLateExplanation("In the hospital Nov 10 to 15.")
        h.vm.submitLate()
        runCurrent()
        val edit = review.agreed.single().third
        assertEquals(12, edit?.evidenceId)
        assertEquals("In the hospital Nov 10 to 15.", edit?.explanation)
        assertTrue(review.justified.isEmpty())
    }

    @Test
    fun returning_the_late_reason_sends_the_task_back_with_no_review_time() = runTest {
        val review = FakeTaskReview().apply {
            late = Result.success(FakeTaskReview.satisfied().copy(canReview = true, justification = submitted))
        }
        val h = harness(review, clock = TestClock(afterMidnightChicago))
        h.vm.start(ReviewAction.Approve, task(due = pastDue))
        runCurrent()
        h.vm.returnLate()
        runCurrent()
        val form = assertIs<ReviewStep.SendBack>(h.vm.uiState.value.step).form
        assertTrue(form.returnsLateReason)
        assertEquals(11, form.approvalId)
        h.vm.setFeedback("Add the discharge date.")
        h.vm.submitSendBack()
        runCurrent()
        assertEquals(FakeTaskReview.SentBack(11, "Add the discharge date.", 0, 81), review.sentBack.single())
        assertTrue(review.sent.isEmpty())
    }

    // ------------------------------------------------------------- send back

    @Test
    fun rejecting_needs_a_step_and_feedback_and_a_failure_can_be_retried() = runTest {
        val review = FakeTaskReview().apply {
            options =
                Result.success(
                    listOf(SendBackOption(11, "Chris Patel", "Case manager"), SendBackOption(12, "Priya", null)),
                )
            sendBackResult = Result.failure(WaiveraError.Network())
        }
        val h = harness(review)
        h.vm.startReject()
        runCurrent()
        var form = assertIs<ReviewStep.SendBack>(h.vm.uiState.value.step).form
        assertIs<Loadable.Ready<*>>(form.options)
        assertNull(form.approvalId)
        h.vm.submitSendBack()
        form = assertIs<ReviewStep.SendBack>(h.vm.uiState.value.step).form
        assertTrue(form.optionMissing && form.feedbackMissing && form.showErrors)

        h.vm.chooseSendBack(12)
        h.vm.setFeedback("Goals 2 and 3 are last year's.")
        h.vm.setMinutes("15")
        h.vm.submitSendBack()
        runCurrent()
        assertTrue(assertIs<ReviewStep.SendBack>(h.vm.uiState.value.step).form.failed)

        review.sendBackResult = Result.success(Unit)
        h.vm.submitSendBack()
        runCurrent()
        assertEquals(FakeTaskReview.SentBack(12, "Goals 2 and 3 are last year's.", 15, null), review.sentBack.last())
        assertEquals(listOf(TaskReviewEffect.SentBack, TaskReviewEffect.Reload), h.effects)
    }

    @Test
    fun no_earlier_step_means_nothing_to_send_back_to() = runTest {
        val review = FakeTaskReview().apply { options = Result.success(emptyList()) }
        val h = harness(review)
        h.vm.startReject()
        runCurrent()
        assertTrue(assertIs<ReviewStep.SendBack>(h.vm.uiState.value.step).form.noOptions)
    }

    @Test
    fun review_minutes_must_be_a_day_or_less() = runTest {
        val h = harness(FakeTaskReview())
        h.vm.startReject()
        runCurrent()
        h.vm.setMinutes("1500")
        assertTrue(assertIs<ReviewStep.SendBack>(h.vm.uiState.value.step).form.minutesInvalid)
        h.vm.setMinutes("")
        assertEquals(0, assertIs<ReviewStep.SendBack>(h.vm.uiState.value.step).form.minutesValue)
    }

    // ------------------------------------------------------------- review fixes

    @Test
    fun changed_requirements_are_fetched_again_with_the_new_versions() = runTest {
        var asked = 0
        val review = FakeTaskReview().apply {
            plan = {
                asked++
                val version = if (asked == 1) "w1" else "w2"
                Result.success(
                    FakeTaskReview.plan(it).copy(workflowVersion = version, requirements = listOf(planDates)),
                )
            }
            outcomes += Result.success(ApprovalOutcome.RequirementsChanged(listOf(planDates), "r2"))
        }
        val h = harness(review)
        h.vm.start(ReviewAction.Approve, task())
        runCurrent()
        h.vm.setRequirement(5, StageRequirement.PLAN_START, "2027-01-01")
        h.vm.setRequirement(5, StageRequirement.PLAN_END, "2027-12-31")
        h.vm.submitRequirements()
        runCurrent()
        val form = assertIs<ReviewStep.Requirements>(h.vm.uiState.value.step).form
        assertTrue(form.changed)
        assertEquals(2, asked)
        h.vm.setRequirement(5, StageRequirement.PLAN_START, "2027-01-01")
        h.vm.setRequirement(5, StageRequirement.PLAN_END, "2027-12-31")
        h.vm.submitRequirements()
        runCurrent()
        assertEquals(listOf("w1", "w2"), review.sent.map { it.plan.workflowVersion })
        assertTrue(review.sent[0].idempotencyKey != review.sent[1].idempotencyKey)
    }

    @Test
    fun a_failure_after_the_late_reason_is_saved_does_not_offer_to_send_it_again() = runTest {
        val review = FakeTaskReview().apply {
            late = Result.success(FakeTaskReview.satisfied().copy(satisfied = false))
            outcomes += Result.failure(WaiveraError.Network())
        }
        val h = harness(review, clock = TestClock(afterMidnightChicago))
        h.vm.start(ReviewAction.Submit, task(due = pastDue))
        runCurrent()
        h.vm.setLateReason(2)
        h.vm.setLateExplanation("Waiting on the county.")
        h.vm.submitLate()
        runCurrent()
        assertEquals(1, review.justified.size)
        assertNull(h.vm.uiState.value.step)
        assertTrue(TaskReviewEffect.Failed in h.effects)
    }

    @Test
    fun a_retry_after_a_failed_justification_keeps_the_delay_and_the_uploaded_evidence() = runTest {
        val delay = SuggestedDelay(44, 2, "Waiting on outside agency", "Waiting on the county.", evidenceId = 9)
        val review = FakeTaskReview().apply {
            late = Result.success(FakeTaskReview.satisfied().copy(satisfied = false, suggestedDelay = delay))
            justifyResult = Result.failure(WaiveraError.Network())
        }
        val uploads = FakeTaskUploads()
        val h = harness(review, uploads, TestClock(afterMidnightChicago))
        h.vm.start(ReviewAction.Submit, task(due = pastDue))
        runCurrent()
        h.vm.pickEvidence(listOf("content://picked/letter.pdf"))
        runCurrent()
        h.vm.submitLate()
        runCurrent()
        review.justifyResult = Result.success(Unit)
        h.vm.submitLate()
        runCurrent()
        assertEquals(1, uploads.evidence.size)
        assertEquals(listOf("content://picked/letter.pdf"), uploads.evidencePicks)
        val expected = FakeTaskReview.Justified(2, "Waiting on the county.", FakeTaskUploads.EVIDENCE_ID, 44)
        assertEquals(listOf(expected, expected), review.justified)
    }

    @Test
    fun a_task_the_server_says_is_not_late_goes_straight_on() = runTest {
        val review = FakeTaskReview().apply {
            late = Result.success(FakeTaskReview.satisfied().copy(satisfied = false, late = false))
        }
        val h = harness(review, clock = TestClock(afterMidnightChicago))
        h.vm.start(ReviewAction.Submit, task(due = pastDue))
        runCurrent()
        assertNull(h.vm.uiState.value.step)
        assertEquals(1, review.sent.size)
    }

    @Test
    fun a_task_late_by_the_servers_utc_day_is_checked_before_the_saved_zone_day_ends() = runTest {
        val review = FakeTaskReview().apply {
            late = Result.success(FakeTaskReview.satisfied().copy(satisfied = false))
        }
        // 01:00 UTC on Mar 8 is still Mar 7 evening in Chicago, but the server already counts Mar 8.
        val h = harness(review, clock = TestClock(Instant.parse("2026-03-08T01:00:00Z")))
        h.vm.start(ReviewAction.Submit, task(due = pastDue))
        runCurrent()
        assertIs<ReviewStep.LateReason>(h.vm.uiState.value.step)
    }
}
