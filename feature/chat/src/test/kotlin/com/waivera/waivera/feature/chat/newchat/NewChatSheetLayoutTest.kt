package com.waivera.waivera.feature.chat.newchat

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
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
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The new-chat sheet at 200% on the small phone: the empty and load-error states scroll in the
 * space under the header, so their message is whole and "Try again" can be reached (UI5-07).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotSdk.VALUE], qualifiers = "w360dp-h640dp-notnight-xhdpi")
class NewChatSheetLayoutTest {
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

    @Test
    fun loadErrorCanReachTryAgain() {
        render(NewChatUiState(isLoading = false, loadErrorRes = WaiveraError.Network().messageRes))
        compose.onNodeWithText(TRY_AGAIN).performScrollTo().assertIsDisplayed().assertHasClickAction()
    }

    @Test
    fun emptyStateShowsItsWholeMessage() {
        render(NewChatUiState(query = "zz", isLoading = false))
        val message = compose.onNodeWithText(EMPTY_MESSAGE, useUnmergedTree = true)
            .performScrollTo()
            .assertIsDisplayed()
        val layout = message.fetchTextLayoutResult()
        assertFalse("\"$EMPTY_MESSAGE\" is cut", layout.didOverflowHeight)
    }

    private fun SemanticsNodeInteraction.fetchTextLayoutResult(): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        return results.first()
    }

    private companion object {
        const val LARGE_FONT = 2f
        const val TRY_AGAIN = "Try again"
        const val EMPTY_MESSAGE = "No staff members match."
    }
}
