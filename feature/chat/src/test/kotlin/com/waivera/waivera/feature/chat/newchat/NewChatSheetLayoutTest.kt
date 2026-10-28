package com.waivera.waivera.feature.chat.newchat

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import com.waivera.waivera.core.designsystem.component.WaiveraSheetFrame
import com.waivera.waivera.core.designsystem.theme.WaiveraMotionScheme
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.core.network.error.WaiveraError
import com.waivera.waivera.core.screenshottesting.ScreenshotSdk
import com.waivera.waivera.feature.chat.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The new-chat sheet at 200% on the small phone. The subtitle is left out, so the load error
 * fits with "Try again" in view (UI6-05). The empty state still scrolls in the space under the
 * header, so its message is whole (UI5-07).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotSdk.VALUE], qualifiers = "w360dp-h640dp-notnight-xhdpi")
class NewChatSheetLayoutTest {
    private val emptyMessage get() = RuntimeEnvironment.getApplication().getString(R.string.feature_chat_newchat_empty)
    private val subtitle get() = RuntimeEnvironment.getApplication().getString(R.string.feature_chat_newchat_subtitle)

    @get:Rule
    val compose = createComposeRule()

    private fun render(state: NewChatUiState) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, LARGE_FONT)) {
                WaiveraTheme(darkTheme = false, motion = WaiveraMotionScheme.Reduced, hapticsEnabled = false) {
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
        }
        compose.waitForIdle()
    }

    /**
     * The load error fits whole at a glance (UI6-05): "Try again" and every line of the message
     * are inside the sheet without scrolling. A node cut by the sheet's edge has clipped bounds
     * smaller than its unclipped ones.
     */
    @Test
    fun loadErrorShowsTryAgainWithoutScrolling() {
        render(NewChatUiState(isLoading = false, loadErrorRes = WaiveraError.Network().messageRes))
        val tryAgain = compose.onNodeWithText(TRY_AGAIN).assertIsDisplayed().assertHasClickAction()
        assertWhollyVisible(TRY_AGAIN, tryAgain)
        assertWhollyVisible(OFFLINE_MESSAGE, compose.onNodeWithText(OFFLINE_MESSAGE, useUnmergedTree = true))
    }

    @Test
    fun atLargeFontTheSubtitleMakesRoomForThePeopleList() {
        render(NewChatUiState(isLoading = false, people = listOf(PersonOptionUi("usr_mei_chen", "Mei Chen", false))))
        compose.onNodeWithText(subtitle).assertDoesNotExist()
        compose.onNodeWithText("Mei Chen", useUnmergedTree = true).assertIsDisplayed()
    }

    private fun assertWhollyVisible(label: String, node: SemanticsNodeInteraction) {
        val clipped = node.getBoundsInRoot()
        val whole = node.getUnclippedBoundsInRoot()
        assertEquals("\"$label\" is cut at the bottom", whole.bottom.value, clipped.bottom.value, BOUNDS_TOLERANCE)
        assertEquals("\"$label\" is cut at the top", whole.top.value, clipped.top.value, BOUNDS_TOLERANCE)
    }

    @Test
    fun emptyStateShowsItsWholeMessage() {
        render(NewChatUiState(query = "zz", isLoading = false))
        val message = compose.onNodeWithText(emptyMessage, useUnmergedTree = true)
            .performScrollTo()
            .assertIsDisplayed()
        val layout = message.fetchTextLayoutResult()
        assertFalse("\"$emptyMessage\" is cut", layout.didOverflowHeight)
    }

    private fun SemanticsNodeInteraction.fetchTextLayoutResult(): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        return results.first()
    }

    private companion object {
        const val LARGE_FONT = 2f
        const val TRY_AGAIN = "Try again"
        const val OFFLINE_MESSAGE = "You appear to be offline. Check your connection and try again."
        const val BOUNDS_TOLERANCE = 0.5f
    }
}
