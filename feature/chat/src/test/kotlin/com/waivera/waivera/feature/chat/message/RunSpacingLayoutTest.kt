package com.waivera.waivera.feature.chat.message

import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.DpRect
import com.waivera.waivera.core.designsystem.theme.WaiveraMotionScheme
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.core.screenshottesting.ScreenshotSdk
import com.waivera.waivera.feature.chat.screenshot.WithDemoChatAvatars
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Bubbles of one run sit exactly [ChatDimens.RunGap] apart when their time lines are hidden
 * (owner's device review, 2026-10-30): a hidden line takes no height, and the first bubble's
 * avatar no longer hangs below a one-line bubble. A revealed line opens the run by its own height.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotSdk.VALUE], qualifiers = "w360dp-h640dp-notnight-xhdpi")
class RunSpacingLayoutTest {
    @get:Rule
    val compose = createComposeRule()

    private fun render(revealedKeys: Set<String> = emptySet()) {
        compose.setContent {
            WaiveraTheme(darkTheme = false, motion = WaiveraMotionScheme.Reduced, hapticsEnabled = false) {
                WithDemoChatAvatars { ChatPolishFixtures.Rows(ChatPolishFixtures.runs(), revealedKeys) }
            }
        }
        compose.waitForIdle()
    }

    private fun bubble(text: String): DpRect =
        compose.onNode(hasContentDescription(text, substring = true)).getBoundsInRoot()

    private fun gap(upper: String, lower: String): Float = (bubble(lower).top - bubble(upper).bottom).value

    @Test
    fun anIncomingRunIsHeldTogetherByTheRunGapUnderTheAvatar() {
        render()
        assertEquals(ChatDimens.RunGap.value, gap("They don't normally", "but some do"), TOLERANCE)
        assertEquals(ChatDimens.RunGap.value, gap("but some do", "why?"), TOLERANCE)
    }

    @Test
    fun myRunIsHeldTogetherByTheRunGap() {
        render()
        assertEquals(ChatDimens.RunGap.value, gap("Just reviewing some data", "and was curious"), TOLERANCE)
    }

    @Test
    fun aRevealedTimeOpensTheRunByItsLineOnly() {
        render(revealedKeys = setOf("m-12"))
        assertEquals(
            "an unrevealed line took room",
            ChatDimens.RunGap.value,
            gap("They don't normally", "but some do"),
            TOLERANCE,
        )
        assertTrue("the revealed line took no room", gap("but some do", "why?") > ChatDimens.RunGap.value + TOLERANCE)
    }

    @Test
    fun theLastBubbleOfARunKeepsItsTimeBeforeTheNextRun() {
        render()
        // "why?" ends Priya's run, so its time line sits between it and my first bubble.
        assertTrue(gap("why?", "Just reviewing some data") > ChatDimens.RunGap.value + TOLERANCE)
    }

    private companion object {
        const val TOLERANCE = 0.75f
    }
}
