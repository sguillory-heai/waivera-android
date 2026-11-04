package com.waivera.waivera.core.designsystem.component

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.center
import androidx.compose.ui.unit.toOffset
import com.waivera.waivera.core.designsystem.icon.WaiveraIcons
import com.waivera.waivera.core.designsystem.preview.WaiveraPreviews
import com.waivera.waivera.core.designsystem.theme.EXTRA_LARGE_FONT_SCALE
import com.waivera.waivera.core.designsystem.theme.PressDepth
import com.waivera.waivera.core.designsystem.theme.WaiveraElevation
import com.waivera.waivera.core.designsystem.theme.WaiveraShapes
import com.waivera.waivera.core.designsystem.theme.WaiveraSizes
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTextStyles
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.core.designsystem.theme.glide
import kotlin.math.abs
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

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
 * behind its glyph. v5 decision 5, option A (owner pick), makes it the solid brand violet with a
 * white glyph in both themes, and the selected label in the heavier weight, never a gradient. An unread count sits on
 * the glyph's top-end corner in the attention rose, ringed in the bar's colour so it reads as cut
 * out of it. The pill is decoration; the label, the selected state and the count carry the meaning.
 *
 * Feedback, matching the iPhone bar: a tab's glyph and the pill under it shrink from touch-down,
 * then the bar's one pill glides across to the tab just picked and its glyph pops. One pill moves
 * rather than one fading out and another in, so the bar never shows two half pills (M1-11). A tab
 * switch has no haptic (the feedback map keeps taps silent).
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
    val pill = rememberTabPill(selectedKey)
    NavigationBar(
        modifier = modifier
            .testTag(NAV_BAR_TEST_TAG)
            // Glyph centres are kept relative to the bar, so the bar sliding in or out (or the
            // window resizing) moves the pill with it instead of sending it gliding.
            .onPlaced { pill.bar = it }
            .drawBehind {
                // The bar's fill is drawn here, under the pill: Material's own container would
                // cover anything drawn behind the bar.
                drawRect(colors.navBar)
                drawLine(
                    color = colors.navBarBorder,
                    start = Offset.Zero,
                    end = Offset(size.width, 0f),
                    strokeWidth = WaiveraSpacing.Hairline.toPx(),
                )
                drawTabPill(pill, colors.selectedFill)
            },
        containerColor = Color.Transparent,
        contentColor = colors.text,
        tonalElevation = WaiveraElevation.Level0,
        windowInsets = insets,
    ) {
        items.forEach { item ->
            val selected = item.key == selectedKey
            val interactionSource = remember { MutableInteractionSource() }
            val press = rememberPressFeedback(interactionSource, depth = PressDepth.Compact)
            pill.presses[item.key] = press
            // The tab just picked pops its glyph; the press shrinks glyph and pill from touch-down.
            val pop = rememberPopScale(key = selected, enabled = selected)
            NavigationBarItem(
                selected = selected,
                onClick = { onSelect(item.key) },
                modifier = Modifier.pressTracking(press),
                interactionSource = interactionSource,
                icon = {
                    NavIconWithBadge(
                        badge = formatBadgeCount(item.badgeCount),
                        besideGlyph = extraLarge,
                        modifier = Modifier
                            .testTag(NAV_ICON_TEST_TAG)
                            // Before the scales, so the pill aims at the glyph's resting centre.
                            .onGloballyPositioned { pill.place(item.key, it) }
                            .pressScale(press)
                            .popScale(pop),
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
                    // The pill is drawn once, by the bar, so Material's own indicator stays
                    // invisible: two pills of different widths read as a smear (UI8-01).
                    indicatorColor = Color.Transparent,
                    unselectedIconColor = colors.textSecondary,
                    unselectedTextColor = colors.textSecondary,
                ),
            )
        }
    }
}

/**
 * The one pill the whole bar shares, as on iPhone: where each glyph rests, and the pill's centre
 * gliding to the selected one. It shrinks as it leaves the old tab, travels small, and swells back
 * over the new icon as it arrives. Under Remove animations it snaps. Everything here is read in
 * the draw block, so the glide never recomposes the bar.
 */
