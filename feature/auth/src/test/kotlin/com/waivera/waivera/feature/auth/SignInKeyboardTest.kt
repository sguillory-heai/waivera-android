package com.waivera.waivera.feature.auth

import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.github.takahirom.roborazzi.captureRoboImage
import com.waivera.waivera.core.designsystem.theme.WaiveraHero
import com.waivera.waivera.core.designsystem.theme.WaiveraMotionScheme
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.core.screenshottesting.ScreenshotMatrix
import com.waivera.waivera.core.screenshottesting.ScreenshotSdk
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Sign-in with the keyboard up on the small phone (UI9-11). Robolectric has no soft keyboard, so
 * the test focuses the password and dispatches IME insets to the compose view, the same path the
 * platform takes: the header goes compact, the frame pads for the keyboard, and Sign in is
 * brought fully into view above it. It also records `sign_in_keyboard_up_small_*_font100`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotSdk.VALUE], qualifiers = "w360dp-h640dp-notnight-xhdpi")
class SignInKeyboardTest {
    @get:Rule
    val compose = createComposeRule()

    private var composeView: View? = null

    @Test
    fun signInStaysAboveTheKeyboardInLight() = keyboardUp(dark = false)

    @Test
    fun signInStaysAboveTheKeyboardInDark() = keyboardUp(dark = true)

    private fun keyboardUp(dark: Boolean) {
        if (dark) RuntimeEnvironment.setQualifiers("+night")
        compose.setContent {
            composeView = LocalView.current
            WaiveraTheme(darkTheme = dark, motion = WaiveraMotionScheme.Reduced, hapticsEnabled = false) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(WaiveraTheme.colors.background),
                ) {
                    SignInContent(
                        state = SignInUiState(email = "alex.rivera@example.org", password = "correct horse"),
                        actions = SignInActions(),
                        legalLinks = LegalLinks(termsOfService = "terms", privacyPolicy = "privacy"),
                    )
                }
            }
        }
        compose.waitForIdle()
        // The password field: the second text input on the screen.
        compose.onAllNodes(hasSetTextAction())[1].performClick()
        raiseKeyboard()

        // Focusing the password adds the cursor handle's popup as a second root; the window is
        // the first.
        val window = compose.onAllNodes(isRoot()).onFirst().fetchSemanticsNode().boundsInRoot
        val keyboardTop = window.bottom - keyboardPx()
        val signIn = compose.onNode(hasText("Sign in") and hasClickAction()).fetchSemanticsNode().boundsInRoot
        assertTrue("Sign in ends at ${signIn.bottom}, under the keyboard at $keyboardTop", signIn.bottom <= keyboardTop)
        assertTrue("Sign in starts at ${signIn.top}, above the window", signIn.top >= window.top)

        val logo = compose.onNode(hasContentDescription("Waivera")).fetchSemanticsNode().boundsInRoot
        val compactLogo = with(compose.density) { WaiveraHero.LockupCompact.toPx() }
        assertTrue("the header is not compact: logo is ${logo.height}px tall", logo.height <= compactLogo + 1f)

        val theme = if (dark) "dark" else "light"
        compose.onAllNodes(isRoot()).onFirst().captureRoboImage(
            filePath = "${ScreenshotMatrix.outputDirectory}/sign_in_keyboard_up_small_${theme}_font100.png",
        )
    }

    private fun keyboardPx(): Float = with(compose.density) { KEYBOARD.toPx() }

    /** Shows a keyboard the way the platform reports one: visible IME insets at the bottom. */
    private fun raiseKeyboard() {
        val height = with(compose.density) { KEYBOARD.roundToPx() }
        compose.runOnIdle {
            val insets = WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, height))
                .setVisible(WindowInsetsCompat.Type.ime(), true)
                .build()
            ViewCompat.dispatchApplyWindowInsets(checkNotNull(composeView), insets)
        }
        compose.waitForIdle()
    }

    private companion object {
        /** A typical phone keyboard with its suggestion strip. */
        val KEYBOARD = 300.dp
    }
}
