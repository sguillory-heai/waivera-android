package com.waivera.waivera.core.data.model

import java.time.Instant
import java.time.LocalDate

/** What a change request asks for, by the server's request type name. */
enum class RequestKind {
    /** "Task Extension Request": a later due date. */
    MoreTime,

    /** "Task Delay Request": known lateness is recorded; the due date stays. */
    Delay,

    /** "Task Deletion Request": the task closes without being completed. */
    Closure,

    /** "Time-Off Request". */
    TimeOff,

    /** "Time-Off Cancellation": approved time off handed back. */
    TimeOffCancellation,

    /** "Profile Change Request": new details for a participant. */
    ProfileChange,

    /** "Task Reactivation": a closed task opens again. */
    Reactivation,

    /** "Required Attachment Removal Request": a required file is no longer needed. */
    AttachmentRemoval,

    /** "External Training Credit": a certificate from training outside Waivera. */
    TrainingCredit,

    /** Anything newer than this app: reviewed on the web. */
    Other,
    ;

    /** Every kind the server's approval inbox returns is reviewed on the phone, as on the web. */
    val reviewableHere: Boolean get() = this != Other

    val isTimeOff: Boolean get() = this == TimeOff || this == TimeOffCancellation

    companion object {
        fun of(typeName: String?): RequestKind = when (typeName?.trim()?.lowercase()) {
            "task extension request" -> MoreTime
            "task delay request" -> Delay
            "task deletion request" -> Closure
            "time-off request" -> TimeOff
            "time-off cancellation" -> TimeOffCancellation
            "profile change request" -> ProfileChange
            "task reactivation" -> Reactivation
            "required attachment removal request" -> AttachmentRemoval
            "external training credit" -> TrainingCredit
            else -> Other
        }
    }
}

/**
 * The time off a request asks for or hands back. Full days are literal dates; a partial day is
 * a pair of instants told in the agency's leave zone.
 */
data class TimeOffReview(
    val leaveType: String,
    val leaveTypeId: Int,
    val partialDay: Boolean,
    val startDate: LocalDate?,
    val endDate: LocalDate?,
    val start: Instant?,
    val end: Instant?,
    val minutes: Int,
    val note: String?,
    /** The task review saved with the request, refreshed by the server when it can. */
    val impact: LeaveImpact?,
    val impactChanged: Boolean,
    /** Null when the request carries no task review at all. */
    val acknowledged: Boolean?,
)

/** sprout_web `LeaveImpactSummary`: open tasks due around the leave and the notices that matter. */
data class LeaveImpact(
    val dueBefore: Int,
    val dueDuring: Int,
    val shortNotice: Boolean,
    /** Sick leave over three working days; the agency may ask for documentation. */
    val extendedSick: Boolean = false,
)

/** The employee's balance for one leave type; [tracksBalance] false needs none. */
data class LeaveBalance(val leaveTypeId: Int, val tracksBalance: Boolean, val availableMinutes: Int)

/** One participant field in a profile change, as the web compares them. */
data class ProfileField(
    val field: Field,
    /** Text as entered; dates as `YYYY-MM-DD`; SSN digits are already masked. */
    val previous: String?,
    val requested: String?,
    val changed: Boolean,
) {
    /** The web's order. SSN digits never reach the screen. */
    enum class Field {
        DateOfBirth,
        FirstName,
        LastName,
        Email,
        Mobile,
        Waiver,
        WaiverId,
        Ssn,
        Certification,
        CpocStart,
        CpocEnd,
    }
}

/** An external training certificate under review (`training/external/requests/{id}`). */
data class TrainingCredit(
    val requestId: Int,
    val course: String,
    val employeeName: String,
    val trainer: String?,
    val provider: String?,
    val completed: LocalDate?,
    val hours: Double,
    val pending: Boolean,
    /** Needed to decide; a stale one is refused by the server. */
    val revision: Int?,
    val canApprove: Boolean,
    /** The reviewer uploaded it for the employee, so approving needs a reason too. */
    val needsApproveReason: Boolean,
    val uploadedBy: String?,
    val blocked: TrainingBlock?,
    /** Reviewed on the web's training page instead. */
    val reviewOnWeb: Boolean,
) {
    val decidable: Boolean get() = pending && canApprove && revision != null && !reviewOnWeb
}

