package com.waivera.waivera.feature.tasks

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalContext
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
import androidx.core.net.toUri
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
import com.waivera.waivera.core.designsystem.component.WaiveraCountTone
import com.waivera.waivera.core.designsystem.component.WaiveraEmptyState
import com.waivera.waivera.core.designsystem.component.WaiveraErrorState
import com.waivera.waivera.core.designsystem.component.WaiveraIconTile
import com.waivera.waivera.core.designsystem.component.WaiveraIllustration
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
fun RequestsScreen(
    onOpenTask: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onOpenMine: () -> Unit = {},
    mode: RequestsMode = RequestsMode.Waiting,
) {
    val viewModel: RequestsViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
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
                RequestsEffect.CertificateFailed -> resources.getString(
                    R.string.feature_tasks_review_certificate_failed,
                )
                is RequestsEffect.OpenCertificate -> {
                    val view = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(effect.uri.toUri(), PDF_MIME_TYPE)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    val opened = try {
                        context.startActivity(view)
                        true
                    } catch (_: ActivityNotFoundException) {
                        false
                    }
                    if (opened) null else resources.getString(R.string.feature_tasks_review_certificate_failed)
                }
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
            onOpenMine = onOpenMine,
        ),
        modifier = modifier,
        mode = mode,
    )
    val certificateName = stringResource(R.string.feature_tasks_review_certificate_file)
    state.reviewing?.let { item ->
        WaiveraBottomSheet(onDismissRequest = viewModel::closeReview, title = item.reviewTitle()) {
            ReviewFields(
                item = item,
                extras = ReviewExtras(
                    balance = state.balance,
                    training = state.training,
                    openingCertificate = state.openingCertificate,
                ),
                actions = ReviewActions(
                    onApprove = { viewModel.ask(approve = true) },
                    onReject = { viewModel.ask(approve = false) },
                    onOpenTask = item.request.taskId?.let { id ->
                        {
                            viewModel.closeReview()
                            onOpenTask(id)
                        }
                    },
                    onRetry = viewModel::retryDetails,
                    onOpenCertificate = { viewModel.openCertificate(certificateName) },
                    onOpenWeb = viewModel::openWeb,
                ),
            )
        }
    }
    state.confirming?.let { decision ->
        val copy = decision.confirmCopy()
        val credit = (state.training as? Loadable.Ready)?.value
        if (decision.item.request.kind == RequestKind.TrainingCredit && credit != null) {
            TrainingDecisionSheet(
                title = copy.title,
                message = copy.message,
                approve = decision.approve,
                credit = credit,
                reason = state.reason,
                canConfirm = viewModel.canConfirm(state),
                onReason = viewModel::setReason,
                onConfirm = viewModel::confirm,
                onDismiss = viewModel::cancelConfirm,
            )
        } else {
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
}

/** Requests on a page of its own, opened from a notification or from Today's Needs you. */
@Composable
fun RequestsPage(
    onBack: () -> Unit,
    onOpenTask: (Int) -> Unit,
    onOpenMine: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().background(WaiveraTheme.colors.background)) {
        WaiveraTopBar(title = stringResource(R.string.feature_tasks_segment_requests), onBack = onBack)
        RequestsScreen(onOpenTask = onOpenTask, onOpenMine = onOpenMine, modifier = Modifier.weight(1f))
    }
}

/** The person's own requests and where each stands, opened from the Requests tab. */
@Composable
fun MyRequestsPage(onBack: () -> Unit, onOpenTask: (Int) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().background(WaiveraTheme.colors.background)) {
        WaiveraTopBar(title = stringResource(R.string.feature_tasks_requests_mine), onBack = onBack)
        RequestsScreen(onOpenTask = onOpenTask, mode = RequestsMode.Mine, modifier = Modifier.weight(1f))
    }
}

internal data class RequestsActions(
    val onRefresh: () -> Unit = {},
    val onRetryWaiting: () -> Unit = {},
    val onRetryMine: () -> Unit = {},
    val onReview: (RequestItem) -> Unit = {},
    val onOpenTask: (Int) -> Unit = {},
    val onOpenWeb: () -> Unit = {},
    val onOpenMine: () -> Unit = {},
)

/** Which list the screen shows: requests waiting on the person (the tab), or their own (a page). */
enum class RequestsMode { Waiting, Mine }

