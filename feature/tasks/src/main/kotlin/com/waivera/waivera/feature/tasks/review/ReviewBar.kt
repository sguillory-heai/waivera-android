package com.waivera.waivera.feature.tasks.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.waivera.waivera.core.data.model.TaskReviewAccess
import com.waivera.waivera.core.designsystem.component.WaiveraButton
import com.waivera.waivera.core.designsystem.component.WaiveraButtonSize
import com.waivera.waivera.core.designsystem.component.WaiveraButtonVariant
import com.waivera.waivera.core.designsystem.icon.WaiveraIcons
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.core.designsystem.theme.isLargeFontScale
import com.waivera.waivera.feature.tasks.R

internal data class ReviewBarActions(
    val onAction: (ReviewAction) -> Unit = {},
    val onReject: () -> Unit = {},
    /** Approve or Submit for someone who may also approve every remaining stage. */
    val onAskScope: (ReviewAction) -> Unit = {},
)

/**
 * The task's review buttons, from the server's capabilities only (web TaskPage): the assignee's
 * Submit, or a reviewer's Approve and Reject. "All remaining & complete" is asked in a sheet after
 * Approve or Submit, for those allowed it, so the bar stays two buttons. Submit and Approve wait
 * for every required form, file and signature; Reject never does.
 */
@Composable
internal fun ReviewButtons(access: TaskReviewAccess, state: ReviewUiState, actions: ReviewBarActions) {
    val busy = state.running != null || state.rejecting
    // Behind a sheet that waits for an answer the button just stays disabled: nothing is sent yet.
    val progress = when {
        state.step == null -> stringResource(R.string.feature_tasks_review_checking)
        state.stepSaving -> stringResource(R.string.feature_tasks_review_sending)
        else -> null
    }

    // "All" runs under the button that asked for it.
    fun loading(action: ReviewAction) = progress?.takeIf { state.running == action || state.running == action.all }

    val start = { action: ReviewAction ->
        if (access.canApproveAll) actions.onAskScope(action) else actions.onAction(action)
    }
    Column(verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.Small)) {
        if (access.canSubmit) {
            WaiveraButton(
                text = stringResource(R.string.feature_tasks_review_submit),
                onClick = { start(ReviewAction.Submit) },
                leadingIcon = WaiveraIcons.Check,
                fullWidth = true,
                size = WaiveraButtonSize.Large,
                enabled = access.ready && !busy,
                loadingText = loading(ReviewAction.Submit),
            )
        } else {
            ApproveRejectRow(access, busy, state, actions.onReject, { start(ReviewAction.Approve) }, ::loading)
        }
        if (!access.ready && (access.canSubmit || access.canApprove)) {
            Text(
                text = stringResource(R.string.feature_tasks_review_not_ready),
                style = MaterialTheme.typography.bodySmall,
                color = WaiveraTheme.colors.warningText,
            )
        }
    }
}

@Composable
private fun ApproveRejectRow(
    access: TaskReviewAccess,
    busy: Boolean,
    state: ReviewUiState,
    onReject: () -> Unit,
    onApprove: () -> Unit,
    loading: (ReviewAction) -> String?,
) {
    val reject = @Composable { modifier: Modifier ->
        WaiveraButton(
            text = stringResource(R.string.feature_tasks_review_reject),
            onClick = onReject,
            variant = WaiveraButtonVariant.DestructiveOutline,
            size = WaiveraButtonSize.Large,
            fullWidth = !access.canApprove,
            enabled = !busy,
            loadingText = stringResource(R.string.feature_tasks_review_checking).takeIf {
                state.rejecting &&
                    state.step == null
            },
            modifier = modifier,
        )
    }
    val approve = @Composable { modifier: Modifier ->
        WaiveraButton(
            text = stringResource(R.string.feature_tasks_review_approve),
            onClick = onApprove,
            leadingIcon = WaiveraIcons.Check,
            size = WaiveraButtonSize.Large,
            fullWidth = !access.canReject,
            enabled = access.ready && !busy,
            loadingText = loading(ReviewAction.Approve),
            modifier = modifier,
        )
    }
    when {
        access.canApprove && access.canReject && isLargeFontScale() -> Column(
            verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.Small),
        ) {
            approve(Modifier.fillMaxWidth())
            reject(Modifier.fillMaxWidth())
        }
        access.canApprove && access.canReject -> Row(
            horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.Small),
        ) {
            reject(Modifier.weight(REJECT_WEIGHT))
            approve(Modifier.weight(APPROVE_WEIGHT))
        }
        access.canApprove -> approve(Modifier)
        access.canReject -> reject(Modifier)
    }
}

private const val REJECT_WEIGHT = 1f
private const val APPROVE_WEIGHT = 1.4f
