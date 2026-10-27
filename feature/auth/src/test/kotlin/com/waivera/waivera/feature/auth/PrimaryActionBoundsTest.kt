package com.waivera.waivera.feature.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.waivera.waivera.core.designsystem.theme.WaiveraMotionScheme
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.core.network.R as NetworkR
import com.waivera.waivera.core.screenshottesting.ScreenshotSdk
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * On the small phone (360×640) at 100% font, the action each one-tap screen exists for is fully
 * on the first screen, inside the window and not under the scroll fade (UI3-01). The screens
 * here are the ones whose content is taller than that window.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ScreenshotSdk.VALUE], qualifiers = "w360dp-h640dp-notnight-xhdpi")
class PrimaryActionBoundsTest {
    @get:Rule
    val compose = createComposeRule()

    private fun render(content: @Composable () -> Unit) {
        compose.setContent {
            WaiveraTheme(darkTheme = false, motion = WaiveraMotionScheme.Reduced, hapticsEnabled = false) {
                content()
            }
        }
        compose.waitForIdle()
    }

    private fun button(label: String): SemanticsNodeInteraction =
        compose.onNode(hasText(label) and hasClickAction(), useUnmergedTree = false)

    private fun assertFullyOnScreen(label: String) {
        val window = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val bounds = button(label).fetchSemanticsNode().boundsInRoot
        assertTrue("$label starts at ${bounds.top}, above ${window.top}", bounds.top >= window.top)
        assertTrue("$label ends at ${bounds.bottom}, below ${window.bottom}", bounds.bottom <= window.bottom)
        assertTrue("$label has no height", bounds.height > 0f)
    }

    @Test
    fun primerTurnOnIsFullyVisible() {
        render { NotificationPrimerContent(onEnable = {}, onNotNow = {}) }
        assertFullyOnScreen("Turn on notifications")
        assertFullyOnScreen("Not now")
    }

    @Test
    fun primerRequestingIsFullyVisible() {
        render { NotificationPrimerContent(onEnable = {}, onNotNow = {}, requesting = true) }
        // The label stays in the semantics while busy; the spinner row reads as the state.
        assertFullyOnScreen("Turn on notifications")
    }

    @Test
    fun signInAfterAWrongPasswordIsFullyVisible() {
        render {
            SignInContent(
                state = SignInUiState(
                    email = "alex.rivera@example.org",
                    password = "correct horse",
                    errorMessage = NetworkR.string.core_network_error_invalid_credentials,
                ),
                actions = SignInActions(),
            )
        }
        assertFullyOnScreen("Sign in")
    }

    @Test
    fun signInWithEveryBannerIsFullyVisible() {
        val states = listOf(
            SignInUiState(email = "a@example.org", errorMessage = NetworkR.string.core_network_error_network),
            SignInUiState(email = "a@example.org", errorMessage = NetworkR.string.core_network_error_session_expired),
            SignInUiState(signedOutNotice = true),
            SignInUiState(email = "a@example.org", resetSentTo = "a@example.org"),
            SignInUiState(missingConfiguration = listOf("waivera.apiBaseUrl")),
        )
        var current by mutableStateOf(states.first())
        render { SignInContent(state = current, actions = SignInActions()) }
        states.forEach { state ->
            current = state
            compose.waitForIdle()
            assertFullyOnScreen("Sign in")
        }
    }

    @Test
    fun passwordChangeSignOutIsFullyVisible() {
        render {
            PasswordChangeContent(
                state = PasswordChangeUiState(
                    new = "a much longer phrase",
                    confirm = "a much longer phrase",
                    currentError = R.string.feature_auth_error_current_password_wrong,
                ),
                required = true,
                actions = PasswordChangeActions(),
            )
        }
        assertFullyOnScreen("Sign out")
    }
}
