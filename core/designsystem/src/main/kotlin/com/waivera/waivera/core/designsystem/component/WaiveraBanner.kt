package com.waivera.waivera.core.designsystem.component

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.waivera.waivera.core.designsystem.R
import com.waivera.waivera.core.designsystem.icon.WaiveraIcons
import com.waivera.waivera.core.designsystem.preview.WaiveraPreviews
import com.waivera.waivera.core.designsystem.theme.TitleLineBreak
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
 * A banner shows its title and message in full at every font scale, and never cuts a title
 * short: an error or warning that hid its reason ("Couldn't sign in" with no why) would fail
 * the glance it exists for (UI4-01).
 *
 * The action sits beside the text when it is short and the message fits in two lines beside it;
 * otherwise it moves to its own row under the text, so the message keeps the full width instead
 * of squeezing into a narrow column (UI4-06). Above the large font scale an action with an
 * [actionIcon] becomes a 48dp icon button whose content description is [actionLabel], so the
 * text keeps the width and wraps; without an icon the text action goes under the text.
 *
 * [compact] is for status banners only: the connection banner and "showing saved data" notices,
 * where the title alone says what is going on. A compact banner with a [title] shows the title
 * (wrapping, never ellipsized) and a visible chevron, and folds the message away behind a tap on
 * them; TalkBack always reads both. [ConnectionBanner] goes compact at large font by itself;
 * other status banners pass `compact = isLargeFontScale()`.
 *
 * [gutter] insets the banner by the screen gutter; turn it off inside a block that is already
 * padded, so the banner lines up with the buttons around it instead of sitting narrower (UI3-15).
 *
 * [detail] is one value the message is about, such as an email address, on its own line between
 * the title and the message. It is shown whole as a [WaiveraAddressText]: one line when it fits,
 * otherwise wrapped after its "@", never cut, because it is the fact the banner confirms
 * (UI6-03). Inside a sentence the paragraph breaker split an address at its dots.
 */
@Composable
fun WaiveraBanner(
    message: String,
    modifier: Modifier = Modifier,
    tone: BannerTone = BannerTone.Info,
    title: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    @DrawableRes actionIcon: Int? = null,
    onDismiss: (() -> Unit)? = null,
    compact: Boolean = false,
    gutter: Boolean = true,
    detail: String? = null,
) {
    val style = tone.style(WaiveraTheme.colors)
    val action = if (actionLabel != null && onAction != null) BannerActionModel(actionLabel, onAction) else null
    val collapsible = compact && title != null
    val largeFont = isLargeFontScale()
    // An action and a dismiss beside the text took two 48dp buttons out of a 200% line and left
    // the reason a ragged four-word column; they go in a row under the text instead (UI5-06).
    val actionsBelow = action != null && onDismiss != null && largeFont
    val iconAction = if (action != null && largeFont && !actionsBelow) actionIcon else null
    var expanded by rememberSaveable(message) { mutableStateOf(false) }
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                horizontal = if (gutter) WaiveraSpacing.Gutter else WaiveraSpacing.None,
                vertical = WaiveraSpacing.ExtraSmall,
            ),
    ) {
        val stacked = action != null &&
            !actionsBelow &&
            iconAction == null &&
            !collapsible &&
            rememberBannerActionStacks(
                label = action.label,
                message = message,
                hasDismiss = onDismiss != null,
                availableWidthPx = constraints.maxWidth,
            )
        val trailingInline = !actionsBelow && ((action != null && !stacked) || onDismiss != null)
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
            // Top-aligned, so the icon and buttons stay by the first line of a wrapped message
            // instead of floating mid-paragraph (UI5-09). A compact banner stays centred: its
            // text column is already one touch target tall.
            Row(
                horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.MediumSmall),
                verticalAlignment = if (collapsible) Alignment.CenterVertically else Alignment.Top,
            ) {
                BannerIcon(
                    icon = style.icon,
                    color = style.content,
                    firstLineStyle = if (title != null) {
                        MaterialTheme.typography.titleSmall
                    } else {
                        MaterialTheme.typography.bodySmall
                    },
                    centred = collapsible,
                )
                val textModifier = Modifier.weight(1f)
                if (collapsible) {
                    CompactBannerText(
                        title = title.orEmpty(),
                        message = message,
                        detail = detail,
                        expanded = expanded,
                        onToggle = { expanded = !expanded },
                        color = style.content,
                        modifier = textModifier,
                    )
                } else {
                    BannerText(
                        title = title,
                        message = message,
                        detail = detail,
                        color = style.content,
                        modifier = textModifier,
                    )
                }
                when {
                    action == null || stacked || actionsBelow -> Unit
                    iconAction != null -> WaiveraIconButton(
                        icon = iconAction,
                        contentDescription = action.label,
                        onClick = action.onClick,
                        tint = style.content,
                    )
                    else -> BannerAction(action = action, color = style.content)
                }
                if (onDismiss != null && !actionsBelow) BannerDismiss(onDismiss = onDismiss, color = style.content)
            }
            if (action != null && onDismiss != null && actionsBelow) {
                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BannerAction(action = action, color = style.content)
                    BannerDismiss(onDismiss = onDismiss, color = style.content)
                }
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
 * The compact banner's text: the title (wrapping, never cut) with a chevron that shows there is
 * more, and the message under it only when [expanded]. One TalkBack stop that always reads both,
 * so folding the message away hides nothing.
 */
