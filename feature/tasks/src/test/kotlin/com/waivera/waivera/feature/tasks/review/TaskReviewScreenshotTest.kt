package com.waivera.waivera.feature.tasks.review

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.waivera.waivera.core.data.model.ChangeReason
import com.waivera.waivera.core.data.model.ClosurePdf
import com.waivera.waivera.core.data.model.EmployeeClosure
import com.waivera.waivera.core.data.model.FieldType
import com.waivera.waivera.core.data.model.LateContext
import com.waivera.waivera.core.data.model.LateJustification
import com.waivera.waivera.core.data.model.LateReview
import com.waivera.waivera.core.data.model.PreparedFile
import com.waivera.waivera.core.data.model.RelatedTask
import com.waivera.waivera.core.data.model.RequirementField
import com.waivera.waivera.core.data.model.SendBackOption
import com.waivera.waivera.core.data.model.StageRequirement
import com.waivera.waivera.core.data.model.StepStatus
import com.waivera.waivera.core.data.model.TaskReviewAccess
import com.waivera.waivera.core.data.model.WorkflowStep
import com.waivera.waivera.core.designsystem.component.WaiveraSheetFrame
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.core.screenshottesting.ScreenshotSdk
import com.waivera.waivera.core.screenshottesting.captureScreenshots
import com.waivera.waivera.feature.tasks.Loadable
import com.waivera.waivera.feature.tasks.TaskDetailActions
import com.waivera.waivera.feature.tasks.TaskDetailContent
import com.waivera.waivera.feature.tasks.TaskPreviewData
import java.time.LocalDate
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Submit/Approve/Reject bar and every sheet of the review chain (mockups C1 to C12). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotSdk.VALUE])
class TaskReviewScreenshotTest {
    @Test
    fun reviewerDetail() = captureScreenshots("task_review_detail_reviewer", heightDp = 1700) {
        val base = TaskPreviewData.detailState()
        TaskDetailContent(
            state = base.copy(task = base.task?.copy(review = REVIEWER, steps = STEPS)),
            snackbar = remember { SnackbarHostState() },
            actions = TaskDetailActions.None,
        )
    }

    @Test
    fun submitBar() =
        bar("task_review_bar_submit", REVIEWER.copy(canSubmit = true, canApprove = false, canReject = false))

    @Test
    fun submitBarNotReady() = bar(
        "task_review_bar_not_ready",
        TaskReviewAccess(
            canSubmit = true,
            canApprove = false,
            canApproveAll = false,
            remainingStages = 0,
            canReject = false,
            ready = false,
        ),
    )

    @Test
    fun approveBarWorking() = bar("task_review_bar_approving", REVIEWER, ReviewUiState(running = ReviewAction.Approve))

    @Test
    fun confirmAll() = sheet("task_review_confirm_all", "Approve all remaining?") {
        ConfirmAllContent(ReviewStep.ConfirmAll(stages = 3, submit = false), ReviewSheetActions())
    }

    @Test
    fun chooseScope() = sheet("task_review_choose_scope", "What do you want to approve?", heightDp = 520) {
        ChooseScopeContent(ReviewStep.ChooseScope(stages = 2, submit = false)) {}
    }

    @Test
    fun chooseScopeSubmit() = sheet("task_review_choose_scope_submit", "What do you want to submit?", heightDp = 520) {
        ChooseScopeContent(ReviewStep.ChooseScope(stages = 3, submit = true)) {}
    }

    @Test
    fun drafts() = sheet("task_review_drafts", "Unfinished forms") {
        DraftsContent(ReviewStep.Drafts(listOf("Home visit note", "Risk screen")), ReviewSheetActions())
    }

    @Test
    fun drive() = sheet("task_review_drive", "Where should this be filed?") {
        DriveContent(ReviewStep.DriveSection(LocalDate.of(2026, 3, 8)), TODAY, ReviewSheetActions())
    }

    @Test
    fun related() = sheet("task_review_related", "A related task is still open") {
        RelatedContent(ReviewStep.Related(blocked = false, related = RELATED), ReviewSheetActions())
    }

    @Test
    fun blocked() = sheet("task_review_blocked", "This task can't be completed yet") {
        RelatedContent(ReviewStep.Related(blocked = true, related = RELATED.take(1)), ReviewSheetActions())
    }

    @Test
    fun closureMerge() = sheet("task_review_closure", "Finish the employee file", heightDp = 1300) {
        ClosureFields(
            ClosureForm(CLOSURE, merge = true, picked = setOf("a", "b"), showErrors = true),
            TODAY,
            ClosureActions(),
        )
    }

    @Test
    fun requirementsWithWarnings() = sheet("task_review_requirements", "Before you approve", heightDp = 1300) {
        RequirementFields(
            RequirementsForm(
                requirements = listOf(PLAN),
                values = mapOf(5 to mapOf("cpocDate" to "2027-01-05", "cpocEndDate" to "2027-06-30")),
                warnings = mapOf(
                    5 to mapOf(
                        "cpocDate" to RequirementIssue(IssueKind.ExpectedStart, LocalDate.of(2027, 1, 1)),
                        "cpocEndDate" to RequirementIssue(IssueKind.ExpectedEnd, LocalDate.of(2028, 1, 4)),
                    ),
                ),
                confirmWarnings = true,
            ),
            ReviewAction.Approve,
            TODAY,
            RequirementActions(),
        )
    }

