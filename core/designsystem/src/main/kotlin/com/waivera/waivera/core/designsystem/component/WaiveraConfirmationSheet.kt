package com.waivera.waivera.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.waivera.waivera.core.designsystem.R
import com.waivera.waivera.core.designsystem.feedback.rememberHaptics
import com.waivera.waivera.core.designsystem.icon.WaiveraIcons
import com.waivera.waivera.core.designsystem.preview.WaiveraPreviews
import com.waivera.waivera.core.designsystem.text.withBreaksAfterHyphens
import com.waivera.waivera.core.designsystem.theme.WaiveraSizes
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme

/**
 * The styled confirmation that replaces `AlertDialog` everywhere (sign out, delete message,
 * leave conversation). Prefer an Undo snackbar where the action can be reversed.
 * A destructive confirm fires the warning haptic. While [confirmInProgress], the confirm button
 * shows [confirmInProgressLabel] ("Signing out…"), or else its own label, beside its spinner.
 *
 * Concept A's sheet: a destructive question leads with the warning glyph in the danger tile, the
 * confirm is the full 56dp action (the CTA gradient, or the saturated red when destructive) and
 * the dismiss is the tonal button under it.
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
    WaiveraSheetContent(title = null, modifier = modifier) {
        if (destructive) {
            WaiveraIconTile(icon = WaiveraIcons.Warning, tone = WaiveraTileTone.Danger, size = WaiveraSizes.CardTile)
        }
        // Sheet titles often name a person or channel: wrap after a hyphen, never mid-word (UI5-05).
        Text(
            text = title.withBreaksAfterHyphens(),
            style = MaterialTheme.typography.titleLarge,
            color = WaiveraTheme.colors.text,
            modifier = Modifier.semantics { heading() },
        )
        Text(text = message, style = MaterialTheme.typography.bodyMedium, color = WaiveraTheme.colors.textSecondary)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = WaiveraSpacing.MediumSmall),
            verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.Small),
        ) {
            WaiveraButton(
                text = confirmLabel,
                onClick = {
                    if (destructive) haptics.warning()
                    onConfirm()
                },
                modifier = Modifier.heightIn(min = WaiveraSizes.CtaHeight),
                variant = if (destructive) WaiveraButtonVariant.Destructive else WaiveraButtonVariant.Primary,
                // Never a bare spinner: without its own progress copy the confirm label stays beside it.
                loadingText = (confirmInProgressLabel ?: confirmLabel).takeIf { confirmInProgress },
                fullWidth = true,
            )
            WaiveraButton(
                text = dismissLabel,
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = WaiveraSizes.CtaHeight),
                variant = WaiveraButtonVariant.Tonal,
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