@Stable
private class TabPill(val x: Animatable<Float, *>, val y: Animatable<Float, *>, val squeeze: Animatable<Float, *>) {
    var bar: LayoutCoordinates? = null
    val centers = mutableStateMapOf<String, Offset>()
    val presses = mutableMapOf<String, PressFeedbackState>()
    var selectedKey by mutableStateOf<String?>(null)
    var placed by mutableStateOf(false)

    /** Records a glyph's resting centre in the bar's own space; an unchanged one writes nothing. */
    fun place(key: String, glyph: LayoutCoordinates) {
        val bar = bar?.takeIf { it.isAttached } ?: return
        val center = bar.localPositionOf(glyph, glyph.size.center.toOffset())
        if (centers[key] != center) centers[key] = center
    }
}

@Composable
private fun rememberTabPill(selectedKey: String): TabPill {
    val motion = WaiveraTheme.motion
    val pill = remember { TabPill(Animatable(0f), Animatable(0f), Animatable(1f)) }
    SideEffect { pill.selectedKey = selectedKey }
    // The target is read here, outside composition, so a glide or a re-layout never recomposes
    // the bar. A new pick mid-glide redirects the pill from where it is, keeping its speed.
    LaunchedEffect(pill, motion) {
        snapshotFlow { pill.selectedKey?.let { pill.centers[it] } }
            .filterNotNull()
            .collectLatest { target ->
                if (!pill.placed || motion.reduced) {
                    pill.x.snapTo(target.x)
                    pill.y.snapTo(target.y)
                    pill.squeeze.snapTo(1f)
                    pill.placed = true
                    return@collectLatest
                }
                val from = pill.x.value
                if (abs(target.x - from) < 1f) {
                    coroutineScope {
                        launch { pill.squeeze.animateTo(1f, motion.pop) }
                        pill.y.animateTo(target.y, motion.glide())
                    }
                    return@collectLatest
                }
                coroutineScope {
                    launch { pill.y.animateTo(target.y, motion.glide()) }
                    launch {
                        pill.squeeze.animateTo(TAB_PILL_GLIDE_SCALE, motion.press())
                        // Swell back once most of the way there, so it lands full size on the icon.
                        snapshotFlow { (pill.x.value - from) / (target.x - from) }
                            .first { it >= TAB_PILL_SWELL_AT }
                        pill.squeeze.animateTo(1f, motion.pop)
                    }
                    pill.x.animateTo(target.x, motion.glide())
                }
            }
    }
    return pill
}

/** How small the pill travels between tabs, as a fraction of its resting size. */
private const val TAB_PILL_GLIDE_SCALE = 0.72f

/** How far along its glide the pill starts swelling back to full size. */
private const val TAB_PILL_SWELL_AT = 0.7f

/**
 * Draws the shared pill, solid [fill], centred on its gliding centre. It shrinks with the
 * selected tab's press, so the tab under the finger and its pill give together, and with its
 * own squeeze while it travels.
 */
private fun DrawScope.drawTabPill(pill: TabPill, fill: Color) {
    if (!pill.placed) return
    val scale = (pill.selectedKey?.let { pill.presses[it]?.value } ?: 1f) * pill.squeeze.value
    val width = WaiveraSizes.NavIndicatorWidth.toPx() * scale
    val height = WaiveraSizes.NavIndicatorHeight.toPx() * scale
    val center = Offset(pill.x.value, pill.y.value)
    drawRoundRect(
        color = fill,
        topLeft = Offset(center.x - width / 2f, center.y - height / 2f),
        size = Size(width, height),
        cornerRadius = CornerRadius(height / 2f),
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
    val shownBadge = rememberLatestNonNull(badge)
    Layout(
        modifier = modifier,
        content = {
            icon()
            // The count pops in, pops on each change and shrinks away when it is read.
            WaiveraPopVisibility(visible = badge != null) { shownBadge?.let { NavUnreadBadge(it) } }
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
    val pop = rememberPopScale(key = text)
    Box(
        modifier = Modifier
            .testTag(NAV_BADGE_TEST_TAG)
            .popScale(pop)
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
