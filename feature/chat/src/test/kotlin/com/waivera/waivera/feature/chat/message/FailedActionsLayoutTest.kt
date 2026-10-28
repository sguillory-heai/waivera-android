package com.waivera.waivera.feature.chat.message

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import com.waivera.waivera.core.designsystem.theme.WaiveraMotionScheme
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
 * A failed send's Discard, Edit and Retry share one line in a 360dp phone's bubble column at 100%
 * font, as on the v5 board, and when a column is too narrow for that, Retry keeps the top line and
 * the secondaries go under it: the primary never wraps below them.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotSdk.VALUE], qualifiers = "w360dp-h640dp-notnight-xhdpi")
class FailedActionsLayoutTest {
    @get:Rule
    val compose = createComposeRule()

    private fun render(width: Dp, showEdit: Boolean = true) {
        compose.setContent {
            WaiveraTheme(darkTheme = false, motion = WaiveraMotionScheme.Reduced, hapticsEnabled = false) {
                Box(Modifier.width(width)) {
                    FailedActionsRow(showEdit = showEdit, onDiscard = {}, onEdit = {}, onRetry = {})
                }
            }
        }
    }

    private fun bounds(label: String): DpRect = compose.onNodeWithText(label).getBoundsInRoot()

    @Test
    fun theThreeShareOneLineInTheSmallPhonesBubbleColumn() {
        render(SMALL_PHONE_COLUMN)
        val discard = bounds("Discard")
        val edit = bounds("Edit")
        val retry = bounds("Retry")
        assertEquals(discard.top, edit.top)
        assertEquals(edit.top, retry.top)
        assertTrue("Discard $discard is not before Edit $edit", discard.right <= edit.left)
        assertTrue("Edit $edit is not before Retry $retry", edit.right <= retry.left)
        assertTrue("Retry $retry runs past the column", retry.right <= SMALL_PHONE_COLUMN)
    }

    @Test
    fun withoutEditDiscardAndRetryShareOneLine() {
        render(SMALL_PHONE_COLUMN, showEdit = false)
        assertEquals(bounds("Discard").top, bounds("Retry").top)
    }

    @Test
    fun tooNarrowAColumnPutsRetryOnTopNeverBelow() {
        render(NARROW_COLUMN)
        val discard = bounds("Discard")
        val edit = bounds("Edit")
        val retry = bounds("Retry")
        assertTrue("Retry $retry is not above Discard $discard", retry.bottom <= discard.top)
        assertEquals(discard.top, edit.top)
    }

    @Test
    fun theArrangementFallsBackOnlyWhenTheLineDoesNotFit() {
        assertEquals(FailedActionsArrangement.OneRow, failedActionsArrangement(listOf(90, 60), 90, 4, 248))
        assertEquals(FailedActionsArrangement.RetryAbove, failedActionsArrangement(listOf(90, 60), 90, 4, 247))
        assertEquals(FailedActionsArrangement.RetryAbove, failedActionsArrangement(listOf(90, 60), 90, 4, 154))
        assertEquals(FailedActionsArrangement.Stacked, failedActionsArrangement(listOf(90, 60), 90, 4, 153))
        assertEquals(FailedActionsArrangement.OneRow, failedActionsArrangement(emptyList(), 90, 4, 90))
    }

    private companion object {
        // (360dp - two 16dp gutters) x the 0.82 bubble column, rounded down.
        val SMALL_PHONE_COLUMN = 268.dp
        val NARROW_COLUMN = 200.dp
    }
}
