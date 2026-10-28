package com.waivera.waivera.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.waivera.waivera.core.designsystem.theme.WaiveraMotionScheme
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
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
 * v5 decision 2, option C, as drawn: a filled button is lighter just under its top edge than at
 * its foot (the sheen is there), its foot is the plain fill (the sheen fades out), and the white
 * label keeps 4.5:1 against the lightest pixel of the sheen, on the violet primary and the red
 * destructive in both themes. The raised secondary is the plain surface. Probed at the start of
 * each button, clear of its centred label.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotSdk.VALUE], qualifiers = "w360dp-h640dp-notnight-xhdpi")
class ButtonSheenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun lightButtons_carryTheSheenAndKeepTheirLabelReadable() = check(dark = false)

    @Test
    fun darkButtons_carryTheSheenAndKeepTheirLabelReadable() = check(dark = true)

    private fun check(dark: Boolean) {
        val colors = waiveraColors(darkTheme = dark)
        compose.setContent {
            WaiveraTheme(darkTheme = dark, motion = WaiveraMotionScheme.Reduced, hapticsEnabled = false) {
                Column(
                    modifier = Modifier.padding(WaiveraSpacing.Medium),
                    verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.Large),
                ) {
                    WaiveraButton(text = "Send", onClick = {}, fullWidth = true, modifier = Modifier.testTag(PRIMARY))
                    WaiveraButton(
                        text = "Sign out",
                        onClick = {},
                        variant = WaiveraButtonVariant.Destructive,
                        fullWidth = true,
                        modifier = Modifier.testTag(DESTRUCTIVE),
                    )
                    WaiveraButton(
                        text = "Cancel",
                        onClick = {},
                        variant = WaiveraButtonVariant.Secondary,
                        fullWidth = true,
                        modifier = Modifier.testTag(SECONDARY),
                    )
                }
            }
        }
        compose.waitForIdle()
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        val theme = if (dark) "dark" else "light"

        for ((tag, fill) in listOf(PRIMARY to colors.accentFill, DESTRUCTIVE to colors.dangerFill)) {
            val top = pixels.probe(tag, fromTop = SHEEN_PROBE)
            val foot = pixels.probe(tag, fromTop = null)
            assertTrue("$theme $tag shows no sheen: top $top, foot $foot", top.luminance() > foot.luminance())
            assertTrue("$theme $tag foot is not the plain fill: $foot", foot.near(fill))
            val ratio = contrast(Color.White, top)
            assertTrue("$theme $tag label on the sheen is ${"%.2f".format(ratio)}:1", ratio >= TEXT_MIN)
        }
        val secondary = pixels.probe(SECONDARY, fromTop = null)
        assertTrue("$theme secondary is not the raised surface: $secondary", secondary.near(colors.surface))
    }

    /** A pixel [fromTop] below the button's top, or [FOOT_PROBE] above its bottom when null. */
    private fun PixelMap.probe(tag: String, fromTop: Dp?): Color {
        val bounds = compose.onNodeWithTag(tag, useUnmergedTree = true).getBoundsInRoot()
        val density = compose.density
        val x = with(density) { (bounds.left + START_PROBE).roundToPx() }
        val y = with(density) {
            if (fromTop != null) (bounds.top + fromTop).roundToPx() else (bounds.bottom - FOOT_PROBE).roundToPx()
        }
        return this[x, y]
    }

    private fun Color.near(other: Color): Boolean =
        maxOf(abs(red - other.red), abs(green - other.green), abs(blue - other.blue)) <= SAME_COLOR

    private fun contrast(a: Color, b: Color): Double {
        val la = a.luminance().toDouble()
        val lb = b.luminance().toDouble()
        return (maxOf(la, lb) + LUMINANCE_OFFSET) / (minOf(la, lb) + LUMINANCE_OFFSET)
    }

    private companion object {
        const val PRIMARY = "primary"
        const val DESTRUCTIVE = "destructive"
        const val SECONDARY = "secondary"

        // Clear of the rounded corner and the centred label; just under the 1dp edge; above the foot.
        val START_PROBE: Dp = 24.dp
        val SHEEN_PROBE: Dp = 3.dp
        val FOOT_PROBE: Dp = 6.dp

        // About 5/255: anti-aliasing stays under it; the sheen's lift at the top does not.
        const val SAME_COLOR = 0.02f
        const val TEXT_MIN = 4.5
        const val LUMINANCE_OFFSET = 0.05
    }
}
