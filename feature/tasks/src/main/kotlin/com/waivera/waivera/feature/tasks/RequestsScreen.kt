package com.waivera.waivera.feature.tasks

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.waivera.waivera.core.data.model.RequestKind
import com.waivera.waivera.core.data.model.RequestStatus
import com.waivera.waivera.core.designsystem.component.BadgeTone
import com.waivera.waivera.core.designsystem.component.SkeletonBlock
import com.waivera.waivera.core.designsystem.component.SkeletonText
import com.waivera.waivera.core.designsystem.component.WaiveraAvatar
import com.waivera.waivera.core.designsystem.component.WaiveraBottomSheet
import com.waivera.waivera.core.designsystem.component.WaiveraButton
import com.waivera.waivera.core.designsystem.component.WaiveraButtonSize
import com.waivera.waivera.core.designsystem.component.WaiveraButtonVariant
import com.waivera.waivera.core.designsystem.component.WaiveraConfirmationSheet
import com.waivera.waivera.core.designsystem.component.WaiveraErrorState
import com.waivera.waivera.core.designsystem.component.WaiveraIconTile
import com.waivera.waivera.core.designsystem.component.WaiveraLoadSwap
import com.waivera.waivera.core.designsystem.component.WaiveraPressable
import com.waivera.waivera.core.designsystem.component.WaiveraPullToRefreshBox
import com.waivera.waivera.core.designsystem.component.WaiveraSectionHeader
import com.waivera.waivera.core.designsystem.component.WaiveraSnackbarHost
import com.waivera.waivera.core.designsystem.component.WaiveraStatusBadge
import com.waivera.waivera.core.designsystem.component.WaiveraTileTone
import com.waivera.waivera.core.designsystem.component.WaiveraTopBar
import com.waivera.waivera.core.designsystem.component.rememberSkeletonPulse
import com.waivera.waivera.core.designsystem.component.rememberSkeletonVisible
import com.waivera.waivera.core.designsystem.icon.WaiveraIcons
import com.waivera.waivera.core.designsystem.theme.PressDepth
import com.waivera.waivera.core.designsystem.theme.WaiveraElevation
import com.waivera.waivera.core.designsystem.theme.WaiveraShapes
import com.waivera.waivera.core.designsystem.theme.WaiveraSizes
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTextStyles
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.core.designsystem.theme.isLargeFontScale

/** The Requests segment of the Tasks tab. */
@Composable
fun RequestsScreen(onOpenTask: (Int) -> Unit, modifier: Modifier = Modifier) {
    val viewModel: RequestsViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val uriHandler = LocalUriHandler.current
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            val message = when (effect) {
                is RequestsEffect.Decided -> resources.getString(
                    if (effect.approve) {
                        R.string.feature_tasks_decided_approved
                    } else {
                        R.string.feature_tasks_decided_rejected
                    },
                    effect.requester,
                )
                is RequestsEffect.DecideFailed -> resources.getString(
                    if (effect.approve) {
                        R.string.feature_tasks_decide_failed_approve
                    } else {
                        R.string.feature_tasks_decide_failed_reject
                    },
                )
                RequestsEffect.AlreadyDecided -> resources.getString(R.string.feature_tasks_already_decided)
                RequestsEffect.WebUnavailable -> resources.getString(R.string.feature_tasks_web_unavailable)
                is RequestsEffect.OpenWeb -> {
                    val opened = runCatching { uriHandler.openUri(effect.url) }.isSuccess
                    if (opened) null else resources.getString(R.string.feature_tasks_web_failed)
                }
            }
            message?.let { snackbar.showSnackbar(it) }
        }
    }
    RequestsContent(
        state = state,
        snackbar = snackbar,
        actions = RequestsActions(
            onRefresh = viewModel::refresh,
            onRetryWaiting = viewModel::retryWaiting,
            onRetryMine = viewModel::retryMine,
            onReview = viewModel::review,
            onOpenTask = onOpenTask,
            onOpenWeb = viewModel::openWeb,
        ),
        modifier = modifier,
    )
    state.reviewing?.let { item ->
        WaiveraBottomSheet(onDismissRequest = viewModel::closeReview, title = item.reviewTitle()) {
            ReviewFields(
                item = item,
                onApprove = { viewModel.ask(approve = true) },
                onReject = { viewModel.ask(approve = false) },
                onOpenTask = item.request.taskId?.let { id ->
                    {
                        viewModel.closeReview()
                        onOpenTask(id)
                    }
                },
            )
        }
    }
    state.confirming?.let { decision ->
        val copy = decision.confirmCopy()
        WaiveraConfirmationSheet(
            title = copy.title,
            message = copy.message,
            confirmLabel = stringResource(
                if (decision.approve) R.string.feature_tasks_approve else R.string.feature_tasks_reject,
            ),
            onConfirm = viewModel::confirm,
            onDismiss = viewModel::cancelConfirm,
            destructive = !decision.approve,
            icon = if (decision.approve) WaiveraIcons.Check else WaiveraIcons.Close,
        )
    }
}

