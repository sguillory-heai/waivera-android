package com.waivera.waivera.feature.tasks.review

import androidx.compose.runtime.Immutable
import com.waivera.waivera.core.data.model.ApprovalMode
import com.waivera.waivera.core.data.model.ChangeReason
import com.waivera.waivera.core.data.model.EmployeeClosure
import com.waivera.waivera.core.data.model.LateContext
import com.waivera.waivera.core.data.model.PreparedFile
import com.waivera.waivera.core.data.model.RelatedTask
import com.waivera.waivera.core.data.model.SendBackOption
import com.waivera.waivera.core.data.model.StageRequirement
import com.waivera.waivera.core.data.model.UploadProblem
import com.waivera.waivera.feature.tasks.Loadable
import java.time.LocalDate

/** Which button started the chain; it names the progress and the result. */
enum class ReviewAction { Submit, SubmitAll, Approve, ApproveAll }

/** The "all remaining & complete" form of Submit or Approve. */
internal val ReviewAction.all: ReviewAction
    get() = when (this) {
        ReviewAction.Submit, ReviewAction.SubmitAll -> ReviewAction.SubmitAll
        ReviewAction.Approve, ReviewAction.ApproveAll -> ReviewAction.ApproveAll
    }

internal val ReviewAction.mode: ApprovalMode
    get() = if (this == ReviewAction.SubmitAll || this == ReviewAction.ApproveAll) {
        ApprovalMode.AllRemaining
    } else {
        ApprovalMode.CurrentStage
    }

/**
 * The late reason: sent by the assignee before a late task moves on (web
 * `LateTaskJustificationModalV2`), or corrected by a reviewer as they agree with it
 * ([editingId], web `LateJustificationReviewModal` edit mode).
 */
@Immutable
data class LateForm(
    val reasons: Loadable<List<ChangeReason>> = Loadable.Loading,
    val reasonId: Long? = null,
    val explanation: String = "",
    /** Something was typed, so picking a reason no longer replaces the explanation with its preset. */
    val typed: Boolean = false,
    /** A reported delay's reason and explanation are reused, with its evidence. */
    val delayId: Long? = null,
    val delayEvidenceId: Int? = null,
    /** Evidence already on the reason being corrected. */
    val existingEvidenceId: Int? = null,
    val evidence: PreparedFile? = null,
    val evidenceChecking: Boolean = false,
    val evidenceProblem: UploadProblem? = null,
    /** Returned for correction: the reviewer's feedback, shown above the fields. */
    val returned: Boolean = false,
    val correction: String? = null,
    /** A reviewer's correction of this reason, saved as they agree. */
    val editingId: Long? = null,
    val due: LocalDate? = null,
    val isSaving: Boolean = false,
    val showErrors: Boolean = false,
    val failed: Boolean = false,
) {
    val reason: ChangeReason? get() = (reasons as? Loadable.Ready)?.value?.firstOrNull { it.id == reasonId }
    val keptEvidenceId: Int? get() = if (delayId != null) delayEvidenceId else existingEvidenceId
    val evidenceMissing: Boolean get() = reason?.requiresEvidence == true && evidence == null && keptEvidenceId == null
    val reasonMissing: Boolean get() = reasonId == null
    val explanationMissing: Boolean get() = explanation.isBlank()
    val explanationTooLong: Boolean get() = explanation.length > MAX_EXPLANATION
    val isValid: Boolean
        get() = !reasonMissing && !explanationMissing && !explanationTooLong && !evidenceMissing && !evidenceChecking

    companion object {
        const val MAX_EXPLANATION = 4_000
    }
}

/** The reviewer's look at a submitted late reason: agree, correct it, or send the task back. */
@Immutable
data class LateCheck(
    val context: LateContext,
    val note: String = "",
    val isSaving: Boolean = false,
    val failed: Boolean = false,
) {
    val noteTooLong: Boolean get() = note.length > MAX_NOTE

    companion object {
        const val MAX_NOTE = 2_000
    }
}

