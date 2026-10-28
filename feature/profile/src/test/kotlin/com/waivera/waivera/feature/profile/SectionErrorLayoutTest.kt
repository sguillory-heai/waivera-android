package com.waivera.waivera.feature.profile

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import com.waivera.waivera.core.designsystem.theme.WaiveraMotionScheme
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.core.network.error.WaiveraError
import com.waivera.waivera.core.screenshottesting.ScreenshotSdk
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * A profile section error on the small phone keeps its reason readable: no line ends inside a
 * word at 100% or 200%, at 200% "Try again" sits under the message instead of beside it
 * (UI5-03), and beside a wrapped reason it lines up with the first line (UI6-08).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ScreenshotSdk.VALUE], qualifiers = "w360dp-h640dp-notnight-xhdpi")
class SectionErrorLayoutTest {
    @get:Rule
    val compose = createComposeRule()

    private fun render(error: WaiveraError, fontScale: Float) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                WaiveraTheme(darkTheme = false, motion = WaiveraMotionScheme.Reduced, hapticsEnabled = false) {
                    SectionError(error = error, onRetry = {})
                }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun timeoutAtDefaultFont() = assertReadable(WaiveraError.Timeout(), DEFAULT_FONT, stacked = false)

    @Test
    fun timeoutAtLargeFont() = assertReadable(WaiveraError.Timeout(), LARGE_FONT, stacked = true)

    @Test
    fun offlineAtDefaultFont() = assertReadable(WaiveraError.Network(), DEFAULT_FONT, stacked = false)

    @Test
    fun offlineAtLargeFont() = assertReadable(WaiveraError.Network(), LARGE_FONT, stacked = true)

    /**
     * Beside a wrapped reason, "Try again" lines up with the reason's first line, as banner
     * actions do, instead of floating at the middle of the paragraph (UI6-08).
     */
    @Test
    fun tryAgainLinesUpWithTheFirstLineOfAWrappedReason() {
        val error = WaiveraError.Server(httpStatus = 503)
        render(error = error, fontScale = DEFAULT_FONT)
        val text = RuntimeEnvironment.getApplication().getString(error.messageRes)
        val message = compose.onNodeWithText(text, useUnmergedTree = true)
        val layout = message.fetchTextLayoutResult()
        assertTrue("\"$text\" did not wrap, so there is nothing to line up", layout.lineCount > 1)
        val top = message.getBoundsInRoot().top
        val retry = compose.onNodeWithText(TRY_AGAIN, useUnmergedTree = true).getBoundsInRoot()
        val retryCentre = (retry.top + retry.bottom) / 2
        with(compose.density) {
            val firstLineTop = top + layout.getLineTop(0).toDp()
            val firstLineBottom = top + layout.getLineBottom(0).toDp()
            assertTrue(
                "Try again is centred at $retryCentre, outside the first line ($firstLineTop to $firstLineBottom)",
                retryCentre in firstLineTop..firstLineBottom,
            )
        }
    }

    private fun assertReadable(error: WaiveraError, fontScale: Float, stacked: Boolean) {
        render(error = error, fontScale = fontScale)
        val text = RuntimeEnvironment.getApplication().getString(error.messageRes)
        val message = compose.onNodeWithText(text, useUnmergedTree = true).assertIsDisplayed()
        val layout = message.fetchTextLayoutResult()
        for (line in 0 until layout.lineCount - 1) {
            val end = layout.getLineEnd(line)
            assertTrue("\"$text\" breaks mid-word at $end", text[end - 1] == ' ' || text[end - 1] == '-')
        }
        val retry = compose.onNodeWithText(TRY_AGAIN, useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText(TRY_AGAIN).assertHasClickAction()
        val messageBounds = message.getBoundsInRoot()
        val retryBounds = retry.getBoundsInRoot()
        if (stacked) {
            assertTrue("Try again is beside the message", retryBounds.top >= messageBounds.bottom)
        } else {
            assertTrue("Try again is under the message", retryBounds.top < messageBounds.bottom)
        }
    }

    private fun SemanticsNodeInteraction.fetchTextLayoutResult(): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        return results.first()
    }

    private companion object {
        const val DEFAULT_FONT = 1f
        const val LARGE_FONT = 2f
        const val TRY_AGAIN = "Try again"
    }
}
