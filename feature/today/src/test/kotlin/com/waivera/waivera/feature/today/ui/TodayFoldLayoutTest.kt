package com.waivera.waivera.feature.today.ui

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onRoot
import com.waivera.waivera.core.designsystem.theme.WaiveraMotionScheme
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.core.screenshottesting.ScreenshotSdk
import com.waivera.waivera.feature.today.R
import com.waivera.waivera.feature.today.screenshot.TodayDemo
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * On the 640dp phone at 100% font, the working-day Today (`today_populated_small_*_font100`)
 * shows its hero and then two whole Needs you cards above the fold (UI10-01): the payoff of the
 * hero is not cut off. The first section title also sits close under the sheet's curve, and
 * its "Show all" link keeps a full 48dp target.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ScreenshotSdk.VALUE], qualifiers = "w360dp-h640dp-notnight-xhdpi")
class TodayFoldLayoutTest {
    @get:Rule
    val compose = createComposeRule()

    private fun show() {
        compose.setContent {
            WaiveraTheme(darkTheme = false, motion = WaiveraMotionScheme.Reduced, hapticsEnabled = false) {
                TodayContent(state = TodayDemo.state(), callbacks = TodayPreviewStates.callbacks())
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun twoWholeNeedsYouCardsShowAboveTheFold() {
        show()
        val window = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val cards = compose.onAllNodesWithTag(NEEDS_YOU_CARD_TEST_TAG, useUnmergedTree = true).fetchSemanticsNodes()
        assertTrue(cards.size >= 2, "only ${cards.size} Needs you card is composed on the first screen")
        val second = cards[1].boundsInRoot
        assertTrue(second.height > 0f, "the second Needs you card has no height")
        assertTrue(
            second.bottom <= window.bottom,
            "the second Needs you card ends at ${second.bottom}px, below the window at ${window.bottom}px",
        )
    }

    @Test
    fun showAllKeepsAFullTargetUnderACompactHeader() {
        show()
        val label = RuntimeEnvironment.getApplication().getString(R.string.feature_today_brand_show_all)
        val link = compose.onNode(hasText(label), useUnmergedTree = false).fetchSemanticsNode().boundsInRoot
        val minimum = with(compose.density) { WaiveraSpacing.MinTouchTarget.toPx() }
        assertTrue(link.height >= minimum - 1f, "Show all is ${link.height}px tall, under $minimum")
        val firstCard = compose.onAllNodesWithTag(NEEDS_YOU_CARD_TEST_TAG, useUnmergedTree = true)
            .fetchSemanticsNodes()
            .first()
            .boundsInRoot
        // The card's own 4dp inset is not a tap target, so the link may reach into it but no further.
        val inset = with(compose.density) { WaiveraSpacing.ExtraSmall.toPx() }
        assertTrue(
            link.bottom <= firstCard.top + inset + 1f,
            "Show all's target ends at ${link.bottom}px, inside the first card's taps from ${firstCard.top + inset}px",
        )
    }
}
