package com.waivera.waivera.feature.calendar.event

import androidx.compose.runtime.Immutable
import com.waivera.waivera.core.data.model.MyInvitation
import com.waivera.waivera.core.data.model.ScheduleEvent
import com.waivera.waivera.core.network.dto.InviteStatus
import com.waivera.waivera.core.network.error.WaiveraError
import com.waivera.waivera.feature.calendar.model.Celebration
import com.waivera.waivera.feature.calendar.model.EventTiming
import com.waivera.waivera.feature.calendar.model.ZoneNotice
import java.time.Instant
import java.time.ZoneId

@Immutable
sealed interface EventUiState {
    data object Loading : EventUiState

    data object NotFound : EventUiState

    data class Error(val error: WaiveraError) : EventUiState

    data class Loaded(val details: EventDetails) : EventUiState
}

/** The event as the detail screen shows it, already placed in [zone] (the employee's saved zone). */
@Immutable
data class EventDetails(
    val event: ScheduleEvent,
    val zone: ZoneId,
    val zoneNotice: ZoneNotice?,
    val timing: EventTiming,
    val currentYear: Int,
    /** True when the signed-in employee owns the event (backend id match only). */
    val isOrganizer: Boolean,
    val organizer: PersonRow?,
    val attendees: List<AttendeeRow>,
    val invitation: InvitationUi?,
    val join: JoinUi?,
    val chat: ChatAction?,
    val isRefreshing: Boolean,
    /** A refresh failed; cached details are shown with a quiet banner. */
    val refreshError: WaiveraError?,
)

/** The organizer block: tapping opens their profile when they are a known employee. */
@Immutable
data class PersonRow(val userId: String?, val name: String?, val celebration: Celebration?)

@Immutable
data class AttendeeRow(
    val id: String,
    /** Backend user id for employees, so the row can open their profile; null otherwise. */
    val userId: String?,
    val name: String?,
    val email: String?,
    val status: InviteStatus,
    /** Only computed for employees (07 §7: non-employee attendees get no milestone badge). */
    val celebration: Celebration?,
    val isMe: Boolean,
)

/**
 * My answer to the invitation. [response] is what to show now, including an optimistic choice
 * that is waiting out its undo window or being saved. Buttons show only while [canChange].
 */
@Immutable
data class InvitationUi(
    val response: MyInvitation,
    val canChange: Boolean,
    val phase: ResponsePhase,
    /** The last save failed; the screen offers Retry for [failedChoiceAccept]. */
    val failure: WaiveraError?,
    val failedChoiceAccept: Boolean?,
)

enum class ResponsePhase {
    /** Nothing in flight; [InvitationUi.response] is the server's answer. */
    IDLE,

    /** Chosen locally; Undo is still possible and nothing has been sent. */
    UNDO_WINDOW,

    /** Sent; waiting for the server. */
    SAVING,

    /** The server accepted it. */
    SAVED,
}

@Immutable
data class JoinUi(val state: JoinState, val opensAt: Instant?, val isBusy: Boolean, val error: JoinError?)

enum class JoinState { OPEN, TOO_EARLY, ENDED, NO_TIME }

@Immutable
sealed interface JoinError {
    /** Neither the meeting details nor the location hold an http(s) link (07 §7). */
    data object NoLink : JoinError

    /** The lobby could not be created or opened. */
    data class Failed(val error: WaiveraError?) : JoinError
}

@Immutable
data class ChatAction(val kind: ChatActionKind, val isBusy: Boolean)

enum class ChatActionKind { MEETING_CHAT, MESSAGE_ORGANIZER }

/** One-off effects the screen performs. */
sealed interface EventEffect {
    /** An http(s) URL, validated before it is emitted. */
    data class OpenUrl(val url: String) : EventEffect

    data class OpenConversation(val conversationId: Long) : EventEffect

    /**
     * Show "[You accepted|You declined] · Undo". Pass [token] back to Undo and to the dismissal
     * so a snackbar replaced by a newer choice cannot commit or undo the newer one.
     */
    data class OfferUndo(val accept: Boolean, val token: Long) : EventEffect

    data class ResponseSaved(val accept: Boolean) : EventEffect

    data object ResponseFailed : EventEffect

    data class ChatFailed(val error: WaiveraError) : EventEffect
}
