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

    /** Sends a failed answer again straight away; the decision was already made. */
    data class RetryInvitation(val eventId: String, val accept: Boolean) : TodayAction

    /** Undo on the answer's snackbar; [token] ties it to that snackbar. */
    data class UndoInvitation(val eventId: String, val token: Long) : TodayAction

    /** The answer's snackbar went away without Undo, so the answer can go out now. */
    data class InvitationUndoClosed(val eventId: String, val token: Long) : TodayAction

    /** "Send wishes" on a celebration: opens the direct conversation with [userId]. */
    data class SendWishes(val userId: String) : TodayAction
}

/** One-off results the screen turns into a snackbar (never a vibration). */
sealed interface TodayEffect {
    /** A refresh failed; cached data stays on screen. */
    data class RefreshFailed(val error: WaiveraError) : TodayEffect

    /** The row went back to unread; Retry sends the mark-read again. */
    data class MarkReadFailed(val activityId: Long) : TodayEffect

    data object MarkAllReadFailed : TodayEffect

    /** The invitation is answered on screen; the snackbar offers Undo until the answer is sent. */
    data class OfferUndo(val eventId: String, val accept: Boolean, val token: Long) : TodayEffect

    /** The invitation is back in Needs you; Retry sends the same answer again. */
    data class InvitationFailed(val eventId: String, val accept: Boolean) : TodayEffect

    /** The direct conversation for "Send wishes" is ready: open it. */
    data class OpenConversation(val conversationId: Long) : TodayEffect

    /** The conversation couldn't be opened; Retry tries "Send wishes" again. */
    data class WishesFailed(val userId: String) : TodayEffect
}
