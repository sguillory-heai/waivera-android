package com.waivera.waivera.feature.tasks

import androidx.compose.runtime.Immutable
import com.waivera.waivera.core.data.model.ChangeReason
import com.waivera.waivera.core.data.model.DelayPeriod
import com.waivera.waivera.core.data.model.TaskFile
import java.time.LocalDate

/** The three changes a person can ask for on a task, in the order the chooser offers them. */
enum class AskKind { MoreTime, Delay, Closure }

/**
 * The Ask for a change sheet. [kind] null is the chooser. The rules match the web's forms: a
 * new due date after the current one and within five years, an explanation of at most 4,000
 * characters, and a file already on the task when the reason needs evidence.
 */
@Immutable
data class AskForm(
    val choices: List<AskKind>,
    val kind: AskKind? = null,
    val reasons: Loadable<List<ChangeReason>> = Loadable.Loading,
    val reasonId: Long? = null,
    val newDue: LocalDate? = null,
    val explanation: String = "",
    val files: Loadable<List<TaskFile>> = Loadable.Loading,
    val evidenceId: Int? = null,
    /** The due date the request moves, from the server's eligibility check. */
    val currentDue: LocalDate? = null,
    /** The server-owned delay period; it can't be edited. */
    val delay: DelayPeriod? = null,
    val isSaving: Boolean = false,
    val showErrors: Boolean = false,
    val failed: Boolean = false,
    /** The delay period changed or closed since the sheet opened. */
    val delayChanged: Boolean = false,
) {
    val reason: ChangeReason? get() = (reasons as? Loadable.Ready)?.value?.firstOrNull { it.id == reasonId }
    val evidence: TaskFile? get() = (files as? Loadable.Ready)?.value?.firstOrNull { it.attachmentId == evidenceId }
    val needsEvidence: Boolean get() = kind != AskKind.Closure && reason?.requiresEvidence == true

    val dueMissing: Boolean get() = kind == AskKind.MoreTime && newDue == null
    val dueTooEarly: Boolean
        get() = kind == AskKind.MoreTime && newDue != null && currentDue != null && !newDue.isAfter(currentDue)
    val dueTooLate: Boolean
        get() = kind == AskKind.MoreTime &&
            newDue != null &&
            currentDue != null &&
            newDue.isAfter(currentDue.plusYears(MAX_YEARS))
    val reasonMissing: Boolean get() = reasonId == null
    val explanationMissing: Boolean
        get() = explanation.isBlank() && (kind != AskKind.Closure || reason?.needsComment == true)
    val explanationTooLong: Boolean get() = explanation.length > MAX_EXPLANATION
    val evidenceMissing: Boolean get() = needsEvidence && evidence == null
    val delayMissing: Boolean get() = kind == AskKind.Delay && delay == null

    val isValid: Boolean
        get() = kind != null &&
            !dueMissing &&
            !dueTooEarly &&
            !dueTooLate &&
            !reasonMissing &&
            !explanationMissing &&
            !explanationTooLong &&
            !evidenceMissing &&
            !delayMissing

    companion object {
        const val MAX_EXPLANATION = 4_000
        const val MAX_YEARS = 5L
    }
}