/** Send back for changes (web `RejectTaskModal`), or return a late reason for correction ([lateReasonId]). */
@Immutable
data class SendBackForm(
    val options: Loadable<List<SendBackOption>> = Loadable.Loading,
    val approvalId: Int? = null,
    val feedback: String = "",
    val minutes: String = "",
    val lateReasonId: Long? = null,
    val isSaving: Boolean = false,
    val showErrors: Boolean = false,
    val failed: Boolean = false,
) {
    val option: SendBackOption? get() = (options as? Loadable.Ready)?.value?.firstOrNull { it.approvalId == approvalId }
    val noOptions: Boolean get() = (options as? Loadable.Ready)?.value?.isEmpty() == true
    val returnsLateReason: Boolean get() = lateReasonId != null
    val minutesValue: Int? get() = if (minutes.isBlank()) 0 else minutes.toIntOrNull()?.takeIf { it in 0..MAX_MINUTES }
    val optionMissing: Boolean get() = option == null
    val feedbackMissing: Boolean get() = feedback.isBlank()
    val feedbackTooLong: Boolean get() = feedback.length > MAX_FEEDBACK
    val minutesInvalid: Boolean get() = minutesValue == null
    val isValid: Boolean get() = !optionMissing && !feedbackMissing && !feedbackTooLong && !minutesInvalid

    companion object {
        const val MAX_MINUTES = 24 * 60
        const val MAX_FEEDBACK = 2_000
    }
}

/** How an employee task's files are finalized as it completes (web employee closure prompt). */
@Immutable
data class ClosureForm(
    val options: EmployeeClosure,
    val merge: Boolean = false,
    /** The PDFs to merge, in order; every eligible PDF starts picked, as on the web. */
    val order: List<String> = options.pdfs.map { it.id },
    val picked: Set<String> = options.pdfs.map { it.id }.toSet(),
    val expiration: LocalDate? = options.nextExpiration,
    val isSaving: Boolean = false,
    val showErrors: Boolean = false,
    val failed: Boolean = false,
) {
    val tooFewPicked: Boolean get() = merge && picked.size < MIN_MERGE
    val expirationMissing: Boolean get() = options.needsExpiration && expiration == null
    val isValid: Boolean get() = !tooFewPicked && !expirationMissing

    companion object {
        const val MIN_MERGE = 2
    }
}

/**
 * The stage's own requirements (web `WorkflowApprovalRequirementsModal`). [values] are keyed by
 * action id, then field key; dates are ISO `YYYY-MM-DD`. Warnings need a second tap to approve.
 */
@Immutable
data class RequirementsForm(
    val requirements: List<StageRequirement>,
    val values: Map<Int, Map<String, String>>,
    val errors: Map<Int, Map<String, RequirementIssue>> = emptyMap(),
    val warnings: Map<Int, Map<String, RequirementIssue>> = emptyMap(),
    val confirmWarnings: Boolean = false,
    /** The server changed what this stage needs while the sheet was open. */
    val changed: Boolean = false,
    val isSaving: Boolean = false,
    val failed: Boolean = false,
) {
    val supported: Boolean get() = requirements.all { it.supported }
    val hasWarnings: Boolean get() = warnings.values.any { it.isNotEmpty() }
}

/** A field problem, worded by the sheet. [date] is the expected date some warnings name. */
@Immutable
data class RequirementIssue(val kind: IssueKind, val date: LocalDate? = null)

enum class IssueKind { Required, InvalidDate, EndBeforeStart, OverAYearAgo, ExpectedStart, ExpectedEnd }

/** One sheet of the chain at a time; null is no sheet. */
@Immutable
sealed interface ReviewStep {
    data class LateReason(val form: LateForm) : ReviewStep

    data class LateReview(val check: LateCheck) : ReviewStep

    data class ConfirmAll(val stages: Int, val submit: Boolean) : ReviewStep

    /** Approve (or Submit) tapped by someone who may also approve every remaining stage. */
    data class ChooseScope(val stages: Int, val submit: Boolean) : ReviewStep

    data class Drafts(val names: List<String>) : ReviewStep

    data class DriveSection(val previous: LocalDate, val useOriginal: Boolean = true) : ReviewStep

    data class Closure(val form: ClosureForm) : ReviewStep

    data class Requirements(val form: RequirementsForm) : ReviewStep

    /** A related task holds this one up; [blocked] means it can't complete at all yet. */
    data class Related(val blocked: Boolean, val related: List<RelatedTask>, val isSaving: Boolean = false) : ReviewStep

    data class SendBack(val form: SendBackForm) : ReviewStep
}

@Immutable
data class ReviewUiState(
    val step: ReviewStep? = null,
    /** Which button is working through the chain, for its progress label. */
    val running: ReviewAction? = null,
    val rejecting: Boolean = false,
) {
    /** The open sheet is sending its answer, as opposed to waiting for the person. */
    val stepSaving: Boolean
        get() = when (val step = step) {
            is ReviewStep.Requirements -> step.form.isSaving
            is ReviewStep.Closure -> step.form.isSaving
            is ReviewStep.LateReason -> step.form.isSaving
            is ReviewStep.LateReview -> step.check.isSaving
            is ReviewStep.Related -> step.isSaving
            is ReviewStep.SendBack -> step.form.isSaving
            else -> false
        }
}
