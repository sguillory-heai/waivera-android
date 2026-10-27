package com.waivera.waivera.feature.today

import com.waivera.waivera.core.data.repository.CalendarRepository
import com.waivera.waivera.feature.today.model.TodayLocalState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Today's one-tap invitation answers (UI2-23). The row hides at once and the snackbar offers Undo;
 * nothing is sent until the same window as the event screen closes. A send runs in [appScope] so
 * leaving Today cannot cancel it, and an answer still waiting when Today goes away is sent anyway.
 */
internal class InvitationAnswers(
    private val scope: CoroutineScope,
    private val appScope: CoroutineScope,
    private val calendar: CalendarRepository,
    private val local: MutableStateFlow<TodayLocalState>,
    private val effects: SendChannel<TodayEffect>,
) {
    private val pending = mutableMapOf<String, PendingAnswer>()
    private var undoToken = 0L

    fun respond(eventId: String, accept: Boolean) {
        if (eventId in pending) return
        hide(eventId)
        undoToken += 1
        val token = undoToken
        val job = scope.launch {
            delay(UNDO_WINDOW_MILLIS)
            commit(eventId, accept)
        }
        pending[eventId] = PendingAnswer(accept, token, job)
        effects.trySend(TodayEffect.OfferUndo(eventId, accept, token))
    }

    /** Undo tapped: the answer was never sent, so the invitation simply comes back. */
    fun undo(eventId: String, token: Long) {
        val answer = pending[eventId]?.takeIf { it.token == token } ?: return
        answer.job.cancel()
        pending -= eventId
        show(eventId)
    }

    /** The Undo snackbar went away without Undo: send now rather than waiting out the timer. */
    fun sendNow(eventId: String, token: Long) {
        val answer = pending[eventId]?.takeIf { it.token == token } ?: return
        answer.job.cancel()
        scope.launch { commit(eventId, answer.accept) }
    }

    /** Retry after a failed send repeats a decision already made, so it goes straight out. */
    fun retry(eventId: String, accept: Boolean) {
        if (eventId in pending) return
        hide(eventId)
        scope.launch { commit(eventId, accept) }
    }

    /** The screen is going away: answers still inside their window are the person's decision. */
    fun flush() {
        pending.forEach { (eventId, answer) ->
            appScope.launch { calendar.respondToInvitation(eventId, answer.accept) }
        }
        pending.clear()
    }

    private suspend fun commit(eventId: String, accept: Boolean) {
        pending -= eventId
        appScope.async { calendar.respondToInvitation(eventId, accept) }.await()
            .onFailure {
                show(eventId)
                effects.send(TodayEffect.InvitationFailed(eventId, accept))
            }
    }

    private fun hide(eventId: String) = local.update { it.copy(answeredInvitations = it.answeredInvitations + eventId) }

    private fun show(eventId: String) = local.update { it.copy(answeredInvitations = it.answeredInvitations - eventId) }

    private class PendingAnswer(val accept: Boolean, val token: Long, val job: Job)

    companion object {
        /** Matches `SnackbarDuration.Long`, so Undo is offered for the whole window (as on the event screen). */
        const val UNDO_WINDOW_MILLIS = 10_000L
    }
}
