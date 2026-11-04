package com.waivera.waivera.core.data.model

import java.time.Instant

/** One of the agency's leave types, with the person's balance for it when it keeps one. */
data class LeaveType(
    val id: Int,
    val name: String,
    val tracksBalance: Boolean,
    /** Minutes the person can still request; null when the balance couldn't be read. */
    val availableMinutes: Int?,
)

/**
 * Time off as the person filled it in. A full day runs from the first day's midnight to the last
 * day's end in UTC calendar terms (sprout_web `AdminRequestModal`); a partial day is two instants
 * on one day in the person's saved zone.
 */
data class LeaveDraft(
    val leaveTypeId: Int,
    val partialDay: Boolean,
    val start: Instant,
    val end: Instant,
    val note: String?,
)

/** Why the server says there is nothing to request (`BuildBlockedPreview` codes). */
enum class LeaveBlock { Weekend, Holiday, Overlap, Other }

/** sprout_api `PreviewRequestAsync`: the hours this asks for and the balance around it. */
data class LeavePreview(
    val requestedMinutes: Int,
    val tracksBalance: Boolean,
    val beforeMinutes: Int?,
    val afterMinutes: Int?,
    /** Set when the dates hold no requestable hours; sending is off. */
    val block: LeaveBlock?,
    val impact: LeaveImpact?,
)

/** Why a send was refused, worded by the app rather than the server. */
enum class LeaveSendProblem { NotEnoughBalance, Probation, Weekend, Holiday, NoWorkdays, Overlap, Other }

/** A refused time-off send; [problem] picks the app's wording. */
class LeaveSendException(val problem: LeaveSendProblem) : Exception()