/** Requests on a page of its own, opened from a notification or from Today's Needs you. */
@Composable
fun RequestsPage(onBack: () -> Unit, onOpenTask: (Int) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().background(WaiveraTheme.colors.background)) {
        WaiveraTopBar(title = stringResource(R.string.feature_tasks_segment_requests), onBack = onBack)
        RequestsScreen(onOpenTask = onOpenTask, modifier = Modifier.weight(1f))
    }
}

internal data class RequestsActions(
    val onRefresh: () -> Unit = {},
    val onRetryWaiting: () -> Unit = {},
    val onRetryMine: () -> Unit = {},
    val onReview: (RequestItem) -> Unit = {},
    val onOpenTask: (Int) -> Unit = {},
    val onOpenWeb: () -> Unit = {},
)

@Composable
internal fun RequestsContent(
    state: RequestsUiState,
    snackbar: SnackbarHostState,
    actions: RequestsActions,
    modifier: Modifier = Modifier,
) {
    val refreshLabel = stringResource(R.string.feature_tasks_requests_refresh)
    val loading = state.waiting is Loadable.Loading && state.mine is Loadable.Loading
    Box(modifier.fillMaxSize()) {
        WaiveraPullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = actions.onRefresh,
            modifier = Modifier
                .fillMaxSize()
                .semantics {
                    customActions = listOf(
                        CustomAccessibilityAction(refreshLabel) {
                            actions.onRefresh()
                            true
                        },
                    )
                },
        ) {
            WaiveraLoadSwap(loading = loading, modifier = Modifier.fillMaxSize()) { isLoading ->
                if (isLoading) {
                    if (rememberSkeletonVisible(loading = true)) RequestsSkeleton()
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = WaiveraSpacing.Gutter,
                            end = WaiveraSpacing.Gutter,
                            top = WaiveraSpacing.Small,
                            bottom = WaiveraSpacing.Large,
                        ),
                        verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.Small),
                    ) {
                        requestSection(
                            key = "waiting",
                            title = R.string.feature_tasks_requests_waiting,
                            items = state.waiting,
                            empty = R.string.feature_tasks_requests_none_waiting,
                            error = R.string.feature_tasks_requests_waiting_error,
                            onRetry = actions.onRetryWaiting,
                        ) { item -> RequestCard(item, waiting = true, onClick = { actions.onReview(item) }) }
                        requestSection(
                            key = "mine",
                            title = R.string.feature_tasks_requests_mine,
                            items = state.mine,
                            empty = R.string.feature_tasks_requests_none_mine,
                            error = R.string.feature_tasks_requests_mine_error,
                            onRetry = actions.onRetryMine,
                        ) { item ->
                            RequestCard(
                                item = item,
                                waiting = false,
                                onClick = item.request.taskId?.let { id -> { actions.onOpenTask(id) } }
                                    ?: actions.onOpenWeb,
                            )
                        }
                        item(key = "web") {
                            WaiveraButton(
                                text = stringResource(R.string.feature_tasks_requests_open_web),
                                onClick = actions.onOpenWeb,
                                variant = WaiveraButtonVariant.Text,
                                leadingIcon = WaiveraIcons.OpenExternal,
                                fullWidth = true,
                                modifier = Modifier.padding(top = WaiveraSpacing.Small),
                            )
                            InfoNote(
                                text = stringResource(R.string.feature_tasks_requests_web_note),
                                modifier = Modifier.padding(horizontal = WaiveraSpacing.ExtraSmall),
                            )
                        }
                    }
                }
            }
        }
        WaiveraSnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = WaiveraSpacing.Small),
        )
    }
}

