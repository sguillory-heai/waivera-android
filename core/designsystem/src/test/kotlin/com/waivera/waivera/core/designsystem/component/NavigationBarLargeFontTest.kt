package com.waivera.waivera.core.designsystem.component

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.waivera.waivera.core.designsystem.icon.WaiveraIcons
import com.waivera.waivera.core.designsystem.theme.WaiveraMotionScheme
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.core.screenshottesting.ScreenshotSdk
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The bottom bar at 200% on the small phone (UI10-02): the unread count is drawn whole inside
 * the bar (the bar clips anything past its top edge), it no longer sits on the Chat glyph, and
 * no tab label runs into the screen edge.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ScreenshotSdk.VALUE], qualifiers = "w360dp-h640dp-notnight-xhdpi")
class NavigationBarLargeFontTest {
    @get:Rule
    val compose = createComposeRule()

    private var chatBadge by mutableIntStateOf(4)

    private fun showBar(fontScale: Float) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                WaiveraTheme(darkTheme = false, motion = WaiveraMotionScheme.Reduced, hapticsEnabled = false) {
                    WaiveraNavigationBar(
                        items = listOf(
                            NavigationBarItemModel(key = "today", label = "Today", icon = WaiveraIcons.Today),
                            NavigationBarItemModel(
                                key = "chat",
                                label = "Chat",
                                icon = WaiveraIcons.Chat,
                                badgeCount = chatBadge,
                            ),
                            NavigationBarItemModel(key = "schedule", label = "Schedule", icon = WaiveraIcons.Calendar),
                        ),
                        selectedKey = "today",
                        onSelect = {},
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun theCountIsWholeInsideTheBarAndOffTheGlyphAtLargeFont() {
        showBar(fontScale = LARGE_FONT)
        for (count in listOf(4, 120)) {
            chatBadge = count
            compose.waitForIdle()
            val bar = compose.onNodeWithTag(NAV_BAR_TEST_TAG).fetchSemanticsNode().boundsInRoot
            // Unclipped placement: boundsInRoot would already be cut to the bar.
            val badge = compose.onNodeWithTag(NAV_BADGE_TEST_TAG, useUnmergedTree = true).fetchSemanticsNode()
            val top = badge.positionInRoot.y
            val left = badge.positionInRoot.x
            val bottom = top + badge.size.height
            assertTrue("the $count badge starts at $top, above the bar's top at ${bar.top}", top >= bar.top)
            assertTrue("the $count badge ends at $bottom, below the bar at ${bar.bottom}", bottom <= bar.bottom)
            val chatIcon = compose.onAllNodesWithTag(NAV_ICON_TEST_TAG, useUnmergedTree = true)
                .fetchSemanticsNodes()[1]
            val glyphEnd = chatIcon.unclippedRight()
            assertTrue("the $count badge starts at $left, over the glyph ending at $glyphEnd", left >= glyphEnd - 1f)
        }
    }

    @Test
    fun noTabLabelRunsIntoTheScreenEdgeAtLargeFont() {
        showBar(fontScale = LARGE_FONT)
        val window = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val gutter = with(compose.density) { EDGE_GUTTER.toPx() }
        for (label in listOf("Today", "Chat", "Schedule")) {
            val node = compose.onNodeWithText(label, useUnmergedTree = true).fetchSemanticsNode()
            val left = node.positionInRoot.x
            val right = node.unclippedRight()
            assertTrue("\"$label\" starts at $left, within $gutter of the left edge", left - window.left >= gutter)
            assertTrue("\"$label\" ends at $right, within $gutter of the right edge", window.right - right >= gutter)
        }
    }

    private fun SemanticsNode.unclippedRight(): Float = positionInRoot.x + size.width

    private companion object {
        const val LARGE_FONT = 2f
        val EDGE_GUTTER = 8.dp
    }
}
