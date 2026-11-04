package com.waivera.waivera.feature.tasks

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.core.screenshottesting.ScreenshotSdk
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Show more and Show less move the entry's height over time instead of jumping. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ScreenshotSdk.VALUE], qualifiers = "w360dp-h640dp-notnight-xhdpi")
class ActivityNoteMotionTest {
    @get:Rule
    val compose = createComposeRule()

    private val row = TaskPreviewData.activity.last()

    @Test
    fun opening_and_closing_animate_through_in_between_heights() {
        compose.setContent { WaiveraTheme { ActivityItem(row, Modifier.testTag(ENTRY)) } }
        val closed = height()

        compose.mainClock.autoAdvance = false
        compose.onNodeWithText(MORE).performClick()
        val opening = frames()
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        val open = height()
        assertTrue(open > closed, "opened $open should be taller than closed $closed")
        assertTrue(opening.any { it > closed && it < open }, "opening went $opening, no step between $closed and $open")

        compose.mainClock.autoAdvance = false
        compose.onNodeWithText(LESS).performClick()
        val closing = frames()
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertTrue(closing.any { it > closed && it < open }, "closing went $closing, no step between $closed and $open")
        assertEquals(closed, height())
        compose.onNodeWithText(MORE).assertExists()
    }

    /** Heights over the next [FRAMES] frames: about a third of a second. */
    private fun frames(): List<Dp> = List(FRAMES) {
        compose.mainClock.advanceTimeByFrame()
        height()
    }

    private fun height(): Dp = compose.onNodeWithTag(ENTRY).getUnclippedBoundsInRoot().let { it.bottom - it.top }

    private companion object {
        const val ENTRY = "entry"
        const val MORE = "Show more"
        const val LESS = "Show less"
        const val FRAMES = 20
    }
}