@Composable
private fun CompactBannerText(
    title: String,
    message: String,
    detail: String?,
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
            .semantics { contentDescription = listOfNotNull(title, detail, message).joinToString(separator = ". ") }
            .padding(vertical = WaiveraSpacing.Small),
        verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.XxSmall, Alignment.CenterVertically),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.ExtraSmall),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The column's description already reads title and message; the texts stay silent.
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = color,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .clearAndSetSemantics { testTag = BANNER_COMPACT_TITLE_TAG },
            )
            Icon(
                painter = painterResource(if (expanded) WaiveraIcons.ExpandLess else WaiveraIcons.ExpandMore),
                contentDescription = null,
                tint = color,
                modifier = Modifier
                    .size(WaiveraSizes.Icon)
                    .semantics { testTag = BANNER_CHEVRON_TAG },
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(motion.layout()) + fadeIn(motion.state()),
            exit = shrinkVertically(motion.layout()) + fadeOut(motion.state()),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.XxSmall)) {
                if (detail != null) {
                    // The column's description already reads the detail.
                    Box(modifier = Modifier.clearAndSetSemantics {}) { BannerDetail(detail = detail, color = color) }
                }
                Text(
                    text = message,
                    style = bannerMessageStyle(),
                    color = color,
                    modifier = Modifier.clearAndSetSemantics {},
                )
            }
        }
    }
}

/** Test tags for the compact banner's title and its "more" chevron. */
internal const val BANNER_COMPACT_TITLE_TAG = "waivera_banner_compact_title"
internal const val BANNER_CHEVRON_TAG = "waivera_banner_chevron"

/** Test tag for the banner's detail. */
internal const val BANNER_DETAIL_TAG = "waivera_banner_detail"

@Immutable
private class BannerActionModel(val label: String, val onClick: () -> Unit)

@Composable
private fun BannerText(title: String?, message: String, detail: String?, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .heightIn(min = WaiveraSpacing.MinTouchTarget)
            .padding(vertical = WaiveraSpacing.Small),
        verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.XxSmall, Alignment.CenterVertically),
    ) {
        if (title != null) {
            Text(text = title, style = MaterialTheme.typography.titleSmall, color = color)
        }
        if (detail != null) BannerDetail(detail = detail, color = color)
        Text(text = message, style = bannerMessageStyle(), color = color)
    }
}

/**
 * The message wraps greedily: in a banner's narrow column the balancing breaker left a one-word
 * first line ("Showing / your saved / conversations.") where "Showing your" fits (UI5-09).
 */
@Composable
private fun bannerMessageStyle(): TextStyle = MaterialTheme.typography.bodySmall.copy(lineBreak = TitleLineBreak)

/**
 * The leading icon, centred on the first line of the text beside it: a slot as tall as the
 * text's top padding, first line and bottom padding, and never shorter than one touch target,
 * which is how tall a one-line banner's text column is.
 */
@Composable
private fun BannerIcon(@DrawableRes icon: Int, color: Color, firstLineStyle: TextStyle, centred: Boolean) {
    val firstLine = with(LocalDensity.current) { firstLineStyle.lineHeight.toDp() }
    val slot = maxOf(WaiveraSpacing.MinTouchTarget, firstLine + WaiveraSpacing.Small * 2)
    Box(
        modifier = if (centred) Modifier else Modifier.height(slot),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(icon),
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(WaiveraSizes.Icon),
        )
    }
}

/** The banner's [detail]: the whole address, wrapping only where an address may wrap. */
@Composable
private fun BannerDetail(detail: String, color: Color, modifier: Modifier = Modifier) {
    WaiveraAddressText(
        address = detail,
        style = MaterialTheme.typography.bodySmall,
        color = color,
        modifier = modifier.semantics { testTag = BANNER_DETAIL_TAG },
    )
}

@Composable
private fun BannerAction(action: BannerActionModel, color: Color, modifier: Modifier = Modifier) {
    TextButton(onClick = action.onClick, modifier = modifier.heightIn(min = WaiveraSpacing.MinTouchTarget)) {
        Text(text = action.label, style = MaterialTheme.typography.labelLarge, color = color)
    }
}

/**
 * Measures the action label, and the message in the width it would have beside the action (and
 * dismiss), once per text, style, density and width; then applies [bannerActionStacks].
 */
