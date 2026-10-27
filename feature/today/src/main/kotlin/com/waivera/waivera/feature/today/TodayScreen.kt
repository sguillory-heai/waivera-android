package com.waivera.waivera.feature.today

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.waivera.waivera.core.designsystem.feedback.Haptics
import com.waivera.waivera.core.designsystem.feedback.rememberHaptics
import com.waivera.waivera.core.designsystem.preview.WaiveraPreviews
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.feature.today.ui.TodayCallbacks
import com.waivera.waivera.feature.today.ui.TodayContent
import com.waivera.waivera.feature.today.ui.TodayPreviewStates

/**
 * Entry point for the Today tab. Owned by `:feature:today`; routed by `:app`.
 *
 * @param onOpenMessage opens a message highlighted in its conversation, or its thread when
 *   [threadRootMessageId] is set (activity and mention rows). Without it, rows fall
 *   back to [onOpenConversation].
 */
@Composable
fun TodayScreen(
    onAccountClick: () -> Unit,
    onOpenConversation: (conversationId: Long) -> Unit,
    onOpenSchedule: () -> Unit,
    onOpenEvent: (eventId: String) -> Unit,
    modifier: Modifier = Modifier,
    onOpenMessage: ((conversationId: Long, messageId: Long, threadRootMessageId: Long?) -> Unit)? = null,
) {
    val viewModel: TodayViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    TodayEffects(viewModel, snackbarHostState)
    val openMessage by rememberUpdatedState(onOpenMessage)
    val openConversation by rememberUpdatedState(onOpenConversation)
    val callbacks = remember(viewModel, onAccountClick, onOpenSchedule, onOpenEvent) {
        TodayCallbacks(
            onAction = viewModel::onAction,
            onAccountClick = onAccountClick,
            onOpenConversation = { id -> openConversation(id) },
            onOpenMessage = { conversationId, messageId, rootId ->
                openMessage?.invoke(conversationId, messageId, rootId) ?: openConversation(conversationId)
            },
            onOpenSchedule = onOpenSchedule,
            onOpenEvent = onOpenEvent,
        )
    }
    TodayContent(state = state, callbacks = callbacks, snackbarHostState = snackbarHostState, modifier = modifier)
}

/** Turns one-off results into a snackbar (with Retry where it helps) and the matching haptic. */
@Composable
private fun TodayEffects(viewModel: TodayViewModel, snackbarHostState: SnackbarHostState) {
    val haptics = rememberHaptics()
    val messages = TodayMessages(
        refreshFailed = stringResource(R.string.feature_today_refresh_failed),
        markReadFailed = stringResource(R.string.feature_today_mark_read_failed),
        markAllFailed = stringResource(R.string.feature_today_mark_all_failed),
        accepted = stringResource(R.string.feature_today_invitation_accepted),
        declined = stringResource(R.string.feature_today_invitation_declined),
        invitationFailed = stringResource(R.string.feature_today_invitation_failed),
        retry = stringResource(R.string.feature_today_retry),
        undo = stringResource(R.string.feature_today_undo),
    )
    val currentMessages by rememberUpdatedState(messages)
    LaunchedEffect(viewModel, snackbarHostState) {
        viewModel.effects.collect { effect ->
            handleEffect(effect, currentMessages, haptics, snackbarHostState, viewModel::onAction)
        }
    }
}

private data class TodayMessages(
    val refreshFailed: String,
    val markReadFailed: String,
    val markAllFailed: String,
    val accepted: String,
    val declined: String,
    val invitationFailed: String,
    val retry: String,
    val undo: String,
)

private suspend fun handleEffect(
    effect: TodayEffect,
    messages: TodayMessages,
    haptics: Haptics,
    snackbar: SnackbarHostState,
    onAction: (TodayAction) -> Unit,
) {
    when (effect) {
        is TodayEffect.RefreshFailed ->
            snackbar.showRetry(messages.refreshFailed, messages.retry) { onAction(TodayAction.Refresh) }
        is TodayEffect.MarkReadFailed -> {
            haptics.error()
            snackbar.showRetry(messages.markReadFailed, messages.retry) {
                onAction(TodayAction.MarkActivityRead(effect.activityId))
            }
        }
        TodayEffect.MarkAllReadFailed -> {
            haptics.error()
            snackbar.showRetry(messages.markAllFailed, messages.retry) { onAction(TodayAction.MarkAllActivityRead) }
        }
        TodayEffect.MarkedAllRead -> haptics.success()
        is TodayEffect.OfferUndo -> {
            haptics.success()
            val result = snackbar.showSnackbar(
                message = if (effect.accept) messages.accepted else messages.declined,
                actionLabel = messages.undo,
                duration = SnackbarDuration.Long,
            )
            onAction(
                if (result == SnackbarResult.ActionPerformed) {
                    TodayAction.UndoInvitation(effect.eventId, effect.token)
                } else {
                    TodayAction.InvitationUndoClosed(effect.eventId, effect.token)
                },
            )
        }
        is TodayEffect.InvitationFailed -> {
            haptics.error()
            snackbar.showRetry(messages.invitationFailed, messages.retry) {
                onAction(TodayAction.RetryInvitation(effect.eventId, effect.accept))
            }
        }
    }
}

private suspend fun SnackbarHostState.showRetry(message: String, retry: String, onRetry: () -> Unit) {
    val result = showSnackbar(message = message, actionLabel = retry, duration = SnackbarDuration.Long)
    if (result == SnackbarResult.ActionPerformed) onRetry()
}

@WaiveraPreviews
@Composable
private fun TodayContentPreview() {
    WaiveraTheme {
        TodayContent(state = TodayPreviewStates.populated(), callbacks = TodayPreviewStates.callbacks())
    }
}

@WaiveraPreviews
@Composable
private fun TodayCaughtUpPreview() {
    WaiveraTheme {
        TodayContent(state = TodayPreviewStates.caughtUp(), callbacks = TodayPreviewStates.callbacks())
    }
}
