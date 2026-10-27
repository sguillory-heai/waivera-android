package com.waivera.waivera.feature.today

import com.waivera.waivera.core.network.error.WaiveraError
import com.waivera.waivera.feature.today.model.ActivityFilter

/** Everything the person can do on Today that changes state. Navigation goes through the screen's lambdas. */
sealed interface TodayAction {
    data object Refresh : TodayAction

    data object RetryConnection : TodayAction

    data class MarkActivityRead(val activityId: Long) : TodayAction

    data object MarkAllActivityRead : TodayAction

    data class SetActivityFilter(val filter: ActivityFilter) : TodayAction

    data object ToggleUnreadOnly : TodayAction

    /** Clears the type filter and Unread, from the filtered empty state. */
    data object ShowAllActivity : TodayAction

    data object LoadMoreActivity : TodayAction

    data object ToggleNeedsYouExpanded : TodayAction

    data class RespondToInvitation(val eventId: String, val accept: Boolean) : TodayAction
}

/** One-off results the screen turns into a snackbar and a haptic. */
sealed interface TodayEffect {
    /** A refresh failed; cached data stays on screen. */
    data class RefreshFailed(val error: WaiveraError) : TodayEffect

    /** The row went back to unread; Retry sends the mark-read again. */
    data class MarkReadFailed(val activityId: Long) : TodayEffect

    data object MarkAllReadFailed : TodayEffect

    data object MarkedAllRead : TodayEffect

    data class InvitationAnswered(val accept: Boolean) : TodayEffect

    /** The invitation is back in Needs you; Retry sends the same answer again. */
    data class InvitationFailed(val eventId: String, val accept: Boolean) : TodayEffect
}
