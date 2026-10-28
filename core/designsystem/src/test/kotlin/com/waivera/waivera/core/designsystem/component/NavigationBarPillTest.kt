package com.waivera.waivera.core.designsystem.component

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.waivera.waivera.core.designsystem.icon.WaiveraIcons
import com.waivera.waivera.core.designsystem.theme.WaiveraMotionScheme
import com.waivera.waivera.core.designsystem.theme.WaiveraSizes
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.core.designsystem.theme.waiveraColors
import com.waivera.waivera.core.screenshottesting.ScreenshotSdk
import kotlin.math.abs
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The selected tab shows one pill (UI8-01): Material's own indicator used to sit inside the
 * brand wash at a different width, which read as a crescent at each end. Probed along the
 * pill's middle row, beside the glyph: the pill fills its own width, nothing is drawn just
 * past it, there is no inner edge, and the end is pink, not gold.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotSdk.VALUE], qualifiers = "w360dp-h640dp-notnight-xhdpi")
class NavigationBarPillTest {
    @get:Rule
    val compose = createComposeRule()

    private val surface = waiveraColors(darkTheme = false).surface

    private fun showBar() {
        compose.setContent {
            WaiveraTheme(darkTheme = false, motion = WaiveraMotionScheme.Reduced, hapticsEnabled = false) {
                WaiveraNavigationBar(
                    items = listOf(
                        NavigationBarItemModel(key = "today", label = "Today", icon = WaiveraIcons.Today),
                        NavigationBarItemModel(key = "chat", label = "Chat", icon = WaiveraIcons.Chat),
                    ),
                    selectedKey = "today",
                    onSelect = {},
                )
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun theSelectedTabHasOnePillEndingOnPink() {
        showBar()
        val icon = compose.onAllNodesWithTag(NAV_ICON_TEST_TAG, useUnmergedTree = true)[0].getBoundsInRoot()
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        val density = compose.density
        val centerX = (icon.left + icon.right) / 2
        val y = with(density) { ((icon.top + icon.bottom) / 2).roundToPx() }
        val halfPill = WaiveraSizes.NavIndicatorWidth / 2
        fun x(offset: Dp): Int = with(density) { (centerX + offset).roundToPx() }

        for (side in listOf(-1, 1)) {
            val inside = pixels[x((halfPill - EDGE_PROBE) * side), y]
            val outside = pixels[x((halfPill + EDGE_PROBE) * side), y]
            assertTrue("no pill just inside its end on side $side: $inside", !inside.near(surface))
            assertTrue("a second, wider pill shows past the end on side $side: $outside", outside.near(surface))
        }

        // Beside the glyph to just inside the end: a smooth gradient, no inner pill's edge.
        val from = x(WaiveraSizes.Icon / 2 + GLYPH_CLEARANCE)
        val to = x(halfPill - EDGE_PROBE)
        for (px in from until to) {
            val step = pixels[px, y].distanceTo(pixels[px + 1, y])
            assertTrue("an edge inside the pill at x=$px (step $step)", step <= MAX_GRADIENT_STEP)
        }

        // Pink over the soft accent keeps blue above green; gold pulls blue well under it.
        val end = pixels[to, y]
        assertTrue("the pill ends on gold, not pink: $end", end.blue >= end.green)
    }

    private fun Color.near(other: Color): Boolean = distanceTo(other) <= SAME_COLOR

    private fun Color.distanceTo(other: Color): Float =
        maxOf(abs(red - other.red), abs(green - other.green), abs(blue - other.blue))

    private companion object {
        val EDGE_PROBE: Dp = 3.dp
        val GLYPH_CLEARANCE: Dp = 2.dp

        // About 5/255: anti-aliasing and the gradient's own step stay under it; a pill edge doesn't.
        const val MAX_GRADIENT_STEP = 0.02f
        const val SAME_COLOR = 0.02f
    }
}
