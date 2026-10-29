package com.waivera.waivera.core.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.MutableTransitionState
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.waivera.waivera.core.designsystem.theme.WaiveraMotionScheme
import com.waivera.waivera.core.designsystem.theme.WaiveraSizes
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import kotlinx.coroutines.delay

/** Delivery state of an optimistic write (a sent message, a status change). */
enum class SyncState { Synced, Pending, Failed }

/**
 * Small status line under an optimistic item: nothing when synced, "Sending…" while pending,
 * and "Not sent · Tap to retry" (a 48dp-tall button) when it failed. When a pending or failed
 * item goes through, the line says "Synced" with its check popping for
 * [WaiveraMotionScheme.SYNCED_BEAT_MILLIS], then folds away (M1-07): one quiet beat for the good
 * outcome, with no vibration. Under reduced motion the beat fades in and out without moving.
 */
@Composable
fun WaiveraSyncIndicator(state: SyncState, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val beat = rememberSyncedBeat(state)
    // While the beat shows, the line is null: "Synced". Synced itself is the folded line.
    val shown: SyncState? = if (state == SyncState.Synced && beat) null else state
    // While the line folds away it keeps showing what it last showed.
    val lastLine = remember { LastSyncLine(shown) }
    if (shown != SyncState.Synced) lastLine.value = shown
    val visibility = remember { MutableTransitionState(state != SyncState.Synced) }
    visibility.targetState = shown != SyncState.Synced
    // Synced with its beat over and folded away leaves no node at all, as before: an empty slot
    // would still take room, and a column's spacedBy would keep a gap for it under every item.
    if (!visibility.targetState && !visibility.currentState && visibility.isIdle) return
    // The line folds on the layout token. It is a line under its item, so it folds vertically:
    // folding across would hold its height until the end and then drop the item below it.
    WaiveraExpandVisibility(visibleState = visibility, modifier = modifier) {
        // Pending to failed (or back on retry), and on to the beat, swap in place on the shared
        // swap motion. The leaving content keeps what it showed.
        WaiveraSwap(targetState = lastLine.value) { line -> SyncLine(line, onRetry) }
    }
}

/**
 * True for [WaiveraMotionScheme.SYNCED_BEAT_MILLIS] after [state] becomes synced from pending or
 * failed, and from the very composition that sees the change, so the line never folds first.
 * An item that is synced when it first appears has no beat.
 */
@Composable
private fun rememberSyncedBeat(state: SyncState): Boolean {
    val previous = remember { PreviousSyncState(state) }
    var beating by remember { mutableStateOf(false) }
    val arriving = state == SyncState.Synced && previous.value != SyncState.Synced
    LaunchedEffect(state) {
        val was = previous.value
        previous.value = state
        if (state == SyncState.Synced && was != SyncState.Synced) {
            beating = true
            delay(WaiveraMotionScheme.SYNCED_BEAT_MILLIS)
        }
        beating = false
    }
    return beating || arriving
}

private class PreviousSyncState(var value: SyncState)

private class LastSyncLine(var value: SyncState?)

/** One state's line; null is the synced beat's "Synced". */
@Composable
private fun SyncLine(state: SyncState?, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val colors = WaiveraTheme.colors
    when (state) {
        null -> SyncedLine(modifier)
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

/** "Synced", its check popping in on the pop token as it arrives (a snap under reduced motion). */
@Composable
private fun SyncedLine(modifier: Modifier = Modifier) {
    val colors = WaiveraTheme.colors
    val motion = WaiveraTheme.motion
    val checkPop = remember { Animatable(WaiveraMotionScheme.POP_START_SCALE) }
    LaunchedEffect(Unit) { checkPop.animateTo(1f, motion.pop) }
    Row(
        modifier = modifier.padding(vertical = WaiveraSpacing.XxSmall),
        horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.ExtraSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painterResource(WaiveraIcons.Check),
            contentDescription = null,
            tint = colors.success,
            modifier = Modifier
                .size(WaiveraSizes.IconSmall)
                .popScale(checkPop.asState()),
        )
        Text(
            text = stringResource(R.string.core_designsystem_sync_done),
            style = MaterialTheme.typography.labelSmall,
            color = colors.textSecondary,
        )
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