    @Test
    fun lateReason() = sheet("task_review_late_reason", "Why is this late?", heightDp = 1400) {
        LateReasonFields(
            LateForm(
                reasons = Loadable.Ready(REASONS),
                reasonId = 1,
                explanation = "Participant was in the hospital Nov 10 to 15.",
                evidence = PreparedFile("/tmp/d.pdf", "Discharge summary.pdf", "application/pdf", 320_000),
                due = LocalDate.of(2026, 11, 1),
            ),
            TODAY,
            LateActions(),
            onChoose = {},
            onSource = {},
        )
    }

    @Test
    fun lateReasonReturned() = sheet("task_review_late_returned", "Why is this late?", heightDp = 1400) {
        LateReasonFields(
            LateForm(
                reasons = Loadable.Ready(REASONS),
                reasonId = 1,
                explanation = "In the hospital.",
                existingEvidenceId = 12,
                returned = true,
                correction = "Add the discharge date.",
                due = LocalDate.of(2026, 11, 1),
                showErrors = true,
            ),
            TODAY,
            LateActions(),
            onChoose = {},
            onSource = {},
        )
    }

    @Test
    fun lateReview() = sheet("task_review_late_review", "Late reason", heightDp = 1400) {
        LateReviewFields(
            LateCheck(
                LateContext(
                    canSubmit = false,
                    canReview = true,
                    reviewedByMe = false,
                    satisfied = true,
                    justification = LateJustification(
                        id = 81,
                        reasonId = 1,
                        reasonName = "Participant unavailable",
                        explanation = "Participant was in the hospital Nov 10 to 15 and couldn't meet. " +
                            "We rescheduled the visit for the first day back and the family agreed.",
                        evidenceId = 12,
                        delayId = null,
                        submittedBy = "Chris Patel",
                        returned = false,
                    ),
                    suggestedDelay = null,
                    reviews = listOf(
                        LateReview("Priya Shah", "Supervisor", agreed = true, note = "Confirmed with the family."),
                    ),
                ),
            ),
            LateActions(),
        )
    }

    @Test
    fun sendBack() = sheet("task_review_send_back", "Send back for changes", heightDp = 1200) {
        SendBackFields(
            SendBackForm(
                options = Loadable.Ready(OPTIONS),
                approvalId = 11,
                feedback = "Goals 2 and 3 are last year's.",
                minutes = "15",
            ),
            SendBackActions(),
            onChoose = {},
        )
    }

    @Test
    fun sendBackMissing() = sheet("task_review_send_back_errors", "Send back for changes", heightDp = 1200) {
        SendBackFields(
            SendBackForm(
                options = Loadable.Ready(OPTIONS),
                showErrors = true,
            ),
            SendBackActions(),
            onChoose = {
            },
        )
    }

    @Test
    fun returnLateReason() = sheet("task_review_return_late", "Return the late reason", heightDp = 1200) {
        SendBackFields(
            SendBackForm(
                options = Loadable.Ready(OPTIONS.take(1)),
                approvalId = 11,
                lateReasonId = 81,
            ),
            SendBackActions(),
            onChoose = {
            },
        )
    }

    private fun bar(name: String, access: TaskReviewAccess, state: ReviewUiState = ReviewUiState()) =
        captureScreenshots(name) {
            Column(Modifier.background(WaiveraTheme.colors.surface).padding(WaiveraSpacing.Medium)) {
                ReviewButtons(access, state, ReviewBarActions())
            }
        }

    private fun sheet(name: String, title: String, heightDp: Int = 1000, content: @Composable ColumnScope.() -> Unit) =
        captureScreenshots(name, heightDp = heightDp) {
            WaiveraSheetFrame(title = title) { content() }
        }

    private companion object {
        val TODAY: LocalDate = LocalDate.of(2026, 11, 4)
        val REVIEWER = TaskReviewAccess(
            canSubmit = false,
            canApprove = true,
            canApproveAll = true,
            remainingStages = 2,
            canReject = true,
            ready = true,
        )
        val STEPS = listOf(
            WorkflowStep(1, "Assignee", StepStatus.Completed, "Chris Patel"),
            WorkflowStep(2, "Supervisor", StepStatus.Active, null),
            WorkflowStep(3, "Director", StepStatus.Future, null),
        )
        val RELATED = listOf(
            RelatedTask(31, "HEA-988", "Annual assessment", "IN PROGRESS", "Depends on"),
            RelatedTask(32, "HEA-990", "Signed service plan for the annual eligibility review", "NOT STARTED", null),
        )
        val CLOSURE = EmployeeClosure(
            needsExpiration = true,
            nextExpiration = null,
            canMerge = true,
            pdfs = listOf(
                ClosurePdf("a", "CPR card.pdf", 220_000),
                ClosurePdf("b", "Training certificate.pdf", 410_000),
                ClosurePdf("c", "Background check.pdf", 130_000),
            ),
        )
        val PLAN = StageRequirement(
            actionId = 5,
            key = "profile",
            actionType = StageRequirement.PLAN_DATES,
            title = "Update plan dates",
            description = "Enter the new plan year for this participant.",
            fields = listOf(
                RequirementField("cpocDate", "CPOC start date", FieldType.Date, required = true, defaultValue = null),
                RequirementField("cpocEndDate", "CPOC end date", FieldType.Date, required = true, defaultValue = null),
            ),
        )
        val REASONS = listOf(
            ChangeReason(1, "Participant unavailable", "The participant couldn't meet.", requiresEvidence = true),
            ChangeReason(2, "Waiting on outside agency", null, requiresEvidence = false),
        )
        val OPTIONS = listOf(SendBackOption(11, "Chris Patel", "Case manager"), SendBackOption(12, "Priya Shah", null))
    }
}
