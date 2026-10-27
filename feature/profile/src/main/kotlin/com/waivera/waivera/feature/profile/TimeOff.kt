package com.waivera.waivera.feature.profile

import androidx.annotation.StringRes
import com.waivera.waivera.core.data.model.TimeOffBalance
import com.waivera.waivera.core.data.model.TimeOffRequest
import com.waivera.waivera.core.data.model.TimeOffSummary
import com.waivera.waivera.core.designsystem.component.BadgeTone

/** Status chip for one time-off request; the first matching rule wins (spec 08 §3). */
internal enum class TimeOffStatus(@StringRes val label: Int, val tone: BadgeTone) {
    CancellationPending(R.string.feature_profile_time_off_cancellation_pending, BadgeTone.Warning),
    Rejected(R.string.feature_profile_time_off_rejected, BadgeTone.Danger),
    Approved(R.string.feature_profile_time_off_approved, BadgeTone.Success),
    Pending(R.string.feature_profile_time_off_pending, BadgeTone.Warning),
    ;

    companion object {
        fun of(request: TimeOffRequest): TimeOffStatus = when {
            request.isCancellationPending -> CancellationPending
            request.isRejected -> Rejected
            request.isApproved -> Approved
            else -> Pending
        }
    }
}

internal object TimeOffMath {
    /** The profile lists this many requests and says how many more exist. */
    const val VISIBLE_REQUESTS = 3
    private const val FULL = 100.0

    /** The remaining share for the progress bar: `100 - clamp(usedPercentage, 0, 100)`, as 0..1. */
    fun remainingFraction(usedPercentage: Double): Float =
        ((FULL - usedPercentage.coerceIn(0.0, FULL)) / FULL).toFloat()

    /**
     * Used share of the allocation. The API sends `usedLeavePercentage`, which core does not map
     * yet (docs/requests/feature-profile.md), so it is derived from used and allocated days.
     */
    fun usedPercentage(balance: TimeOffBalance): Double? {
        val allocated = balance.allocated?.takeIf { it > 0 } ?: return null
        val used = balance.usedDays ?: balance.remainingDays?.let { allocated - it } ?: return null
        return used * FULL / allocated
    }

    /** The card is omitted entirely when there is nothing to show. */
    fun hasContent(summary: TimeOffSummary): Boolean = summary.balance != null || summary.requests.isNotEmpty()

    fun hiddenCount(summary: TimeOffSummary): Int = (summary.requests.size - VISIBLE_REQUESTS).coerceAtLeast(0)
}
