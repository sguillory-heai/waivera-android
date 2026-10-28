package com.waivera.waivera.feature.auth

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.waivera.waivera.core.designsystem.theme.WaiveraHero
import com.waivera.waivera.core.designsystem.theme.WaiveraMotionScheme
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
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
 * Sign-in on the large phone (412×915) at 100% font, `sign_in_empty_large_*` (UI10-07): the
 * header takes the concept's share of the window, so the form sits lower and the sheet holds no
 * void between "Forgot password?" and the legal line.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ScreenshotSdk.VALUE], qualifiers = "w412dp-h915dp-notnight-xxhdpi")
class SignInLargePhoneLayoutTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = RuntimeEnvironment.getApplication()

    private fun show() {
        compose.setContent {
            WaiveraTheme(darkTheme = false, motion = WaiveraMotionScheme.Reduced, hapticsEnabled = false) {
                SignInContent(
                    state = SignInUiState(),
                    actions = SignInActions(),
                    legalLinks = LegalLinks(termsOfService = "terms", privacyPolicy = "privacy"),
                )
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun theFormLeavesNoVoidAboveTheLegalLine() {
        show()
        val forgot = compose.onNode(
            hasText(context.getString(R.string.feature_auth_forgot_password)) and hasClickAction(),
        )
            .fetchSemanticsNode().boundsInRoot
        val terms = compose.onNode(hasText("Terms of Service") and hasClickAction()).fetchSemanticsNode().boundsInRoot
        val gap = with(compose.density) { (terms.top - forgot.bottom).toDp() }
        assertTrue("$gap of empty sheet between Forgot password? and the legal line", gap < MAX_VOID)
        val window = compose.onRoot().fetchSemanticsNode().boundsInRoot
        assertTrue("the legal line ends at ${terms.bottom}, below the window", terms.bottom <= window.bottom)
    }

    @Test
    fun theHeaderTakesTheConceptsShareOfTheWindow() {
        assertEquals(260.dp, regularHeaderHeight(640.dp))
        assertEquals(915.dp * WaiveraHero.HEADER_WINDOW_SHARE, regularHeaderHeight(915.dp))
        assertEquals(WaiveraHero.HeaderHeightMax, regularHeaderHeight(1200.dp))
        show()
        val logo = compose.onNode(hasContentDescription(context.getString(R.string.feature_auth_brand)))
            .fetchSemanticsNode().boundsInRoot
        val centre = with(compose.density) { logo.center.y.toDp() }
        val expected = regularHeaderHeight(915.dp) * WaiveraHero.LOCKUP_CENTER_SHARE
        assertTrue("the lockup's middle is at $centre, not near $expected", (centre - expected).value in -2f..2f)
    }

    private companion object {
        val MAX_VOID = 200.dp
    }
}
