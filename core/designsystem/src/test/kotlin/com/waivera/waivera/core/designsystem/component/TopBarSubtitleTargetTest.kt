package com.waivera.waivera.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import com.waivera.waivera.core.designsystem.theme.WaiveraMotionScheme
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.core.screenshottesting.ScreenshotSdk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The header's clickable "N members" line (v7 follow-up, item 3): the header is exactly as tall
 * as with a plain subtitle (its height before the line became a button), while the line's touch
 * target is at least 48dp tall and takes a tap just above the drawn text.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotSdk.VALUE], qualifiers = "w360dp-h640dp-notnight-xhdpi")
class TopBarSubtitleTargetTest {
    @get:Rule
    val compose = createComposeRule()

    private var clickable by mutableStateOf(false)
    private var clicks = 0

    private fun show(fontScale: Float) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                WaiveraTheme(darkTheme = false, motion = WaiveraMotionScheme.Reduced, hapticsEnabled = false) {
                    Box(Modifier.testTag(BAR)) {
                        WaiveraTopBar(
                            title = "Priya Raman, Jordan Lee",
                            titleIsName = true,
                            subtitle = "3 members",
                            onSubtitleClick = if (clickable) ({ clicks++ }) else null,
                            subtitleClickLabel = "Show members",
                            onBack = {},
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun barHeight(): Int = compose.onNodeWithTag(BAR).fetchSemanticsNode().size.height

    private fun assertSameHeightAsAPlainSubtitle(fontScale: Float) {
        show(fontScale)
        val plain = barHeight()
        clickable = true
        compose.waitForIdle()
        assertEquals("the header grew when its subtitle became a button at ${fontScale}x", plain, barHeight())
    }

    private fun assertFullTouchTarget(fontScale: Float) {
        clickable = true
        show(fontScale)
        val button = compose.onNodeWithTag(TOP_BAR_SUBTITLE_BUTTON_TAG)
        button.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        val target = button.fetchSemanticsNode().boundsInRoot
        val minTarget = with(compose.density) { WaiveraSpacing.MinTouchTarget.toPx() }
        assertTrue("the target is ${target.height}px tall, under $minTarget", target.height >= minTarget - 1f)

        val text = compose.onNodeWithTag(TOP_BAR_SUBTITLE_TAG, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("the drawn line is as tall as the target", text.height < target.height)
        // A tap above the drawn line, still inside the target, opens the members.
        val above = Offset(text.left + text.width / 2f, text.top - (text.top - target.top) / 2f)
        compose.onRoot().performTouchInput { click(above) }
        compose.waitForIdle()
        assertEquals(1, clicks)
    }

    @Test
    fun theHeaderKeepsItsHeightWithAMembersButton() = assertSameHeightAsAPlainSubtitle(fontScale = 1f)

    @Test
    fun theHeaderKeepsItsHeightWithAMembersButtonAtLargeFont() = assertSameHeightAsAPlainSubtitle(fontScale = 2f)

    @Test
    fun theMembersLineHasAFullTouchTarget() = assertFullTouchTarget(fontScale = 1f)

    @Test
    fun theMembersLineHasAFullTouchTargetAtLargeFont() = assertFullTouchTarget(fontScale = 2f)

    /** The 48dp target overhangs the title, so the press shows on the drawn line alone. */
    @Test
    fun pressingTheMembersLineLeavesTheTitleUntouched() {
        clickable = true
        show(fontScale = 1f)
        val bar = compose.onNodeWithTag(BAR)
        val barTop = bar.fetchSemanticsNode().boundsInRoot.top
        val line = compose.onNodeWithTag(TOP_BAR_SUBTITLE_TAG, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val above = (line.top - barTop).toInt()
        val lineRows = above until (line.bottom - barTop).toInt()
        val rest = bar.captureToImage().asAndroidBitmap()

        compose.onNodeWithTag(TOP_BAR_SUBTITLE_BUTTON_TAG).performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(PRESS_MILLIS)
        val held = bar.captureToImage().asAndroidBitmap()
        compose.onNodeWithTag(TOP_BAR_SUBTITLE_BUTTON_TAG).performTouchInput { up() }

        fun changed(rows: IntRange) = rows.count { y ->
            (0 until rest.width).any { x -> rest.getPixel(x, y) != held.getPixel(x, y) }
        }
        assertTrue("the press drew nothing on the line", changed(lineRows) > 0)
        assertEquals("the press painted over the title", 0, changed(0 until above))
    }

    private companion object {
        const val PRESS_MILLIS = 300L
        const val BAR = "bar"
    }
}
