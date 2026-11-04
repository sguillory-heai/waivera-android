package com.waivera.waivera.core.designsystem.component

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
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
 * The selected tab shows one solid violet pill (v5 decision 5, option A; UI8-01 for the single pill).
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
    fun theSelectedTabHasOneSolidVioletPill() {
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

    /**
     * As on iPhone, picking another tab sends the one pill across the bar: part way there it sits
     * between the two icons and is smaller than at rest, and it lands full size on the new icon.
     */
    @Test
    fun picking_a_tab_glides_the_pill_across_small_and_lands_full_size() {
        var selected by mutableStateOf("today")
        compose.mainClock.autoAdvance = false
        compose.setContent {
            WaiveraTheme(darkTheme = false, motion = WaiveraMotionScheme.Standard, hapticsEnabled = false) {
                WaiveraNavigationBar(
                    items = listOf(
                        NavigationBarItemModel(key = "today", label = "Today", icon = WaiveraIcons.Today),
                        NavigationBarItemModel(key = "chat", label = "Chat", icon = WaiveraIcons.Chat),
                    ),
                    selectedKey = selected,
                    onSelect = {},
                )
            }
        }
        compose.mainClock.advanceTimeBy(SETTLE_MILLIS)
        val icons = compose.onAllNodesWithTag(NAV_ICON_TEST_TAG, useUnmergedTree = true)
        val start = icons[0].getBoundsInRoot()
        val end = icons[1].getBoundsInRoot()
        val y = with(compose.density) { ((start.top + start.bottom) / 2).roundToPx() }
        val startX = with(compose.density) { ((start.left + start.right) / 2).toPx() }
        val endX = with(compose.density) { ((end.left + end.right) / 2).toPx() }
        val restWidth = pillSpan(y).let { it.last - it.first }

        selected = "chat"
        // Frame by frame, drawing each one as a device does; one long jump skips the glide's start.
        var mid = pillSpan(y)
        repeat((MID_GLIDE_MILLIS / FRAME_MILLIS).toInt()) {
            compose.mainClock.advanceTimeBy(FRAME_MILLIS)
            mid = pillSpan(y)
        }
        val midCenter = (mid.first + mid.last) / 2f
        assertTrue(
            "the pill jumped instead of gliding: centre $midCenter",
            midCenter > startX + 4 && midCenter < endX - 4,
        )
        assertTrue(
            "the pill is not smaller while it travels: ${mid.last - mid.first} vs $restWidth",
            mid.last - mid.first < restWidth,
        )

        compose.mainClock.advanceTimeBy(SETTLE_MILLIS)
        val landed = pillSpan(y)
        assertTrue("the pill did not land on the new icon", abs((landed.first + landed.last) / 2f - endX) <= 2f)
        assertTrue("the pill did not land full size", abs((landed.last - landed.first) - restWidth) <= 2)
    }

    /**
     * A tapped tab's screen is asked for only once the pill is well on its way, so building the
     * screen cannot freeze the pill at the old tab; with animations removed it is asked for at once.
     */
    @Test
    fun a_tapped_tab_opens_once_the_pill_is_under_way() {
        val picked = mutableListOf<String>()
        compose.mainClock.autoAdvance = false
        compose.setContent {
            var selected by remember { mutableStateOf("today") }
            WaiveraTheme(darkTheme = false, motion = WaiveraMotionScheme.Standard, hapticsEnabled = false) {
                WaiveraNavigationBar(
                    items = listOf(
                        NavigationBarItemModel(key = "today", label = "Today", icon = WaiveraIcons.Today),
                        NavigationBarItemModel(key = "chat", label = "Chat", icon = WaiveraIcons.Chat),
                    ),
                    selectedKey = selected,
                    onSelect = {
                        picked += it
                        selected = it
                    },
                )
            }
        }
        compose.mainClock.advanceTimeBy(SETTLE_MILLIS)
        val icons = compose.onAllNodesWithTag(NAV_ICON_TEST_TAG, useUnmergedTree = true)
        val y = with(compose.density) { icons[0].getBoundsInRoot().let { (it.top + it.bottom) / 2 }.roundToPx() }
        val startX = with(compose.density) { icons[0].getBoundsInRoot().let { (it.left + it.right) / 2 }.toPx() }

        icons[1].performClick()
        compose.mainClock.advanceTimeBy(FRAME_MILLIS)
        assertTrue("the screen was asked for before the pill moved", picked.isEmpty())

        var frames = 0
        while (picked.isEmpty() && frames < MAX_SWAP_FRAMES) {
            compose.mainClock.advanceTimeBy(FRAME_MILLIS)
            frames++
        }
        assertTrue("the tapped tab never opened", picked == listOf("chat"))
        val span = pillSpan(y)
        assertTrue(
            "the pill had not left the old tab when the screen opened",
            (span.first + span.last) / 2f > startX + 4,
        )
    }

    @Test
    fun with_animations_removed_a_tapped_tab_opens_at_once() {
        val picked = mutableListOf<String>()
        compose.setContent {
            WaiveraTheme(darkTheme = false, motion = WaiveraMotionScheme.Reduced, hapticsEnabled = false) {
                WaiveraNavigationBar(
                    items = listOf(
                        NavigationBarItemModel(key = "today", label = "Today", icon = WaiveraIcons.Today),
                        NavigationBarItemModel(key = "chat", label = "Chat", icon = WaiveraIcons.Chat),
                    ),
                    selectedKey = "today",
                    onSelect = { picked += it },
                )
            }
        }
        compose.onAllNodesWithTag(NAV_ICON_TEST_TAG, useUnmergedTree = true)[1].performClick()
        assertTrue(picked == listOf("chat"))
    }

    /** The pixels along row [y] drawn in the selected fill, first to last. */
    private fun pillSpan(y: Int): IntRange {
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        val hits = (0 until pixels.width).filter { pixels[it, y].near(colors.selectedFill) }
        return hits.first()..hits.last()
    }

    private fun Color.near(other: Color): Boolean = distanceTo(other) <= SAME_COLOR

    private fun Color.distanceTo(other: Color): Float =
        maxOf(abs(red - other.red), abs(green - other.green), abs(blue - other.blue))

    private companion object {
        val EDGE_PROBE: Dp = 3.dp
        val GLYPH_CLEARANCE: Dp = 2.dp

        // About 5/255: anti-aliasing stays under it; a gradient's spread or a pill edge doesn't.
        const val SAME_COLOR = 0.02f
        const val SETTLE_MILLIS = 1_500L
        const val MID_GLIDE_MILLIS = 160L
        const val FRAME_MILLIS = 16L
        const val MAX_SWAP_FRAMES = 20
    }
}
