package com.waivera.waivera.core.designsystem.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.waivera.waivera.core.designsystem.R
import com.waivera.waivera.core.designsystem.icon.WaiveraIcons
import com.waivera.waivera.core.designsystem.preview.WaiveraPreviews
import com.waivera.waivera.core.designsystem.theme.LARGE_FONT_SCALE
import com.waivera.waivera.core.designsystem.theme.WaiveraColors
import com.waivera.waivera.core.designsystem.theme.WaiveraShapes
import com.waivera.waivera.core.designsystem.theme.WaiveraSizes
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.core.designsystem.theme.isLargeFontScale

enum class BannerTone { Info, Warning, Error, Offline }

/**
 * Inline banner: leading icon, optional title, message, optional action and dismiss.
 * Announced politely when it appears. Never covers content.
 *
 * The action sits beside the text only when it is short; when the action label would take more
 * than about a third of the width, it moves to its own row under the text so the message keeps
 * the full width.
 *
 * Above [LARGE_FONT_SCALE] a banner with a [title] goes compact: title and action on one row,
 * and the message folds away behind a tap on the title (TalkBack still reads the message, and
 * the tap is announced as "Show details"). At 200% a full banner would otherwise take a third of
 * a small phone. Pass [compact] to force either form.
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
    compact: Boolean = title != null && isLargeFontScale(),
) {
    val style = tone.style(WaiveraTheme.colors)
    val action = if (actionLabel != null && onAction != null) BannerActionModel(actionLabel, onAction) else null
    val collapsible = compact && title != null
    var expanded by rememberSaveable(message) { mutableStateOf(false) }
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = WaiveraSpacing.Gutter, vertical = WaiveraSpacing.ExtraSmall),
    ) {
        val stacked = action != null && rememberBannerActionStacks(action.label, constraints.maxWidth, collapsible)
        val trailingInline = (action != null && !stacked) || onDismiss != null
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(style.container, WaiveraShapes.Banner)
                .padding(
                    start = WaiveraSpacing.MediumSmall,
                    end = if (trailingInline) 0.dp else WaiveraSpacing.MediumSmall,
                    top = WaiveraSpacing.ExtraSmall,
                    bottom = WaiveraSpacing.ExtraSmall,
                )
                .semantics(mergeDescendants = false) { liveRegion = LiveRegionMode.Polite },
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.MediumSmall),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painterResource(style.icon),
                    contentDescription = null,
                    tint = style.content,
                    modifier = Modifier.size(WaiveraSizes.Icon),
                )
                val textModifier = Modifier.weight(1f)
                if (collapsible) {
                    CompactBannerText(
                        title = title.orEmpty(),
                        message = message,
                        expanded = expanded,
                        onToggle = { expanded = !expanded },
                        color = style.content,
                        modifier = textModifier,
                    )
                } else {
                    BannerText(title = title, message = message, color = style.content, modifier = textModifier)
                }
                if (action != null && !stacked) BannerAction(action, style.content)
                if (onDismiss != null) BannerDismiss(onDismiss, style.content)
            }
            if (action != null && stacked) {
                BannerAction(
                    action = action,
                    color = style.content,
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(start = WaiveraSizes.Icon + WaiveraSpacing.MediumSmall),
                )
            }
        }
    }
}

@Composable
private fun BannerDismiss(onDismiss: () -> Unit, color: Color) {
    WaiveraIconButton(
        icon = WaiveraIcons.Close,
        contentDescription = stringResource(R.string.core_designsystem_dismiss),
        onClick = onDismiss,
        tint = color,
    )
}

/**
 * The compact banner's text: the title, with the message under it only when [expanded]. One
 * TalkBack stop that always reads both, so folding the message away hides nothing.
 */
@Composable
private fun CompactBannerText(
    title: String,
    message: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val toggleLabel = if (expanded) {
        stringResource(R.string.core_designsystem_banner_hide_details)
    } else {
        stringResource(R.string.core_designsystem_banner_show_details)
    }
    val motion = WaiveraTheme.motion
    Column(
        modifier = modifier
            .heightIn(min = WaiveraSpacing.MinTouchTarget)
            .clickable(onClickLabel = toggleLabel, role = Role.Button, onClick = onToggle)
            .semantics { contentDescription = "$title. $message" }
            .padding(vertical = WaiveraSpacing.Small),
        verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.XxSmall, Alignment.CenterVertically),
    ) {
        // The column's description already reads title and message; the texts stay silent.
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = color,
            modifier = Modifier.clearAndSetSemantics {},
        )
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(motion.layout()) + fadeIn(motion.state()),
            exit = shrinkVertically(motion.layout()) + fadeOut(motion.state()),
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = color,
                modifier = Modifier.clearAndSetSemantics {},
            )
        }
    }
}

@Immutable
private class BannerActionModel(val label: String, val onClick: () -> Unit)

@Composable
private fun BannerText(title: String?, message: String, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(vertical = WaiveraSpacing.Small),
        verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.XxSmall),
    ) {
        if (title != null) {
            Text(text = title, style = MaterialTheme.typography.titleSmall, color = color)
        }
        Text(text = message, style = MaterialTheme.typography.bodySmall, color = color)
    }
}

@Composable
private fun BannerAction(action: BannerActionModel, color: Color, modifier: Modifier = Modifier) {
    TextButton(onClick = action.onClick, modifier = modifier.heightIn(min = WaiveraSpacing.MinTouchTarget)) {
        Text(text = action.label, style = MaterialTheme.typography.labelLarge, color = color)
    }
}

/**
 * Measures the action label once per label, style and density, then applies [bannerActionStacks].
 * A [compact] banner has the row to itself, so only the width rule applies there.
 */
@Composable
private fun rememberBannerActionStacks(label: String, availableWidthPx: Int, compact: Boolean): Boolean {
    val measurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.labelLarge
    val density = LocalDensity.current
    val labelWidthPx = remember(label, style, density) {
        measurer.measure(text = label, style = style).size.width
    }
    val buttonPaddingPx = with(density) { (WaiveraSpacing.MediumSmall * 2).roundToPx() }
    val fontScale = if (compact) 1f else density.fontScale
    return bannerActionStacks(fontScale, labelWidthPx + buttonPaddingPx, availableWidthPx)
}

/**
 * True when the action should go under the text: above [BANNER_STACK_FONT_SCALE], or when the
 * action would take more than [BANNER_INLINE_ACTION_MAX_FRACTION] of the banner width.
 */
internal fun bannerActionStacks(fontScale: Float, actionWidthPx: Int, availableWidthPx: Int): Boolean =
    fontScale > BANNER_STACK_FONT_SCALE ||
        actionWidthPx > availableWidthPx * BANNER_INLINE_ACTION_MAX_FRACTION

internal const val BANNER_STACK_FONT_SCALE = LARGE_FONT_SCALE
internal const val BANNER_INLINE_ACTION_MAX_FRACTION = 0.35f

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
 *
 * This is the one connection signal on a screen: [offlineMessage] replaces the offline detail
 * when the screen wants to say what it is showing instead ("Showing what you had at 9:12"), so
 * screens never stack a second "saved data" banner under it.
 */
@Composable
fun ConnectionBanner(
    state: ConnectionBannerState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    offlineMessage: String? = null,
) {
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
                message = offlineMessage ?: stringResource(R.string.core_designsystem_connection_offline_detail),
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
            WaiveraBanner(
                title = "Notifications are off for Waivera",
                message = "Android is blocking notifications from this app.",
                tone = BannerTone.Warning,
                actionLabel = "Open settings",
                onAction = {},
            )
        }
    }
}