@Composable
internal fun RequestsContent(
    state: RequestsUiState,
    snackbar: SnackbarHostState,
    actions: RequestsActions,
    modifier: Modifier = Modifier,
    mode: RequestsMode = RequestsMode.Waiting,
) {
    val refreshLabel = stringResource(R.string.feature_tasks_requests_refresh)
    val shown = if (mode == RequestsMode.Waiting) state.waiting else state.mine
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
            WaiveraLoadSwap(loading = shown is Loadable.Loading, modifier = Modifier.fillMaxSize()) { isLoading ->
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
                        if (mode == RequestsMode.Waiting) waitingItems(state, actions) else mineItems(state, actions)
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

/**
 * Only what needs the person's decision. Their own requests are one quiet row after the last
 * card, or a small button when nothing is waiting, so they never read as more work to review.
 */
private fun LazyListScope.waitingItems(state: RequestsUiState, actions: RequestsActions) {
    when (val waiting = state.waiting) {
        Loadable.Loading -> Unit
        Loadable.Failed -> item(key = "waiting-error") {
            WaiveraErrorState(
                message = stringResource(R.string.feature_tasks_requests_waiting_error),
                onRetry = actions.onRetryWaiting,
                dense = true,
            )
        }
        is Loadable.Ready -> {
            if (waiting.value.isEmpty()) {
                item(key = "caught-up") {
                    CaughtUp(onOpenMine = actions.onOpenMine, modifier = Modifier.animateItem())
                }
                return
            }
            item(key = "waiting-title") {
                WaiveraSectionHeader(
                    title = stringResource(R.string.feature_tasks_requests_waiting),
                    count = waiting.value.size,
                    modifier = Modifier.padding(top = WaiveraSpacing.Small).animateItem(),
                )
            }
            // Approving or rejecting removes the card at once; animateItem closes the gap it leaves.
            items(waiting.value, key = { "waiting-${it.id}" }) { item ->
                Box(Modifier.animateItem()) {
                    RequestCard(item, waiting = true, onClick = { actions.onReview(item) })
                }
            }
        }
    }
    item(key = "mine-row") {
        MineRow(mine = state.mine, onClick = actions.onOpenMine, modifier = Modifier.animateItem())
    }
    webFooter(actions.onOpenWeb)
}

/** The person's own requests: still waiting first, then decided. */
private fun LazyListScope.mineItems(state: RequestsUiState, actions: RequestsActions) {
    when (val mine = state.mine) {
        Loadable.Loading -> Unit
        Loadable.Failed -> item(key = "mine-error") {
            WaiveraErrorState(
                message = stringResource(R.string.feature_tasks_requests_mine_error),
                onRetry = actions.onRetryMine,
                dense = true,
            )
        }
        is Loadable.Ready -> if (mine.value.isEmpty()) {
            item(key = "mine-empty") {
                WaiveraEmptyState(
                    title = stringResource(R.string.feature_tasks_requests_mine_empty_title),
                    message = stringResource(R.string.feature_tasks_requests_mine_empty_message),
                    illustration = WaiveraIllustration.Search,
                    modifier = Modifier.padding(top = WaiveraSpacing.Large),
                )
            }
        } else {
            val (pending, decided) = mine.value.partition { it.request.status == RequestStatus.Pending }
            mineGroup(MineGroup.Pending, pending, actions, first = true)
            mineGroup(MineGroup.Decided, decided, actions, first = pending.isEmpty())
            item(key = "mine-note") {
                InfoNote(
                    text = stringResource(R.string.feature_tasks_requests_mine_note),
                    modifier = Modifier.padding(top = WaiveraSpacing.Small, start = WaiveraSpacing.ExtraSmall),
                )
            }
        }
    }
}

private enum class MineGroup(val key: String, @StringRes val title: Int) {
    Pending("pending", R.string.feature_tasks_requests_pending),
    Decided("decided", R.string.feature_tasks_requests_decided),
}

private fun LazyListScope.mineGroup(
    group: MineGroup,
    rows: List<RequestItem>,
    actions: RequestsActions,
    first: Boolean,
) {
    if (rows.isEmpty()) return
    item(key = "${group.key}-title") {
        WaiveraSectionHeader(
            title = stringResource(group.title),
            count = rows.size.takeIf { group == MineGroup.Pending },
            countTone = WaiveraCountTone.Info,
            modifier = Modifier
                .padding(top = if (first) WaiveraSpacing.Small else WaiveraSpacing.Medium)
                .animateItem(),
        )
    }
    items(rows, key = { "${group.key}-${it.id}" }) { item ->
        Box(Modifier.animateItem()) {
            RequestCard(
                item = item,
                waiting = false,
                onClick = item.request.taskId?.let { id -> { actions.onOpenTask(id) } } ?: actions.onOpenWeb,
            )
        }
    }
}

private fun LazyListScope.webFooter(onOpenWeb: () -> Unit) {
    item(key = "web") {
        WaiveraButton(
            text = stringResource(R.string.feature_tasks_requests_open_web),
            onClick = onOpenWeb,
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

/** Nothing to decide: the same reward art as an empty inbox, and a small way to your own. */
@Composable
private fun CaughtUp(onOpenMine: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.Small),
        modifier = modifier.fillMaxWidth(),
    ) {
        WaiveraEmptyState(
            title = stringResource(R.string.feature_tasks_requests_caught_up_title),
            message = stringResource(R.string.feature_tasks_requests_caught_up_message),
            illustration = WaiveraIllustration.CaughtUp,
            modifier = Modifier.padding(top = WaiveraSpacing.Large),
        )
        WaiveraButton(
            text = stringResource(R.string.feature_tasks_requests_view_mine),
            onClick = onOpenMine,
            variant = WaiveraButtonVariant.Tonal,
            size = WaiveraButtonSize.Compact,
        )
    }
}

/** One quiet row after the waiting cards: the person's own requests, and how many still wait. */
@Composable
private fun MineRow(mine: Loadable<List<RequestItem>>, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = WaiveraTheme.colors
    val summary = (mine as? Loadable.Ready)?.value?.let { rows -> mineSummary(rows) }
    WaiveraPressable(
        depth = PressDepth.Card,
        onClick = onClick,
        shape = WaiveraShapes.SheetCard,
        modifier = modifier
            .fillMaxWidth()
            .padding(top = WaiveraSpacing.Medium)
            .background(colors.surfaceMuted, WaiveraShapes.SheetCard),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(WaiveraSpacing.MediumSmall),
            horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.MediumSmall),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WaiveraIconTile(icon = WaiveraIcons.Schedule, tone = WaiveraTileTone.Brand)
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.feature_tasks_requests_mine),
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.text,
                )
                summary?.let {
                    Text(text = it, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                }
            }
            Icon(
                painter = painterResource(WaiveraIcons.ChevronRight),
                contentDescription = null,
                tint = colors.textSecondary,
            )
        }
    }
}

