package com.waivera.waivera.feature.chat.newchat

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.waivera.waivera.core.designsystem.component.WaiveraSheetFrame
import com.waivera.waivera.core.designsystem.theme.WaiveraMotionScheme
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.core.screenshottesting.ScreenshotSdk
import kotlin.math.abs
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Picking the first person in New chat opens the "1 selected" line above the list. The list
 * moves down with it over several frames instead of jumping under the finger in one (M3-05).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ScreenshotSdk.VALUE], qualifiers = "w360dp-h640dp-notnight-xhdpi")
class NewChatSelectionMotionTest {
    @get:Rule
    val compose = createComposeRule()

    private val people = listOf(
        PersonOptionUi("usr_jordan_lee", "Jordan Lee", selected = false),
        PersonOptionUi("usr_mei_chen", "Mei Chen", selected = false),
        PersonOptionUi("usr_priya_raman", "Priya Raman", selected = false),
    )

    private fun show(): (NewChatUiState) -> Unit {
        var setState: (NewChatUiState) -> Unit = {}
        compose.setContent {
            WaiveraTheme(darkTheme = false, motion = WaiveraMotionScheme.Standard, hapticsEnabled = false) {
                var state by remember { mutableStateOf(NewChatUiState(people = people, isLoading = false)) }
                setState = { state = it }
                WaiveraSheetFrame(title = "New chat") {
                    NewChatSheetContent(
                        state = state,
                        avatars = emptyMap(),
                        onQueryChange = {},
                        onToggle = {},
                        onSubmit = {},
                        onRetryLoad = {},
                        onVisiblePeople = {},
                        skeletonDelayMillis = 0,
                    )
                }
            }
        }
        compose.waitForIdle()
        return { next ->
            compose.runOnUiThread {
                setState(next)
                Snapshot.sendApplyNotifications()
            }
        }
    }

    private fun topOf(name: String): Float =
        compose.onNodeWithText(name, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.top

    @Test
    fun selectingTheFirstPersonMovesTheListSmoothlyRatherThanInOneFrame() {
        compose.mainClock.autoAdvance = false
        val setState = show()
        compose.mainClock.advanceTimeBy(SETTLE_MILLIS)
        val tops = mutableListOf(topOf(WATCHED))
        setState(
            NewChatUiState(
                people = people.mapIndexed { index, person -> person.copy(selected = index == 0) },
                selectedCount = 1,
                isLoading = false,
            ),
        )
        repeat(WATCHED_FRAMES) {
            compose.mainClock.advanceTimeBy(FRAME_MILLIS)
            tops += topOf(WATCHED)
        }
        compose.mainClock.advanceTimeBy(SETTLE_MILLIS)
        tops += topOf(WATCHED)
        val travel = abs(tops.last() - tops.first())
        assertTrue("the \"1 selected\" line never opened above the list: $tops", travel > MIN_TRAVEL_PX)
        val steps = tops.zipWithNext { a, b -> abs(b - a) }
        assertTrue(
            "the list jumped ${steps.max()} of $travel px in one frame: $tops",
            steps.max() <= travel * MAX_STEP_SHARE,
        )
    }

    private companion object {
        const val WATCHED = "Mei Chen"
        const val FRAME_MILLIS = 16L
        const val SETTLE_MILLIS = 1_000L
        const val WATCHED_FRAMES = 24

        /** The line and its gap are about 28dp, 56px at xhdpi. */
        const val MIN_TRAVEL_PX = 30f

        /** The layout spring moves about a sixth of the way in its biggest frame; a cut moves all of it. */
        const val MAX_STEP_SHARE = 0.4f
    }
}
