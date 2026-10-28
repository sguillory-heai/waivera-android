package com.waivera.waivera.core.designsystem.component

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.waivera.waivera.core.designsystem.icon.WaiveraIcons
import com.waivera.waivera.core.designsystem.preview.WaiveraPreviews
import com.waivera.waivera.core.designsystem.theme.WaiveraBrand
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
 * The brand shows here with restraint: the selected tab's soft indicator carries a light wash of
 * the logo gradient, and an unread count sits in a thin gradient ring. The ring and wash are
 * decoration; the label, the selected state and the count carry the meaning.
 */
@Composable
fun WaiveraNavigationBar(
    items: List<NavigationBarItemModel>,
    selectedKey: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = WaiveraTheme.colors
    NavigationBar(
        modifier = modifier,
        containerColor = colors.surface,
        contentColor = colors.text,
        tonalElevation = WaiveraElevation.Level0,
    ) {
        items.forEach { item ->
            val selected = item.key == selectedKey
            val washAlpha = rememberTabWashAlpha(selected)
            NavigationBarItem(
                selected = selected,
                onClick = { onSelect(item.key) },
                icon = {
                    BadgedBox(
                        badge = { formatBadgeCount(item.badgeCount)?.let { NavUnreadBadge(it) } },
                        modifier = Modifier.selectedTabWash { washAlpha },
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
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (selected) FontWeight.Bold else null,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = colors.onAccentSoft,
                    selectedTextColor = colors.text,
                    indicatorColor = colors.accentSoft,
                    unselectedIconColor = colors.textSecondary,
                    unselectedTextColor = colors.textSecondary,
                ),
            )
        }
    }
}

/** Fades the selected tab's brand wash in and out with the Material indicator under it. */
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
 * The brand wash over the Material indicator pill. It draws behind the icon and outside the
 * icon's bounds, centred on it, at the indicator's own size, so it never changes the layout.
 */
private fun Modifier.selectedTabWash(alpha: () -> Float): Modifier = drawBehind {
    val shown = alpha()
    if (shown == 0f) return@drawBehind
    val width = WaiveraSizes.NavIndicatorWidth.toPx()
    val height = WaiveraSizes.NavIndicatorHeight.toPx()
    val left = center.x - width / 2f
    drawRoundRect(
        brush = WaiveraBrand.horizontalGradient(
            alpha = WaiveraBrand.ACCENT_WASH_ALPHA,
            startX = left,
            endX = left + width,
        ),
        topLeft = Offset(left, center.y - height / 2f),
        size = Size(width, height),
        cornerRadius = CornerRadius(height / 2f),
        alpha = shown,
    )
}

/** Unread, not failure: the same purple as every other count (UI2-16), in a thin brand ring. */
@Composable
private fun NavUnreadBadge(text: String) {
    val colors = WaiveraTheme.colors
    Box(
        modifier = Modifier
            .border(WaiveraSizes.NavBadgeRing, WaiveraBrand.diagonalGradient(), WaiveraShapes.Pill)
            .padding(WaiveraSizes.NavBadgeRing)
            .defaultMinSize(minWidth = WaiveraSizes.NavBadgeMin, minHeight = WaiveraSizes.NavBadgeMin)
            .background(colors.unread, WaiveraShapes.Pill)
            .padding(horizontal = WaiveraSpacing.ExtraSmall),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, style = WaiveraTextStyles.Badge, color = colors.onAccent, maxLines = 1, softWrap = false)
    }
}

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
