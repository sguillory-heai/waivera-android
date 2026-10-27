package com.waivera.waivera.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.waivera.waivera.core.designsystem.R
import com.waivera.waivera.core.designsystem.feedback.rememberHaptics
import com.waivera.waivera.core.designsystem.preview.WaiveraPreviews
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme

/**
 * The styled confirmation that replaces `AlertDialog` everywhere (sign out, delete message,
 * leave conversation). Prefer an Undo snackbar where the action can be reversed.
 * A destructive confirm fires the warning haptic. While [confirmInProgress], the confirm button
 * shows [confirmInProgressLabel] ("Signing out…"), or else its own label, beside its spinner.
 */
@Composable
fun WaiveraConfirmationSheet(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    dismissLabel: String = stringResource(R.string.core_designsystem_cancel),
    destructive: Boolean = false,
    confirmInProgress: Boolean = false,
    confirmInProgressLabel: String? = null,
) {
    WaiveraBottomSheet(onDismissRequest = onDismiss, modifier = modifier) {
        ConfirmationContent(
            title = title,
            message = message,
            confirmLabel = confirmLabel,
            dismissLabel = dismissLabel,
            destructive = destructive,
            confirmInProgress = confirmInProgress,
            confirmInProgressLabel = confirmInProgressLabel,
            onConfirm = onConfirm,
            onDismiss = onDismiss,
        )
    }
}

@Composable
internal fun ConfirmationContent(
    title: String,
    message: String,
    confirmLabel: String,
    dismissLabel: String,
    destructive: Boolean,
    confirmInProgress: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    confirmInProgressLabel: String? = null,
) {
    val haptics = rememberHaptics()
    WaiveraSheetContent(title = title, modifier = modifier) {
        Text(text = message, style = MaterialTheme.typography.bodyMedium, color = WaiveraTheme.colors.textSecondary)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = WaiveraSpacing.Small),
            verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.Small),
        ) {
            WaiveraButton(
                text = confirmLabel,
                onClick = {
                    if (destructive) haptics.warning()
                    onConfirm()
                },
                variant = if (destructive) WaiveraButtonVariant.Destructive else WaiveraButtonVariant.Primary,
                // Never a bare spinner: without its own progress copy the confirm label stays beside it.
                loadingText = (confirmInProgressLabel ?: confirmLabel).takeIf { confirmInProgress },
                fullWidth = true,
            )
            WaiveraButton(
                text = dismissLabel,
                onClick = onDismiss,
                variant = WaiveraButtonVariant.Secondary,
                enabled = !confirmInProgress,
                fullWidth = true,
            )
        }
    }
}

@WaiveraPreviews
@Composable
private fun ConfirmationContentPreview() {
    WaiveraTheme {
        ConfirmationContent(
            title = "Delete message?",
            message = "This removes the message for everyone in the conversation.",
            confirmLabel = "Delete",
            dismissLabel = "Cancel",
            destructive = true,
            confirmInProgress = false,
            onConfirm = {},
            onDismiss = {},
        )
    }
}