@Composable
private fun mineSummary(rows: List<RequestItem>): String {
    val pending = rows.count { it.request.status == RequestStatus.Pending }
    val total = pluralStringResource(R.plurals.feature_tasks_requests_mine_total, rows.size, rows.size)
    return when {
        rows.isEmpty() -> stringResource(R.string.feature_tasks_requests_mine_none)
        pending == 0 -> total
        else -> stringResource(
            R.string.feature_tasks_requests_mine_summary,
            pluralStringResource(R.plurals.feature_tasks_requests_mine_pending, pending, pending),
            total,
        )
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
        RequestKind.TimeOff, RequestKind.TimeOffCancellation -> timeOffCopy()
        RequestKind.ProfileChange -> ConfirmCopy(
            title = stringResource(
                if (approve) {
                    R.string.feature_tasks_confirm_approve_title_profile
                } else {
                    R.string.feature_tasks_confirm_reject_title_profile
                },
            ),
            message = if (approve) {
                val count = (item.change as? RequestChange.Profile)?.changed?.size ?: 0
                pluralStringResource(R.plurals.feature_tasks_confirm_approve_profile, count, count)
            } else {
                stringResource(R.string.feature_tasks_confirm_reject_profile, name)
            },
        )
        RequestKind.Reactivation -> ConfirmCopy(
            title = stringResource(
                if (approve) {
                    R.string.feature_tasks_confirm_approve_title_reopen
                } else {
                    R.string.feature_tasks_confirm_reject_title_reopen
                },
            ),
            message = stringResource(
                if (approve) {
                    R.string.feature_tasks_confirm_approve_reopen
                } else {
                    R.string.feature_tasks_confirm_reject_reopen
                },
                task,
                name,
            ),
        )
        RequestKind.AttachmentRemoval -> ConfirmCopy(
            title = stringResource(
                if (approve) {
                    R.string.feature_tasks_confirm_approve_title_remove_file
                } else {
                    R.string.feature_tasks_confirm_reject_title_remove_file
                },
            ),
            message = stringResource(
                if (approve) {
                    R.string.feature_tasks_confirm_approve_remove_file
                } else {
                    R.string.feature_tasks_confirm_reject_remove_file
                },
                task,
                name,
            ),
        )
        RequestKind.TrainingCredit -> ConfirmCopy(
            title = stringResource(
                if (approve) {
                    R.string.feature_tasks_confirm_approve_title_training
                } else {
                    R.string.feature_tasks_confirm_reject_title_training
                },
            ),
            message = if (approve) {
                stringResource(R.string.feature_tasks_confirm_approve_training, name)
            } else {
                stringResource(R.string.feature_tasks_confirm_reject_training)
            },
        )
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
private fun Decision.timeOffCopy(): ConfirmCopy {
    val change = item.change as? RequestChange.TimeOff
    val name = item.request.requesterName
    val dates = change?.dates?.let { spanText(it) }.orEmpty()
    val cancel = item.request.kind == RequestKind.TimeOffCancellation
    return ConfirmCopy(
        title = stringResource(
            when {
                cancel && approve -> R.string.feature_tasks_confirm_approve_title_cancel
                cancel -> R.string.feature_tasks_confirm_reject_title_cancel
                approve -> R.string.feature_tasks_confirm_approve_title_time_off
                else -> R.string.feature_tasks_confirm_reject_title_time_off
            },
        ),
        message = stringResource(
            when {
                cancel && approve -> R.string.feature_tasks_confirm_approve_cancel
                cancel -> R.string.feature_tasks_confirm_reject_cancel
                approve -> R.string.feature_tasks_confirm_approve_time_off
                else -> R.string.feature_tasks_confirm_reject_time_off
            },
            name,
            dates,
        ),
    )
}

@Composable
private fun RequestItem.reviewTitle(): String = when (request.kind) {
    RequestKind.MoreTime -> stringResource(R.string.feature_tasks_review_title_more_time)
    RequestKind.Delay -> stringResource(R.string.feature_tasks_review_title_delay)
    RequestKind.Closure -> stringResource(R.string.feature_tasks_review_title_closure)
    else -> request.kindLabel()
}

@Composable
internal fun com.waivera.waivera.core.data.model.ChangeRequest.kindLabel(): String = when (kind) {
    RequestKind.MoreTime -> stringResource(R.string.feature_tasks_kind_more_time)
    RequestKind.Delay -> stringResource(R.string.feature_tasks_kind_delay)
    RequestKind.Closure -> stringResource(R.string.feature_tasks_kind_closure)
    RequestKind.TimeOff -> stringResource(R.string.feature_tasks_kind_time_off)
    RequestKind.TimeOffCancellation -> stringResource(R.string.feature_tasks_kind_time_off_cancel)
    RequestKind.ProfileChange -> stringResource(R.string.feature_tasks_kind_profile)
    RequestKind.Reactivation -> stringResource(R.string.feature_tasks_kind_reopen)
    RequestKind.AttachmentRemoval -> stringResource(R.string.feature_tasks_kind_remove_file)
    RequestKind.TrainingCredit -> stringResource(R.string.feature_tasks_kind_training)
    RequestKind.Other -> typeName
}

@DrawableRes
internal fun RequestKind.icon(): Int = when (this) {
    RequestKind.MoreTime -> WaiveraIcons.Schedule
    RequestKind.Delay -> WaiveraIcons.Warning
    RequestKind.Closure -> WaiveraIcons.Archive
    RequestKind.TimeOff, RequestKind.TimeOffCancellation, RequestKind.Other -> WaiveraIcons.Calendar
    RequestKind.ProfileChange -> WaiveraIcons.Person
    RequestKind.Reactivation -> WaiveraIcons.Refresh
    RequestKind.AttachmentRemoval -> WaiveraIcons.Attachment
    RequestKind.TrainingCredit -> WaiveraIcons.Award
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
    is RequestChange.TimeOff -> if (leaveType.isEmpty()) {
        spanText(dates)
    } else {
        stringResource(R.string.feature_tasks_change_time_off, leaveType, spanText(dates))
    }
    is RequestChange.Profile -> if (changed.isEmpty()) {
        null
    } else {
        pluralStringResource(R.plurals.feature_tasks_review_changed, changed.size, changed.size)
    }
    RequestChange.Reopen -> stringResource(R.string.feature_tasks_change_reopen)
    RequestChange.RemoveFile -> stringResource(R.string.feature_tasks_change_remove_file)
    RequestChange.Training, RequestChange.Other -> null
}

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

private const val PDF_MIME_TYPE = "application/pdf"