/** What a training decision sends besides yes or no. */
data class TrainingDecision(val revision: Int, val reason: String)

enum class TrainingBlock { OwnCredit, NoReviewer, NotReviewer }

enum class RequestStatus {
    Pending,
    Approved,
    Rejected,
    Other,
    ;

    companion object {
        fun of(name: String?): RequestStatus = when (name?.trim()?.uppercase()) {
            "PENDING" -> Pending
            "APPROVED" -> Approved
            "REJECTED", "DECLINED" -> Rejected
            else -> Other
        }
    }
}

/**
 * One change request. Dates the request moves ([currentDue], [requestedDue], the delay period)
 * are literal calendar dates; [requestedAt] and [decidedAt] are UTC instants.
 */
data class ChangeRequest(
    val id: Int,
    val kind: RequestKind,
    /** The server's type name, shown for kinds the phone does not review. */
    val typeName: String,
    val status: RequestStatus,
    val requesterId: String?,
    val requesterName: String,
    val approverName: String?,
    val requestedAt: Instant?,
    val decidedAt: Instant?,
    val taskId: Int?,
    val taskTitle: String?,
    /** The picked explanation code (more time, delay) or closure reason. */
    val reason: String?,
    /** What the requester wrote. */
    val explanation: String?,
    val currentDue: LocalDate? = null,
    val requestedDue: LocalDate? = null,
    val delayStart: LocalDate? = null,
    val delayEnd: LocalDate? = null,
    val isTrainingTask: Boolean = false,
    val timeOff: TimeOffReview? = null,
    /** Profile changes: every field the web compares, changed first. */
    val profile: List<ProfileField> = emptyList(),
)

/** What the person may ask for on one task, from the server's eligibility check. */
data class ChangeOptions(
    val taskId: Int,
    val currentDue: LocalDate?,
    val canAskMoreTime: Boolean,
    /** The server-owned delay period; null when a delay cannot be reported now. */
    val delay: DelayPeriod?,
    val hasPendingRequest: Boolean,
)

data class DelayPeriod(val start: LocalDate, val end: LocalDate, val renewal: Boolean)

/** A reason the person picks from. [requiresEvidence] needs a file already on the task. */
data class ChangeReason(
    val id: Long,
    val name: String,
    val description: String?,
    val requiresEvidence: Boolean,
    /** Closure reasons named "Other" need a comment, as on the web. */
    val needsComment: Boolean = false,
    /** Fills the explanation when picked, unless something was typed (web `useReasonExplanation`). */
    val preset: String? = null,
)

/** A file attached to the task that a request can cite as evidence. */
data class TaskFile(val attachmentId: Int, val name: String)

/** Whether an asked change took effect at once (an owner's own request) or waits for review. */
enum class RequestOutcome { Approved, Pending }

/**
 * What the person may see and do with change requests, from the same role policies the web
 * checks. The server re-checks every action; these only decide what is offered.
 */
data class RequestAccess(
    val canAskMoreTime: Boolean = false,
    val canAskClosure: Boolean = false,
    /** The Requests list, under the web's Admin Requests rule. */
    val showsRequests: Boolean = false,
    /** Whether casework includes tasks waiting for approval, as the web's task list does. */
    val includePendingApprovals: Boolean = false,
    /** The web's Download permission for case files (participant tasks), `case_file:download`. */
    val canDownloadCaseFiles: Boolean = false,
    /** The same for employee files, `employee_file:download`. */
    val canDownloadEmployeeFiles: Boolean = false,
    /** Asking for time off, `admin_request:create:time_off`. */
    val canRequestTimeOff: Boolean = false,
    val loaded: Boolean = false,
)