@Suppress("LongParameterList")
private fun LazyListScope.requestSection(
    key: String,
    title: Int,
    items: Loadable<List<RequestItem>>,
    empty: Int,
    error: Int,
    onRetry: () -> Unit,
    card: @Composable (RequestItem) -> Unit,
) {
    item(key = "$key-title") {
        val count = (items as? Loadable.Ready)?.value?.size?.takeIf { key == "waiting" && it > 0 }
        WaiveraSectionHeader(
            title = stringResource(title),
            count = count,
            modifier = Modifier.padding(top = WaiveraSpacing.Small).animateItem(),
        )
    }
    when (items) {
        Loadable.Loading -> item(key = "$key-loading") { RequestCardSkeleton() }
        Loadable.Failed -> item(key = "$key-error") {
            WaiveraErrorState(message = stringResource(error), onRetry = onRetry, dense = true)
        }
        is Loadable.Ready -> if (items.value.isEmpty()) {
            item(key = "$key-empty") {
                Text(
                    text = stringResource(empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = WaiveraTheme.colors.textSecondary,
                    modifier = Modifier.padding(horizontal = WaiveraSpacing.ExtraSmall).animateItem(),
                )
            }
        } else {
            // Approving or rejecting removes the card at once; animateItem closes the gap it leaves.
            items(items.value, key = { "$key-${it.id}" }) { item ->
                Box(Modifier.animateItem()) { card(item) }
            }
        }
    }
}

/**
 * One layout for every request card: who (or what) on the left, a kicker with the kind and when,
 * the person or the change, and the task. Your own requests carry their status.
 */
@Composable
private fun RequestCard(item: RequestItem, waiting: Boolean, onClick: () -> Unit) {
    val colors = WaiveraTheme.colors
    val large = isLargeFontScale()
    val request = item.request
    val kind = request.kindLabel()
    val asked = item.asked?.text()
    WaiveraPressable(
        depth = PressDepth.Card,
        onClick = onClick,
        onClickLabel = stringResource(
            if (request.kind.reviewableHere ||
                !waiting
            ) {
                R.string.feature_tasks_request_review
            } else {
                R.string.feature_tasks_request_open_web
            },
        ),
        shape = WaiveraShapes.SheetCard,
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation = WaiveraElevation.Card, shape = WaiveraShapes.SheetCard, clip = false)
            .background(colors.surface, WaiveraShapes.SheetCard)
            .then(
                if (colors.isDark) {
                    Modifier.border(WaiveraSpacing.Hairline, colors.border, WaiveraShapes.SheetCard)
                } else {
                    Modifier
                },
            ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(WaiveraSpacing.MediumSmall),
            horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.MediumSmall),
            verticalAlignment = Alignment.Top,
        ) {
            if (waiting) {
                WaiveraAvatar(name = request.requesterName, size = WaiveraSizes.AvatarMedium, contentDescription = null)
            } else {
                WaiveraIconTile(
                    icon = request.kind.icon(),
                    tone = WaiveraTileTone.Brand,
                    size = WaiveraSizes.AvatarMedium,
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.XxSmall)) {
                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(color = if (waiting) colors.warningText else colors.accent)) {
                            append(kind.uppercase())
                        }
                        if (asked != null) {
                            append(if (large) "\n" else KICKER_SEPARATOR)
                            append(asked.uppercase())
                        }
                    },
                    style = WaiveraTextStyles.Kicker,
                    color = colors.textSecondary,
                )
                Text(
                    text = if (waiting) request.requesterName else item.change.text() ?: kind,
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.text,
                )
                if (waiting) {
                    item.change.text()?.let {
                        Text(text = it, style = MaterialTheme.typography.bodyMedium, color = colors.text)
                    }
                }
                request.taskTitle?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                        maxLines = if (large) LARGE_TASK_LINES else 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (!waiting) {
                    WaiveraStatusBadge(
                        text = request.status.label(),
                        tone = when (request.status) {
                            RequestStatus.Approved -> BadgeTone.Success
                            RequestStatus.Rejected -> BadgeTone.Danger
                            RequestStatus.Pending -> BadgeTone.Warning
                            RequestStatus.Other -> BadgeTone.Neutral
                        },
                        modifier = Modifier.padding(top = WaiveraSpacing.XxSmall),
                    )
                }
            }
            Icon(
                painter = painterResource(
                    if (waiting &&
                        !request.kind.reviewableHere
                    ) {
                        WaiveraIcons.OpenExternal
                    } else {
                        WaiveraIcons.ChevronRight
                    },
                ),
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier.align(Alignment.CenterVertically),
            )
        }
    }
}

