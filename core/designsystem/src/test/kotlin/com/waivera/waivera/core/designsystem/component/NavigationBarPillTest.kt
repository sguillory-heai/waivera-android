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
 * The selected tab shows one solid tonal pill (v5 decision 5; UI8-01 for the single pill).
 * Material's own indicator used to sit inside the brand wash at a different width, which read as
 * a crescent at each end; v5 then took the gradient off every control. Probed along the pill's
 * middle row, beside the glyph: the pill fills its own width in the selected fill, nothing is
 * drawn just past it, every pixel between the glyph and the end is that one colour (no gradient
 * and no inner edge), and the tab that is not selected has no pill at all.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotSdk.VALUE], qualifiers = "w360dp-h640dp-notnight-xhdpi")
class NavigationBarPillTest {
    @get:Rule
    val compose = createComposeRule()

    private val colors = waiveraColors(darkTheme = false)

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
    fun theSelectedTabHasOneSolidTonalPill() {
        showBar()
        val icons = compose.onAllNodesWithTag(NAV_ICON_TEST_TAG, useUnmergedTree = true)
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        val density = compose.density
        val halfPill = WaiveraSizes.NavIndicatorWidth / 2

        val selected = icons[0].getBoundsInRoot()
        val centerX = (selected.left + selected.right) / 2
        val y = with(density) { ((selected.top + selected.bottom) / 2).roundToPx() }
        fun x(offset: Dp): Int = with(density) { (centerX + offset).roundToPx() }

        for (side in listOf(-1, 1)) {
            val inside = pixels[x((halfPill - EDGE_PROBE) * side), y]
            val outside = pixels[x((halfPill + EDGE_PROBE) * side), y]
            assertTrue(
                "the pill is not the selected fill just inside its end on side $side: $inside",
                inside.near(colors.selectedFill),
            )
            assertTrue("a second, wider pill shows past the end on side $side: $outside", outside.near(colors.navBar))
        }

        // Beside the glyph to just inside the end: one solid colour, so no gradient and no inner edge.
        val from = x(WaiveraSizes.Icon / 2 + GLYPH_CLEARANCE)
        val to = x(halfPill - EDGE_PROBE)
        for (px in from..to) {
            val pixel = pixels[px, y]
            assertTrue("the pill is not solid at x=$px: $pixel", pixel.near(colors.selectedFill))
        }

        // The tab that is not selected draws no pill beside its glyph.
        val other = icons[1].getBoundsInRoot()
        val otherY = with(density) { ((other.top + other.bottom) / 2).roundToPx() }
        val besideOther = with(density) { (other.right + GLYPH_CLEARANCE * 2).roundToPx() }
        val rest = pixels[besideOther, otherY]
        assertTrue("an unselected tab shows a pill: $rest", rest.near(colors.navBar))
    }

    private fun Color.near(other: Color): Boolean = distanceTo(other) <= SAME_COLOR

    private fun Color.distanceTo(other: Color): Float =
        maxOf(abs(red - other.red), abs(green - other.green), abs(blue - other.blue))

    private companion object {
        val EDGE_PROBE: Dp = 3.dp
        val GLYPH_CLEARANCE: Dp = 2.dp

        // About 5/255: anti-aliasing stays under it; a gradient's spread or a pill edge doesn't.
        const val SAME_COLOR = 0.02f
    }
}
