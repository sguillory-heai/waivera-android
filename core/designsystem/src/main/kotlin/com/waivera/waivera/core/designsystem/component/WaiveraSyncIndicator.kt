package com.waivera.waivera.core.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import com.waivera.waivera.core.designsystem.R
import com.waivera.waivera.core.designsystem.icon.WaiveraIcons
import com.waivera.waivera.core.designsystem.preview.WaiveraPreviews
import com.waivera.waivera.core.designsystem.theme.WaiveraSizes
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme

/** Delivery state of an optimistic write (a sent message, a status change). */
enum class SyncState { Synced, Pending, Failed }

/**
 * Small status line under an optimistic item: nothing when synced, "Sending…" while pending,
 * and "Not sent · Tap to retry" (a 48dp-tall button) when it failed.
 */
@Composable
fun WaiveraSyncIndicator(state: SyncState, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    // Pending to failed (or back on retry) swaps in place on the shared swap motion.
    WaiveraSwap(targetState = state, modifier = modifier) { shown -> SyncLine(shown, onRetry) }
}

@Composable
private fun SyncLine(state: SyncState, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val colors = WaiveraTheme.colors
    when (state) {
        SyncState.Synced -> Unit
        SyncState.Pending -> Row(
            modifier = modifier.padding(vertical = WaiveraSpacing.XxSmall),
            horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.ExtraSmall),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painterResource(WaiveraIcons.Schedule),
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier.size(WaiveraSizes.IconSmall),
            )
            Text(
                text = stringResource(R.string.core_designsystem_sync_pending),
                style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary,
            )
        }
        SyncState.Failed -> {
            val description = stringResource(R.string.core_designsystem_sync_failed_a11y)
            val retryLabel = stringResource(R.string.core_designsystem_retry)
            val interactionSource = remember { MutableInteractionSource() }
            val press = rememberPressFeedback(interactionSource)
            Row(
                modifier = modifier
                    .pressFeedback(press)
                    .heightIn(min = WaiveraSpacing.MinTouchTarget)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = ripple(),
                        onClickLabel = retryLabel,
                        role = Role.Button,
                        onClick = onRetry,
                    )
                    .clearAndSetSemantics { contentDescription = description }
                    .padding(horizontal = WaiveraSpacing.ExtraSmall),
                horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.ExtraSmall),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painterResource(WaiveraIcons.Error),
                    contentDescription = null,
                    tint = colors.danger,
                    modifier = Modifier.size(WaiveraSizes.IconSmall),
                )
                Text(
                    text = stringResource(R.string.core_designsystem_sync_failed),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.dangerText,
                )
            }
        }
    }
}

@WaiveraPreviews
@Composable
private fun WaiveraSyncIndicatorPreview() {
    WaiveraTheme {
        Column(Modifier.padding(WaiveraSpacing.Medium)) {
            WaiveraSyncIndicator(state = SyncState.Pending, onRetry = {})
            WaiveraSyncIndicator(state = SyncState.Failed, onRetry = {})
        }
    }
}