/** The review: who asked and when, what changes, why, the task, then Approve and Reject. */
@Composable
internal fun ReviewFields(item: RequestItem, onApprove: () -> Unit, onReject: () -> Unit, onOpenTask: (() -> Unit)?) {
    val colors = WaiveraTheme.colors
    val request = item.request
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.MediumSmall),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.MediumSmall),
        ) {
            WaiveraAvatar(name = request.requesterName, size = WaiveraSizes.AvatarMedium, contentDescription = null)
            Column(Modifier.weight(1f)) {
                Text(request.requesterName, style = MaterialTheme.typography.titleSmall, color = colors.text)
                item.asked?.let {
                    Text(
                        text = stringResource(R.string.feature_tasks_review_requested, it.text().lowercaseFirstWord()),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                    )
                }
            }
        }
        SheetGroup {
            ChangeRow(item)
            request.reason?.takeIf { it.isNotBlank() }?.let { reason ->
                InfoDivider()
                InfoRow(stringResource(request.kind.reasonLabel()), WaiveraIcons.Hash) { InfoValue(reason) }
            }
            request.explanation?.takeIf { it.isNotBlank() }?.let { note ->
                InfoDivider()
                InfoRow(stringResource(R.string.feature_tasks_review_note), WaiveraIcons.Chat) {
                    Text(note, style = MaterialTheme.typography.bodyMedium, color = colors.text)
                }
            }
        }
        request.taskTitle?.let { title ->
            SheetGroup {
                InfoRow(
                    label = stringResource(R.string.feature_tasks_review_task),
                    icon = R.drawable.feature_tasks_ic_task,
                    onClick = onOpenTask,
                    onClickLabel = stringResource(R.string.feature_tasks_open_task),
                ) {
                    InfoValue(title)
                    if (item.due != DueText.None) InfoNote(item.due.text())
                    if (request.isTrainingTask) InfoNote(stringResource(R.string.feature_tasks_review_training))
                }
            }
        }
        Spacer(Modifier.height(WaiveraSpacing.ExtraSmall))
        WaiveraButton(
            text = stringResource(R.string.feature_tasks_approve),
            onClick = onApprove,
            leadingIcon = WaiveraIcons.Check,
            fullWidth = true,
            size = WaiveraButtonSize.Large,
        )
        WaiveraButton(
            text = stringResource(R.string.feature_tasks_reject),
            onClick = onReject,
            leadingIcon = WaiveraIcons.Close,
            variant = WaiveraButtonVariant.DestructiveOutline,
            fullWidth = true,
            size = WaiveraButtonSize.Large,
        )
    }
}