@Composable
private fun rememberBannerActionStacks(
    label: String,
    message: String,
    hasDismiss: Boolean,
    availableWidthPx: Int,
): Boolean {
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelLarge
    val messageStyle = bannerMessageStyle()
    val density = LocalDensity.current
    val labelWidthPx = remember(label, labelStyle, density) {
        measurer.measure(text = label, style = labelStyle).size.width
    }
    val actionWidthPx = labelWidthPx + with(density) { (WaiveraSpacing.MediumSmall * 2).roundToPx() }
    // Start padding, the leading icon and the gaps on both sides of the text, plus the dismiss.
    val dismissWidth = if (hasDismiss) WaiveraSizes.IconButton + WaiveraSpacing.MediumSmall else WaiveraSpacing.None
    val chromeWidth = WaiveraSpacing.MediumSmall * 3 + WaiveraSizes.Icon + dismissWidth
    val besideWidthPx = availableWidthPx - with(density) { chromeWidth.roundToPx() } - actionWidthPx
    val messageLines = remember(message, messageStyle, density, besideWidthPx) {
        if (besideWidthPx <= 0) {
            Int.MAX_VALUE
        } else {
            measurer.measure(
                text = message,
                style = messageStyle,
                constraints = Constraints(maxWidth = besideWidthPx),
            ).lineCount
        }
    }
    return bannerActionStacks(
        fontScale = density.fontScale,
        actionWidthPx = actionWidthPx,
        availableWidthPx = availableWidthPx,
        messageLinesBeside = messageLines,
    )
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
 *
 * This is the one connection signal on a screen: [offlineMessage] replaces the offline detail
 * when the screen wants to say what it is showing instead ("Showing what you had at 9:12"), so
 * screens never stack a second "saved data" banner under it.
 *
 * It is a status banner, so at large font it goes compact: one row of title, chevron and a
 * refresh icon, with the detail a tap away.
 */
@Composable
fun ConnectionBanner(
    state: ConnectionBannerState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    offlineMessage: String? = null,
) {
    val motion = WaiveraTheme.motion
    val compact = isLargeFontScale()
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
                actionIcon = WaiveraIcons.Refresh,
                compact = compact,
            )
            ConnectionBannerState.Offline -> WaiveraBanner(
                title = stringResource(R.string.core_designsystem_connection_offline),
                message = offlineMessage ?: stringResource(R.string.core_designsystem_connection_offline_detail),
                tone = BannerTone.Offline,
                actionLabel = stringResource(R.string.core_designsystem_connection_retry),
                onAction = onRetry,
                actionIcon = WaiveraIcons.Refresh,
                compact = compact,
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
private fun WaiveraBannerCompactPreview() {
    WaiveraTheme {
        Column(verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.Small)) {
            WaiveraBanner(
                title = "You're offline",
                message = "Showing what you had at 10:18 AM.",
                tone = BannerTone.Offline,
                actionLabel = "Retry now",
                onAction = {},
                actionIcon = WaiveraIcons.Refresh,
                compact = true,
            )
            WaiveraBanner(
                title = "Couldn't refresh",
                message = "Showing what was saved.",
                tone = BannerTone.Warning,
                actionLabel = "Retry",
                onAction = {},
                actionIcon = WaiveraIcons.Refresh,
                compact = true,
            )
            // A long status title wraps; it is never ellipsized.
            WaiveraBanner(
                title = "Still can't reach Waivera after several tries",
                message = "Showing saved conversations.",
                tone = BannerTone.Offline,
                actionLabel = "Retry now",
                onAction = {},
                actionIcon = WaiveraIcons.Refresh,
                compact = true,
            )
        }
    }
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
                actionIcon = WaiveraIcons.Refresh,
                onDismiss = {},
            )
            WaiveraBanner(
                title = "Notifications are off for Waivera",
                message = "Android is blocking notifications from this app.",
                tone = BannerTone.Warning,
                actionLabel = "Open settings",
                onAction = {},
                actionIcon = WaiveraIcons.OpenExternal,
            )
            WaiveraBanner(
                title = "1 message not sent",
                message = "It's further up the conversation.",
                tone = BannerTone.Error,
                actionLabel = "Show",
                onAction = {},
                actionIcon = WaiveraIcons.ArrowUp,
                onDismiss = {},
            )
            // The address on its own line, never broken at its dots.
            WaiveraBanner(
                title = "Check your email",
                message = "If this address has a Waivera account, we've sent it a link to reset the password.",
                tone = BannerTone.Info,
                onDismiss = {},
                detail = "alex.rivera@example.org",
            )
            // Inside a block that already has the gutter: as wide as the buttons around it.
            Column(Modifier.padding(horizontal = WaiveraSpacing.Gutter)) {
                WaiveraBanner(message = "Couldn't join the meeting.", tone = BannerTone.Error, gutter = false)
            }
        }
    }
}
