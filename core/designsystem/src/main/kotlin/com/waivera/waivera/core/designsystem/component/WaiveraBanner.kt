package com.waivera.waivera.core.designsystem.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.waivera.waivera.core.designsystem.R
import com.waivera.waivera.core.designsystem.icon.WaiveraIcons
import com.waivera.waivera.core.designsystem.preview.WaiveraPreviews
import com.waivera.waivera.core.designsystem.theme.WaiveraColors
import com.waivera.waivera.core.designsystem.theme.WaiveraShapes
import com.waivera.waivera.core.designsystem.theme.WaiveraSizes
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme

enum class BannerTone { Info, Warning, Error, Offline }

/**
 * Inline banner: leading icon, optional title, message, optional action and dismiss.
 * Announced politely when it appears. Never covers content.
 */
@Composable
fun WaiveraBanner(
    message: String,
    modifier: Modifier = Modifier,
    tone: BannerTone = BannerTone.Info,
    title: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
) {
    val style = tone.style(WaiveraTheme.colors)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = WaiveraSpacing.Gutter, vertical = WaiveraSpacing.ExtraSmall)
            .background(style.container, WaiveraShapes.Banner)
            .padding(
                start = WaiveraSpacing.MediumSmall,
                top = WaiveraSpacing.ExtraSmall,
                bottom = WaiveraSpacing.ExtraSmall,
            )
            .semantics(mergeDescendants = false) { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.MediumSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painterResource(style.icon),
            contentDescription = null,
            tint = style.content,
            modifier = Modifier.size(WaiveraSizes.Icon),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = WaiveraSpacing.Small),
            verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.XxSmall),
        ) {
            if (title != null) {
                Text(text = title, style = MaterialTheme.typography.titleSmall, color = style.content)
            }
            Text(text = message, style = MaterialTheme.typography.bodySmall, color = style.content)
        }
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) {
                Text(text = actionLabel, style = MaterialTheme.typography.labelLarge, color = style.content)
            }
        }
        if (onDismiss != null) {
            WaiveraIconButton(
                icon = WaiveraIcons.Close,
                contentDescription = stringResource(R.string.core_designsystem_dismiss),
                onClick = onDismiss,
                tint = style.content,
            )
        }
    }
}

/** Hub connection state as the banner shows it. [Connected] shows nothing. */
@Immutable
sealed interface ConnectionBannerState {
    data object Connected : ConnectionBannerState

    data object Connecting : ConnectionBannerState

    data object Reconnecting : ConnectionBannerState

    data object Offline : ConnectionBannerState
}

/**
 * Quiet connection banner for offline and reconnecting states with a manual retry. Reconnect
 * never gives up on its own; Retry lets the person skip the backoff wait.
 */
@Composable
fun ConnectionBanner(state: ConnectionBannerState, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val motion = WaiveraTheme.motion
    AnimatedVisibility(
        visible = state != ConnectionBannerState.Connected,
        modifier = modifier,
        enter = expandVertically(motion.layout()) + fadeIn(motion.state()),
        exit = shrinkVertically(motion.layout()) + fadeOut(motion.state()),
    ) {
        when (state) {
            ConnectionBannerState.Connected -> Unit
            ConnectionBannerState.Connecting -> WaiveraBanner(
                message = stringResource(R.string.core_designsystem_connection_connecting),
                tone = BannerTone.Info,
            )
            ConnectionBannerState.Reconnecting -> WaiveraBanner(
                title = stringResource(R.string.core_designsystem_connection_reconnecting),
                message = stringResource(R.string.core_designsystem_connection_reconnecting_detail),
                tone = BannerTone.Warning,
                actionLabel = stringResource(R.string.core_designsystem_connection_retry),
                onAction = onRetry,
            )
            ConnectionBannerState.Offline -> WaiveraBanner(
                title = stringResource(R.string.core_designsystem_connection_offline),
                message = stringResource(R.string.core_designsystem_connection_offline_detail),
                tone = BannerTone.Offline,
                actionLabel = stringResource(R.string.core_designsystem_connection_retry),
                onAction = onRetry,
            )
        }
    }
}

private class BannerStyle(val container: Color, val content: Color, val icon: Int)

private fun BannerTone.style(colors: WaiveraColors): BannerStyle = when (this) {
    BannerTone.Info -> BannerStyle(colors.accentSoft, colors.onAccentSoft, WaiveraIcons.Info)
    BannerTone.Warning -> BannerStyle(colors.warningContainer, colors.onWarningContainer, WaiveraIcons.Warning)
    BannerTone.Error -> BannerStyle(colors.dangerContainer, colors.onDangerContainer, WaiveraIcons.Error)
    BannerTone.Offline -> BannerStyle(colors.surfaceMuted, colors.text, WaiveraIcons.CloudOff)
}

@WaiveraPreviews
@Composable
private fun WaiveraBannerPreview() {
    WaiveraTheme {
        Column(verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.Small)) {
            ConnectionBanner(state = ConnectionBannerState.Reconnecting, onRetry = {})
            ConnectionBanner(state = ConnectionBannerState.Offline, onRetry = {})
            WaiveraBanner(
                message = "Message not sent.",
                tone = BannerTone.Error,
                actionLabel = "Retry",
                onAction = {},
                onDismiss = {},
            )
        }
    }
}