@Composable
private fun ChangeRow(item: RequestItem) {
    val colors = WaiveraTheme.colors
    when (val change = item.change) {
        is RequestChange.DueMove -> InfoRow(
            stringResource(R.string.feature_tasks_review_due_change),
            WaiveraIcons.Schedule,
        ) {
            InfoValue(change.text().orEmpty())
            change.daysLater?.let {
                Text(
                    text = pluralStringResource(R.plurals.feature_tasks_days_later, it, it),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.warningText,
                )
            }
        }
        is RequestChange.DelayWindow -> InfoRow(
            stringResource(R.string.feature_tasks_review_delay_window),
            WaiveraIcons.Warning,
            tone = WaiveraTileTone.Amber,
        ) {
            InfoValue(change.text().orEmpty())
        }
        RequestChange.Closure -> InfoRow(stringResource(R.string.feature_tasks_review_closure), WaiveraIcons.Archive) {
            InfoValue(stringResource(R.string.feature_tasks_change_closure))
        }
        RequestChange.Other -> InfoRow(item.request.kindLabel(), item.request.kind.icon()) {
            InfoValue(item.request.typeName)
        }
    }
}

private data class ConfirmCopy(val title: String, val message: String)

@Composable
private fun Decision.confirmCopy(): ConfirmCopy {
    val request = item.request
    val task = request.taskTitle ?: stringResource(R.string.feature_tasks_this_task)
    val name = request.requesterName
    return when (request.kind) {
        RequestKind.MoreTime -> {
            val move = item.change as? RequestChange.DueMove
            val from = move?.from
            ConfirmCopy(
                title = stringResource(
                    if (approve) {
                        R.string.feature_tasks_confirm_approve_title_more_time
                    } else {
                        R.string.feature_tasks_confirm_reject_title_more_time
                    },
                ),
                message = when {
                    approve && move != null && from != null ->
                        stringResource(R.string.feature_tasks_confirm_approve_more_time, task, move.to, from, name)
                    approve && move != null ->
                        stringResource(R.string.feature_tasks_confirm_approve_more_time_short, task, move.to, name)
                    from != null -> stringResource(R.string.feature_tasks_confirm_reject_more_time, task, from, name)
                    else -> stringResource(R.string.feature_tasks_confirm_reject_more_time_short, task, name)
                },
            )
        }
        RequestKind.Delay -> {
            val window = item.change as? RequestChange.DelayWindow
            ConfirmCopy(
                title = stringResource(
                    if (approve) {
                        R.string.feature_tasks_confirm_approve_title_delay
                    } else {
                        R.string.feature_tasks_confirm_reject_title_delay
                    },
                ),
                message = if (approve && window != null) {
                    stringResource(R.string.feature_tasks_confirm_approve_delay, window.start, window.end, task, name)
                } else {
                    stringResource(R.string.feature_tasks_confirm_reject_delay, task, name)
                },
            )
        }
        RequestKind.Closure, RequestKind.Other -> ConfirmCopy(
            title = stringResource(
                if (approve) {
                    R.string.feature_tasks_confirm_approve_title_closure
                } else {
                    R.string.feature_tasks_confirm_reject_title_closure
                },
            ),
            message = stringResource(
                if (approve) {
                    R.string.feature_tasks_confirm_approve_closure
                } else {
                    R.string.feature_tasks_confirm_reject_closure
                },
                task,
                name,
            ),
        )
    }
}

