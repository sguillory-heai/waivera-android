package com.waivera.waivera.feature.auth

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import com.waivera.waivera.core.designsystem.theme.WaiveraMotionScheme
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.core.network.R as NetworkR
import com.waivera.waivera.core.screenshottesting.ScreenshotSdk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * On the small phone at 200% the bootstrap-failed gate keeps Try again pinned and whole, as the
 * onboarding gate does (UI11-05), in the server, retrying and offline states. Sign out moves to
 * the end of the scrolling copy and is offered once.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ScreenshotSdk.VALUE], qualifiers = "w360dp-h640dp-notnight-xhdpi")
class BootstrapFailedLargeFontTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = RuntimeEnvironment.getApplication()
    private var state by mutableStateOf(BootstrapFailedUiState(message = NetworkR.string.core_network_error_server))

    private fun show() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, LARGE_FONT)) {
                WaiveraTheme(darkTheme = false, motion = WaiveraMotionScheme.Reduced, hapticsEnabled = false) {
                    BootstrapFailedContent(state = state, onRetry = {}, onSignOut = {})
                }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun tryAgainStaysPinnedAndWholeInEveryState() {
        show()
        val tryAgain = context.getString(R.string.feature_auth_try_again)
        listOf(
            BootstrapFailedUiState(message = NetworkR.string.core_network_error_server),
            BootstrapFailedUiState(message = NetworkR.string.core_network_error_server, retrying = true),
            BootstrapFailedUiState(message = NetworkR.string.core_network_error_network, isOffline = true),
        ).forEach { gate ->
            state = gate
            compose.waitForIdle()
            val window = compose.onRoot().fetchSemanticsNode().boundsInRoot
            // The label stays in the semantics while busy; the busy row reads as the state.
            val bounds = compose.onNode(hasText(tryAgain) and hasClickAction()).fetchSemanticsNode().boundsInRoot
            assertTrue("$tryAgain has no height in $gate", bounds.height > 0f)
            assertTrue(
                "$tryAgain ends at ${bounds.bottom}, below ${window.bottom} in $gate",
                bounds.bottom <= window.bottom,
            )
            assertTrue("$tryAgain starts at ${bounds.top}, above ${window.top} in $gate", bounds.top >= window.top)
            val signOut = compose.onAllNodes(
                hasText(context.getString(R.string.feature_auth_sign_out)) and hasClickAction(),
            )
                .fetchSemanticsNodes()
            assertEquals("Sign out is offered once in $gate", 1, signOut.size)
        }
    }

    private companion object {
        const val LARGE_FONT = 2f
    }
}
