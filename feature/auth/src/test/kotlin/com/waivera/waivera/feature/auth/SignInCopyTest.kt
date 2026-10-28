package com.waivera.waivera.feature.auth

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import com.waivera.waivera.core.designsystem.theme.WaiveraMotionScheme
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.core.screenshottesting.ScreenshotSdk
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** The sign-in copy that has to read as typeset text: the legal sentence and the email hint. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ScreenshotSdk.VALUE], qualifiers = "w360dp-h640dp-notnight-xhdpi")
class SignInCopyTest {
    @get:Rule
    val compose = createComposeRule()

    private val context: Context = RuntimeEnvironment.getApplication()

    /** The pieces make one sentence with a closing full stop (UI9-03). */
    @Test
    fun legalPiecesReadAsOneSentence() {
        val sentence = listOf(
            R.string.feature_auth_legal_lead,
            R.string.feature_auth_legal_terms_lead,
            R.string.feature_auth_legal_terms,
            R.string.feature_auth_legal_and,
            R.string.feature_auth_legal_privacy,
        ).joinToString(" ") { context.getString(it) } + context.getString(R.string.feature_auth_legal_end)
        assertEquals("By continuing, you agree to our Terms of Service and Privacy Policy.", sentence)
    }

    /** At 200% on a small phone, "our" stays on the line of its link and the full stop on Privacy Policy's. */
    @Test
    fun ourAndTheFullStopNeverSitAlone() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, LARGE_FONT)) {
                WaiveraTheme(darkTheme = false, motion = WaiveraMotionScheme.Reduced, hapticsEnabled = false) {
                    LegalFooter(links = LegalLinks(termsOfService = "terms", privacyPolicy = "privacy"), onOpen = {})
                }
            }
        }
        compose.waitForIdle()
        assertSameLine("our", "Terms of Service")
        val privacy = compose.onNode(hasText("Privacy Policy"), useUnmergedTree = true).fetchSemanticsNode()
        val terms = compose.onNode(hasText("Terms of Service"), useUnmergedTree = true).fetchSemanticsNode()
        assertTrue(privacy.boundsInRoot.top >= terms.boundsInRoot.top, "Privacy Policy comes before Terms")
    }

    /** The example address in the email error never wraps at its "@" or dot (UI9-10). */
    @Test
    fun emailHintKeepsTheExampleAddressWhole() {
        val message = context.getString(R.string.feature_auth_error_email_invalid)
        assertTrue("name${WORD_JOINER}@${WORD_JOINER}organization${WORD_JOINER}.${WORD_JOINER}com" in message, message)
        assertEquals(
            "That doesn't look like an email. Try name@organization.com.",
            message.replace(WORD_JOINER.toString(), ""),
        )
    }

    private fun assertSameLine(first: String, second: String) {
        val a = compose.onNode(hasText(first), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val b = compose.onNode(hasText(second), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue(
            abs(a.center.y - b.center.y) < 1f,
            "\"$first\" (${a.center.y}) and \"$second\" (${b.center.y}) split",
        )
        assertTrue(a.right <= b.left, "\"$first\" should come just before \"$second\"")
    }

    private companion object {
        const val LARGE_FONT = 2f
        const val WORD_JOINER = '⁠'
    }
}
