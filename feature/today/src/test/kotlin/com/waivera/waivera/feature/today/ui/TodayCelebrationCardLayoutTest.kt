package com.waivera.waivera.feature.today.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import com.waivera.waivera.core.designsystem.theme.WaiveraMotionScheme
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.core.screenshottesting.ScreenshotSdk
import com.waivera.waivera.feature.today.R
import com.waivera.waivera.feature.today.model.CelebrationItem
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
 * Today's celebration cards on the small phone at 200% (`today_brand_moments_small_*_font200`,
 * UI11-03): on the birthday and the anniversary card alike, "Send wishes" is one whole line,
 * and the button starts at the card's content start instead of under the sentence.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotSdk.VALUE], qualifiers = "w360dp-h640dp-notnight-xhdpi")
class TodayCelebrationCardLayoutTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = RuntimeEnvironment.getApplication()
    private val label = context.getString(R.string.feature_today_brand_send_wishes)

    private val cards = listOf(
        CelebrationItem("usr_jordan_lee", "Jordan Lee", isBirthday = false, anniversaryYears = 5),
        CelebrationItem("usr_sam", "Sam Ortiz", isBirthday = true, anniversaryYears = null),
    )

    private fun show() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, LARGE_FONT)) {
                WaiveraTheme(darkTheme = false, motion = WaiveraMotionScheme.Reduced, hapticsEnabled = false) {
                    Column(Modifier.fillMaxWidth()) {
                        cards.forEach { item -> CelebrationCard(item = item, onOpen = {}, onSendWishes = {}) }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun sendWishesIsOneWholeLineOnEveryCardAtLargeFont() {
        show()
        val labels = compose.onAllNodesWithText(label, useUnmergedTree = true)
        assertEquals(cards.size, labels.fetchSemanticsNodes().size, "one Send wishes per card")
        cards.indices.forEach { index ->
            val results = mutableListOf<TextLayoutResult>()
            labels[index].performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
            val layout = results.first()
            val name = cards[index].name
            assertEquals(1, layout.lineCount, "\"$label\" wraps on $name's card")
            assertFalse(layout.isLineEllipsized(0), "\"$label\" is ellipsized on $name's card")
            assertFalse(layout.didOverflowHeight, "\"$label\" is cut on $name's card")
        }
    }

    @Test
    fun sendWishesStartsAtTheCardsContentStartAtLargeFont() {
        show()
        // The card sits in the 16dp gutter and pads its content by 16dp.
        val contentStart = with(compose.density) { (WaiveraSpacing.Gutter + WaiveraSpacing.Medium).toPx() }
        cards.forEach { item ->
            val spoken = context.getString(R.string.feature_today_brand_send_wishes_a11y, item.name)
            val button = compose.onNode(hasContentDescription(spoken) and hasClickAction())
                .fetchSemanticsNode()
                .boundsInRoot
            assertTrue(
                button.left <= contentStart + 1f,
                "Send wishes on ${item.name}'s card starts at ${button.left}px, past ${contentStart}px",
            )
        }
    }

    private companion object {
        const val LARGE_FONT = 2f
    }
}
