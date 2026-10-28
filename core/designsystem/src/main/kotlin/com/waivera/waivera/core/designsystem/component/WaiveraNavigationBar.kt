package com.waivera.waivera.core.designsystem.component

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import com.waivera.waivera.core.designsystem.icon.WaiveraIcons
import com.waivera.waivera.core.designsystem.preview.WaiveraPreviews
import com.waivera.waivera.core.designsystem.theme.EXTRA_LARGE_FONT_SCALE
import com.waivera.waivera.core.designsystem.theme.WaiveraElevation
import com.waivera.waivera.core.designsystem.theme.WaiveraShapes
import com.waivera.waivera.core.designsystem.theme.WaiveraSizes
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTextStyles
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme

/** One bottom tab. [key] is the caller's stable id for the tab. */
@Immutable
data class NavigationBarItemModel(
    val key: String,
    val label: String,
    @DrawableRes val icon: Int,
    val badgeCount: Int = 0,
)

/**
 * Bottom tab bar. It owns the navigation bar inset, so screens shown above it must not pad
 * for the navigation bar again.
 *
 * Concept A's bar: the bar's own fill under a top hairline, and the selected tab's one pill
 * behind its glyph. v5 decision 5 makes it tonal: a solid deeper lilac with violet ink in both
 * themes, and the selected label in the heavier weight, never a gradient. An unread count sits on
 * the glyph's top-end corner in the attention rose, ringed in the bar's colour so it reads as cut
 * out of it. The pill is decoration; the label, the selected state and the count carry the meaning.
 */
@Composable
fun WaiveraNavigationBar(
    items: List<NavigationBarItemModel>,
    selectedKey: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = WaiveraTheme.colors
    val fontScale = LocalDensity.current.fontScale
    val extraLarge = fontScale >= EXTRA_LARGE_FONT_SCALE
    // At 150% and up the count sits higher on the glyph's corner (UI10-02), so the bar keeps room
    // above it: the bar clips what is drawn past its top edge.
    val insets = if (extraLarge) {
        NavigationBarDefaults.windowInsets.add(WindowInsets(top = WaiveraSpacing.Small))
    } else {
        NavigationBarDefaults.windowInsets
    }
    val labelStyle = MaterialTheme.typography.labelMedium.let { style ->
        val scale = navLabelTextScale(fontScale) / fontScale
        style.copy(fontSize = style.fontSize * scale, lineHeight = style.lineHeight * scale)
    }
    NavigationBar(
        modifier = modifier
            .testTag(NAV_BAR_TEST_TAG)
            .drawBehind {
                drawLine(
                    color = colors.navBarBorder,
                    start = Offset.Zero,
                    end = Offset(size.width, 0f),
                    strokeWidth = WaiveraSpacing.Hairline.toPx(),
                )
            },
        containerColor = colors.navBar,
        contentColor = colors.text,
        tonalElevation = WaiveraElevation.Level0,
        windowInsets = insets,
    ) {
        items.forEach { item ->
            val selected = item.key == selectedKey
            val washAlpha = rememberTabWashAlpha(selected)
            NavigationBarItem(
                selected = selected,
                onClick = { onSelect(item.key) },
                icon = {
                    NavIconWithBadge(
                        badge = formatBadgeCount(item.badgeCount),
                        besideGlyph = extraLarge,
                        modifier = Modifier
                            .testTag(NAV_ICON_TEST_TAG)
                            .selectedTabPill(fill = colors.selectedFill, alpha = { washAlpha }),
                    ) {
                        Icon(
                            painterResource(item.icon),
                            contentDescription = null,
                            modifier = Modifier.size(WaiveraSizes.Icon),
                        )
                    }
                },
                label = {
                    Text(
                        text = item.label,
                        style = labelStyle,
                        fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = colors.onSelectedFill,
                    selectedTextColor = colors.text,
                    // The pill is drawn once, below, so Material's own indicator stays invisible:
                    // two pills of different widths read as a smear (UI8-01).
                    indicatorColor = Color.Transparent,
                    unselectedIconColor = colors.textSecondary,
                    unselectedTextColor = colors.textSecondary,
                ),
            )
        }
    }
}

/** Fades the selected tab's pill in and out. */
@Composable
private fun rememberTabWashAlpha(selected: Boolean): Float {
    val alpha by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = WaiveraTheme.motion.state(),
        label = "tabWash",
    )
    return alpha
}

/**
 * The selected tab's one pill, solid [fill]. It draws behind the icon and outside the icon's
 * bounds, centred on it, so it never changes the layout.
 */
private fun Modifier.selectedTabPill(fill: Color, alpha: () -> Float): Modifier = drawBehind {
    val shown = alpha()
    if (shown == 0f) return@drawBehind
    val width = WaiveraSizes.NavIndicatorWidth.toPx()
    val height = WaiveraSizes.NavIndicatorHeight.toPx()
    val topLeft = Offset(center.x - width / 2f, center.y - height / 2f)
    drawRoundRect(
        color = fill,
        topLeft = topLeft,
        size = Size(width, height),
        cornerRadius = CornerRadius(height / 2f),
        alpha = shown,
    )
}