@Composable
private fun RequestItem.reviewTitle(): String = stringResource(
    when (request.kind) {
        RequestKind.MoreTime -> R.string.feature_tasks_review_title_more_time
        RequestKind.Delay -> R.string.feature_tasks_review_title_delay
        RequestKind.Closure, RequestKind.Other -> R.string.feature_tasks_review_title_closure
    },
)

@Composable
internal fun com.waivera.waivera.core.data.model.ChangeRequest.kindLabel(): String = when (kind) {
    RequestKind.MoreTime -> stringResource(R.string.feature_tasks_kind_more_time)
    RequestKind.Delay -> stringResource(R.string.feature_tasks_kind_delay)
    RequestKind.Closure -> stringResource(R.string.feature_tasks_kind_closure)
    RequestKind.Other -> typeName
}

@DrawableRes
internal fun RequestKind.icon(): Int = when (this) {
    RequestKind.MoreTime -> WaiveraIcons.Schedule
    RequestKind.Delay -> WaiveraIcons.Warning
    RequestKind.Closure -> WaiveraIcons.Archive
    RequestKind.Other -> WaiveraIcons.Calendar
}

private fun RequestKind.reasonLabel(): Int = when (this) {
    RequestKind.Delay -> R.string.feature_tasks_review_delay_reason
    RequestKind.Closure -> R.string.feature_tasks_review_closure_reason
    RequestKind.MoreTime, RequestKind.Other -> R.string.feature_tasks_review_reason_code
}

@Composable
private fun RequestStatus.label(): String = when (this) {
    RequestStatus.Pending, RequestStatus.Other -> stringResource(R.string.feature_tasks_status_pending)
    RequestStatus.Approved -> stringResource(R.string.feature_tasks_status_approved)
    RequestStatus.Rejected -> stringResource(R.string.feature_tasks_status_rejected)
}

@Composable
internal fun RequestChange.text(): String? = when (this) {
    is RequestChange.DueMove -> if (from != null) {
        stringResource(R.string.feature_tasks_change_due_move, from, to)
    } else {
        stringResource(R.string.feature_tasks_change_due_to, to)
    }
    is RequestChange.DelayWindow -> stringResource(R.string.feature_tasks_change_delay, start, end)
    RequestChange.Closure -> stringResource(R.string.feature_tasks_change_closure)
    RequestChange.Other -> null
}

/** "Today, 9:12 AM" reads "today, 9:12 AM" after "Requested". */
private fun String.lowercaseFirstWord(): String = replaceFirstChar { it.lowercaseChar() }

@Composable
private fun RequestCardSkeleton() {
    val pulse = rememberSkeletonPulse()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(WaiveraTheme.colors.surface, WaiveraShapes.SheetCard)
            .padding(WaiveraSpacing.MediumSmall),
        horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.MediumSmall),
    ) {
        SkeletonBlock(modifier = Modifier.size(WaiveraSizes.AvatarMedium), shape = WaiveraShapes.Tile, pulse = pulse)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.ExtraSmall)) {
            SkeletonText(style = WaiveraTextStyles.Kicker, widthFraction = SKELETON_KICKER, pulse = pulse)
            SkeletonText(style = MaterialTheme.typography.titleSmall, widthFraction = SKELETON_TITLE, pulse = pulse)
        }
    }
}

@Composable
private fun RequestsSkeleton() {
    val loading = stringResource(R.string.feature_tasks_requests_loading)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = WaiveraSpacing.Gutter, vertical = WaiveraSpacing.Medium)
            .semantics { contentDescription = loading },
        verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.Small),
    ) {
        repeat(SKELETON_CARDS) { RequestCardSkeleton() }
    }
}

private const val KICKER_SEPARATOR = "  ·  "
private const val LARGE_TASK_LINES = 3
private const val SKELETON_CARDS = 3
private const val SKELETON_KICKER = 0.4f
private const val SKELETON_TITLE = 0.7f
