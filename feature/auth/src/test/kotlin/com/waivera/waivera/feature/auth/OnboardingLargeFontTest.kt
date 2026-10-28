package com.waivera.waivera.feature.auth

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import com.waivera.waivera.core.designsystem.theme.WaiveraMotionScheme
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.core.screenshottesting.ScreenshotSdk
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * On the small phone at 200% the onboarding gate keeps its main action pinned, so the busy
 * "Checking…" state is in sight without scrolling (UI8-07). Sign out moves into the content.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ScreenshotSdk.VALUE], qualifiers = "w360dp-h640dp-notnight-xhdpi")
class OnboardingLargeFontTest {
    @get:Rule
    val compose = createComposeRule()

    private fun render(state: OnboardingUiState) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, LARGE_FONT)) {
                WaiveraTheme(darkTheme = false, motion = WaiveraMotionScheme.Reduced, hapticsEnabled = false) {
                    OnboardingRequiredContent(state = state, onCheckAgain = {}, onSignOut = {})
                }
            }
        }
        compose.waitForIdle()
    }

    private fun assertInWindow(label: String) {
        val window = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val bounds = compose.onNode(hasText(label) and hasClickAction()).fetchSemanticsNode().boundsInRoot
        assertTrue("$label ends at ${bounds.bottom}, below ${window.bottom}", bounds.bottom <= window.bottom)
        assertTrue("$label starts at ${bounds.top}, above ${window.top}", bounds.top >= window.top)
        assertTrue("$label has no height", bounds.height > 0f)
    }

    @Test
    fun checkingStaysInSightAtLargeFont() {
        render(OnboardingUiState(checking = true))
        // The label stays in the semantics while busy; the busy row reads as the state.
        assertInWindow(CHECK_AGAIN)
    }

    @Test
    fun checkAgainStaysInSightWithTheStillPendingBanner() {
        render(OnboardingUiState(stillPending = true))
        assertInWindow(CHECK_AGAIN)
    }

    private companion object {
        const val CHECK_AGAIN = "I’m done, check again"
        const val LARGE_FONT = 2f
    }
}
