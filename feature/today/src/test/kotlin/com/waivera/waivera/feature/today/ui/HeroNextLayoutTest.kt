package com.waivera.waivera.feature.today.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.waivera.waivera.core.designsystem.theme.WaiveraMotionScheme
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.core.screenshottesting.ScreenshotSdk
import com.waivera.waivera.feature.today.R
import java.time.LocalTime
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The hero's Next line at 200% in the width it gets inside the hero card on each phone
 * (UI11-01): the kicker "NEXT · 11:00 AM" stays on one line, the title is whole, and the
 * countdown chip sits under the title instead of taking the row's end.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotSdk.VALUE], qualifiers = "w412dp-h915dp-notnight-xhdpi")
class HeroNextLayoutTest {
    @get:Rule
    val compose = createComposeRule()

    private var cardWidth by mutableStateOf(SMALL_PHONE_CARD)

    private val formats = TodayFormats(Locale.US)
    private val kicker = RuntimeEnvironment.getApplication()
        .getString(R.string.feature_today_brand_next, formats.time(LocalTime.of(11, 0)))

    private fun show() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, LARGE_FONT)) {
                WaiveraTheme(darkTheme = false, motion = WaiveraMotionScheme.Reduced, hapticsEnabled = false) {
                    Box(Modifier.width(cardWidth)) {
                        NextLine(kicker = kicker, title = TITLE, countdown = COUNTDOWN)
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun layoutOf(text: String): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(text, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        return results.first()
    }

    @Test
    fun theTitleIsWholeAndTheKickerIsOneLineAtLargeFont() {
        show()
        for (width in listOf(SMALL_PHONE_CARD, LARGE_PHONE_CARD)) {
            cardWidth = width
            compose.waitForIdle()
            val kickerLayout = layoutOf(formats.upper(kicker))
            assertEquals(1, kickerLayout.lineCount, "the kicker breaks at $width")
            val title = layoutOf(TITLE)
            assertFalse(title.isLineEllipsized(title.lineCount - 1), "\"$TITLE\" is ellipsized at $width")
            assertFalse(title.didOverflowHeight, "\"$TITLE\" is cut at $width")
        }
    }

    @Test
    fun theCountdownSitsUnderTheTitleAtLargeFont() {
        show()
        for (width in listOf(SMALL_PHONE_CARD, LARGE_PHONE_CARD)) {
            cardWidth = width
            compose.waitForIdle()
            val title = compose.onNodeWithText(TITLE, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            val chip = compose.onNodeWithText(COUNTDOWN, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertTrue(
                chip.top >= title.bottom - 1f,
                "the chip at ${chip.top} is beside the title ending ${title.bottom}",
            )
            // The node is the chip's text, which sits inside the pill's own Small side padding,
            // so the pill starts under the title when its text starts one Small in from it.
            val pillLeft = chip.left - with(compose.density) { WaiveraSpacing.Small.toPx() }
            assertTrue(
                pillLeft <= title.left + 1f,
                "the chip starts at $pillLeft, not under the title at ${title.left}",
            )
        }
    }

    private companion object {
        const val LARGE_FONT = 2f
        const val TITLE = "Client home visit"
        const val COUNTDOWN = "in 30 min"

        /** 360dp less the 16dp gutters and the compact card's 12dp padding. */
        val SMALL_PHONE_CARD: Dp = 304.dp

        /** 412dp less the 16dp gutters and the card's 18dp padding. */
        val LARGE_PHONE_CARD: Dp = 344.dp
    }
}