/**
 * The tab icon with its unread count pinned to the icon's top-end corner. The badge's start
 * edge stays put and a wider count grows away from the glyph, so "99+" at large font never
 * covers the icon it labels (UI8-06). [besideGlyph] (150% font and up) moves the count clear of
 * the glyph: its start on the glyph's end edge and its middle on the glyph's top edge, so it
 * neither covers the glyph's corner nor reaches as high (UI10-02). The layout reports the
 * icon's size only, as a badge must not move the icon or the label.
 */
@Composable
private fun NavIconWithBadge(
    badge: String?,
    besideGlyph: Boolean,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
) {
    Layout(
        modifier = modifier,
        content = {
            icon()
            if (badge != null) NavUnreadBadge(badge)
        },
    ) { measurables, constraints ->
        val iconPlaceable = measurables[0].measure(constraints)
        val badgePlaceable = measurables.getOrNull(1)?.measure(Constraints())
        val overlapX = WaiveraSizes.NavBadgeOverlap.roundToPx()
        val overlapY = WaiveraSizes.NavBadgeOverlap.roundToPx()
        layout(iconPlaceable.width, iconPlaceable.height) {
            iconPlaceable.placeRelative(0, 0)
            badgePlaceable?.placeRelative(
                x = if (besideGlyph) iconPlaceable.width else iconPlaceable.width - overlapX,
                y = if (besideGlyph) -badgePlaceable.height / 2 else overlapY - badgePlaceable.height,
            )
        }
    }
}

/**
 * The tab's unread count: white on the concept's rose, ringed in the bar's own colour so it
 * stands off the glyph and the pill. The rose is the brand's, apart from the danger red, so an
 * unread count never reads as a failure (UI2-16).
 */
@Composable
private fun NavUnreadBadge(text: String) {
    val colors = WaiveraTheme.colors
    val fontScale = LocalDensity.current.fontScale
    val style = WaiveraTextStyles.Badge
    val scale = navBadgeTextScale(fontScale) / fontScale
    Box(
        modifier = Modifier
            .testTag(NAV_BADGE_TEST_TAG)
            .border(WaiveraSizes.NavBadgeRing, colors.navBar, WaiveraShapes.Pill)
            .padding(WaiveraSizes.NavBadgeRing)
            .defaultMinSize(minWidth = WaiveraSizes.NavBadgeMin, minHeight = WaiveraSizes.NavBadgeMin)
            .background(colors.navBadge, WaiveraShapes.Pill)
            .padding(horizontal = WaiveraSpacing.ExtraSmall),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = style.copy(fontSize = style.fontSize * scale, lineHeight = style.lineHeight * scale),
            color = colors.onAccent,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/**
 * The text scale the tab's unread count renders at: the user's font scale up to
 * [NAV_BADGE_MAX_TEXT_SCALE]. The 24dp glyph does not grow with text, so an uncapped count
 * swallowed it at 200%; the tab's label below still carries the meaning at large text.
 */
internal fun navBadgeTextScale(fontScale: Float): Float = minOf(fontScale, NAV_BADGE_MAX_TEXT_SCALE)

internal const val NAV_BADGE_MAX_TEXT_SCALE = 1.3f

/**
 * The text scale a tab's label renders at: the user's font scale up to [NAV_LABEL_MAX_TEXT_SCALE].
 * The labels are short fixed words that already read at 150%; past that, "Schedule" ran into the
 * screen edge on a small phone (UI10-02).
 */
internal fun navLabelTextScale(fontScale: Float): Float = minOf(fontScale, NAV_LABEL_MAX_TEXT_SCALE)

internal const val NAV_LABEL_MAX_TEXT_SCALE = EXTRA_LARGE_FONT_SCALE

/** Tags each tab's icon box, for the pill pixel test. */
internal const val NAV_ICON_TEST_TAG = "waiveraNavIcon"

/** Tags the bar and each unread count, for the large-font bounds test. */
internal const val NAV_BAR_TEST_TAG = "waiveraNavBar"
internal const val NAV_BADGE_TEST_TAG = "waiveraNavBadge"

@WaiveraPreviews
@Composable
private fun WaiveraNavigationBarPreview() {
    WaiveraTheme {
        WaiveraNavigationBar(
            items = listOf(
                NavigationBarItemModel(key = "today", label = "Today", icon = WaiveraIcons.Today),
                NavigationBarItemModel(key = "chat", label = "Chat", icon = WaiveraIcons.Chat, badgeCount = 4),
                NavigationBarItemModel(key = "schedule", label = "Schedule", icon = WaiveraIcons.Calendar),
            ),
            selectedKey = "chat",
            onSelect = {},
        )
    }
}
