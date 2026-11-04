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

    /** Time off, profile changes, reactivations and anything newer: reviewed on the web. */
    Other,
    ;

    /** The phone reviews only the task requests the web reviews with approve and reject alone. */
    val reviewableHere: Boolean get() = this != Other

    companion object {
        fun of(typeName: String?): RequestKind = when (typeName?.trim()?.lowercase()) {
            "task extension request" -> MoreTime
            "task delay request" -> Delay
            "task deletion request" -> Closure
            else -> Other
        }
    }
}

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
    val loaded: Boolean = false,
)
