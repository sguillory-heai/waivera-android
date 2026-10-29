package com.waivera.waivera.core.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.waivera.waivera.core.designsystem.theme.WaiveraMotionScheme
import com.waivera.waivera.core.designsystem.theme.WaiveraNameFit
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.core.screenshottesting.ScreenshotSdk
import com.waivera.waivera.core.screenshottesting.renderedFontSize
import com.waivera.waivera.core.testing.LongFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Chat names on one line (v7, item 3), on the small phone's row width: the 60-character name and
 * the unbroken 40-character name stay on one line at 100% and 200%, shrink no lower than
 * [WaiveraNameFit.MIN_FONT_SCALE] of the style's size, and end in "…" when even that does not fit.
 * Short names keep the full size, and TalkBack gets the whole name.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotSdk.VALUE], qualifiers = "w360dp-h640dp-notnight-xhdpi")
class WaiveraOneLineNameTest {
    @get:Rule
    val compose = createComposeRule()

    private var baseSize = 0f
    private var shown by mutableStateOf<Shown?>(null)

    private data class Shown(val name: String, val fontScale: Float, val width: Dp)

    /** One composition per test, as the rule allows; later calls change what it shows. */
    private fun layout(name: String, fontScale: Float, width: Dp = ROW_WIDTH): TextLayoutResult {
        val first = shown == null
        shown = Shown(name, fontScale, width)
        if (first) {
            compose.setContent {
                val current = shown ?: return@setContent
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, current.fontScale)) {
                    WaiveraTheme(darkTheme = false, motion = WaiveraMotionScheme.Reduced, hapticsEnabled = false) {
                        val style = MaterialTheme.typography.titleSmall
                        baseSize = style.fontSize.value
                        Column {
                            WaiveraOneLineName(
                                text = current.name,
                                style = style,
                                modifier = Modifier.width(current.width).testTag(NAME),
                            )
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        val results = mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag(NAME).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        return results.first()
    }

    /** The size auto-size drew at: the layout keeps the declared style, so it is found by matching. */
    private fun drawnSize(layout: TextLayoutResult): Float = requireNotNull(
        layout.renderedFontSize(floor = (baseSize * WaiveraNameFit.MIN_FONT_SCALE).sp, step = WaiveraNameFit.Step),
    ) { "\"${layout.layoutInput.text}\" was drawn at no size between the floor and its style" }.value

    private fun assertOneLineWithinTheFloor(name: String, fontScale: Float) {
        val layout = layout(name, fontScale)
        assertEquals("\"$name\" wraps at ${fontScale}x", 1, layout.lineCount)
        val size = drawnSize(layout)
        assertTrue("\"$name\" grew past its style at ${fontScale}x: $size", size <= baseSize + EPSILON)
        assertTrue(
            "\"$name\" shrank to $size at ${fontScale}x, under the floor",
            size >= baseSize * WaiveraNameFit.MIN_FONT_SCALE - EPSILON,
        )
    }

    @Test
    fun theSixtyCharacterNameIsOneLineAtDefaultSize() = assertOneLineWithinTheFloor(LongFixtures.NAME_60, 1f)

    @Test
    fun theSixtyCharacterNameIsOneLineAtLargeFont() = assertOneLineWithinTheFloor(LongFixtures.NAME_60, 2f)

    @Test
    fun theUnbrokenNameIsOneLineAtDefaultSize() = assertOneLineWithinTheFloor(LongFixtures.NAME_UNBROKEN_40, 1f)

    @Test
    fun theUnbrokenNameIsOneLineAtLargeFont() = assertOneLineWithinTheFloor(LongFixtures.NAME_UNBROKEN_40, 2f)

    @Test
    fun aNameThatStillDoesNotFitEndsInAnEllipsisAtTheFloor() {
        val layout = layout(LongFixtures.NAME_60, fontScale = 2f)
        assertTrue("the name is not ellipsized", layout.isLineEllipsized(0))
        assertEquals(
            "the name is ellipsized before it reaches the floor",
            baseSize * WaiveraNameFit.MIN_FONT_SCALE,
            drawnSize(layout),
            WaiveraNameFit.Step.value + EPSILON,
        )
    }

    @Test
    fun aNameThatFitsWhenSlightlySmallerShrinksInsteadOfCutting() {
        val full = layout(MEDIUM, fontScale = 1f)
        assertFalse("\"$MEDIUM\" does not fit the row at full size", full.isLineEllipsized(0))
        // About 7% too narrow for the name at full size: it fits once shrunk, so nothing is cut.
        val snugWidth = with(compose.density) { (full.multiParagraph.intrinsics.maxIntrinsicWidth * SNUG).toDp() }
        val snug = layout(MEDIUM, fontScale = 1f, width = snugWidth)
        assertFalse("the name is cut instead of shrunk", snug.isLineEllipsized(0))
        assertEquals(1, snug.lineCount)
        assertTrue("the name did not shrink", drawnSize(snug) < baseSize)
    }

    @Test
    fun shortNamesKeepTheFullSize() {
        listOf(1f, 2f).forEach { scale ->
            val layout = layout(SHORT, fontScale = scale)
            assertEquals(1, layout.lineCount)
            assertFalse("\"$SHORT\" is ellipsized at ${scale}x", layout.isLineEllipsized(0))
            assertEquals(baseSize, drawnSize(layout), EPSILON)
        }
    }

    @Test
    fun talkBackReadsTheWholeName() {
        layout(LongFixtures.NAME_60, fontScale = 2f)
        val text = compose.onNodeWithTag(NAME).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)
        assertEquals(LongFixtures.NAME_60, text?.joinToString("") { it.text })
    }

    private companion object {
        const val NAME = "name"
        const val SHORT = "Priya Raman"
        const val EPSILON = 0.01f

        /** A list row's text column on the 360dp phone: gutters, a 44dp avatar, gaps, a chevron. */
        val ROW_WIDTH = 232.dp

        const val MEDIUM = "Jordan Lee-Castellanos"
        const val SNUG = 0.93f
    }
}
