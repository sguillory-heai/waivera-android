package com.waivera.waivera.feature.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.waivera.waivera.core.designsystem.R as DesignR
import com.waivera.waivera.core.designsystem.component.WaiveraEmptyState
import com.waivera.waivera.core.designsystem.component.WaiveraErrorState
import com.waivera.waivera.core.designsystem.component.WaiveraSnackbarHost
import com.waivera.waivera.core.designsystem.component.WaiveraTopBar
import com.waivera.waivera.core.designsystem.feedback.rememberHaptics
import com.waivera.waivera.core.designsystem.icon.WaiveraIcons
import com.waivera.waivera.core.designsystem.preview.WaiveraPreviews
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.feature.calendar.event.CalendarEventViewModel
import com.waivera.waivera.feature.calendar.event.EventActions
import com.waivera.waivera.feature.calendar.event.EventBody
import com.waivera.waivera.feature.calendar.event.EventBodySkeleton
import com.waivera.waivera.feature.calendar.event.EventEffect
import com.waivera.waivera.feature.calendar.event.EventPreviewData
import com.waivera.waivera.feature.calendar.event.EventUiState
import com.waivera.waivera.feature.calendar.ui.AvatarLoader
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Entry point for the event screen. Owned by `:feature:calendar`; routed by `:app`.
 */
@Composable
fun CalendarEventScreen(
    eventId: String,
    onBack: () -> Unit,
    onOpenConversation: (conversationId: Long) -> Unit,
    onOpenPerson: (userId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = hiltViewModel<CalendarEventViewModel, CalendarEventViewModel.Factory>(
        key = eventId,
        creationCallback = { factory -> factory.create(eventId) },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val uriHandler = LocalUriHandler.current
    val openConversation by rememberUpdatedState(onOpenConversation)
    val acceptedMessage = stringResource(R.string.feature_calendar_undo_accepted)
    val declinedMessage = stringResource(R.string.feature_calendar_undo_declined)
    val undoLabel = stringResource(DesignR.string.core_designsystem_undo)
    val chatFailed = stringResource(R.string.feature_calendar_chat_failed)

    LifecycleResumeEffect(viewModel) {
        viewModel.tick()
        onPauseOrDispose { }
    }
    // Keeps the join window and the invitation buttons current while the screen is open.
    LaunchedEffect(viewModel) {
        while (isActive) {
            delay(CLOCK_TICK_MILLIS)
            viewModel.tick()
        }
    }
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is EventEffect.OpenUrl -> try {
                    uriHandler.openUri(effect.url)
                } catch (_: IllegalArgumentException) {
                    viewModel.onLinkFailed()
                }
                is EventEffect.OpenConversation -> openConversation(effect.conversationId)
                is EventEffect.OfferUndo -> {
                    if (effect.accept) haptics.success() else haptics.selection()
                    scope.launch {
                        snackbarHost.currentSnackbarData?.dismiss()
                        val result = snackbarHost.showSnackbar(
                            message = if (effect.accept) acceptedMessage else declinedMessage,
                            actionLabel = undoLabel,
                            duration = SnackbarDuration.Long,
                        )
                        if (result == SnackbarResult.ActionPerformed) {
                            viewModel.undo(effect.token)
                        } else {
                            viewModel.onUndoWindowClosed(effect.token)
                        }
                    }
                }
                is EventEffect.ResponseSaved -> Unit
                EventEffect.ResponseFailed -> haptics.error()
                is EventEffect.ChatFailed -> {
                    haptics.error()
                    scope.launch { snackbarHost.showSnackbar(chatFailed) }
                }
            }
        }
    }

    EventContent(
        state = state,
        actions = EventActions(
            onBack = onBack,
            onRetry = viewModel::retry,
            onAccept = { viewModel.respond(accept = true) },
            onDecline = { viewModel.respond(accept = false) },
            onRetryResponse = viewModel::retryResponse,
            onJoin = viewModel::join,
            onDismissJoinError = viewModel::dismissJoinError,
            onOpenChat = viewModel::openChat,
            onOpenPerson = onOpenPerson,
        ),
        avatarLoader = remember(viewModel) { AvatarLoader { viewModel.avatar(it) } },
        snackbarHost = snackbarHost,
        modifier = modifier,
    )
}

/** Stateless event screen, for previews and screenshot tests. */
@Composable
fun EventContent(
    state: EventUiState,
    actions: EventActions,
    modifier: Modifier = Modifier,
    avatarLoader: AvatarLoader? = null,
    snackbarHost: SnackbarHostState = remember { SnackbarHostState() },
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(WaiveraTheme.colors.background),
    ) {
        WaiveraTopBar(
            title = stringResource(R.string.feature_calendar_calendar_event_title),
            onBack = actions.onBack,
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            when (state) {
                EventUiState.Loading -> EventBodySkeleton()
                EventUiState.NotFound -> WaiveraEmptyState(
                    title = stringResource(R.string.feature_calendar_event_not_found_title),
                    message = stringResource(R.string.feature_calendar_event_not_found_message),
                    icon = WaiveraIcons.Calendar,
                    actionLabel = stringResource(DesignR.string.core_designsystem_go_back),
                    onAction = actions.onBack,
                    modifier = Modifier.padding(WaiveraSpacing.Gutter),
                )
                is EventUiState.Error -> WaiveraErrorState(
                    message = stringResource(state.error.messageRes),
                    onRetry = actions.onRetry,
                    modifier = Modifier.padding(WaiveraSpacing.Gutter),
                )
                is EventUiState.Loaded -> EventBody(
                    details = state.details,
                    actions = actions,
                    avatarLoader = avatarLoader,
                )
            }
            WaiveraSnackbarHost(
                hostState = snackbarHost,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding(),
            )
        }
    }
}

private const val CLOCK_TICK_MILLIS = 30_000L

@WaiveraPreviews
@Composable
private fun EventContentPreview() {
    WaiveraTheme {
        EventContent(state = EventUiState.Loaded(EventPreviewData.invited()), actions = EventActions())
    }
}

@WaiveraPreviews
@Composable
private fun EventContentNotFoundPreview() {
    WaiveraTheme {
        EventContent(state = EventUiState.NotFound, actions = EventActions())
    }
}
