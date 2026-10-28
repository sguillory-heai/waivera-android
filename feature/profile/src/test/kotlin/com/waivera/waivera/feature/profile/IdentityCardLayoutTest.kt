package com.waivera.waivera.feature.profile

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import com.waivera.waivera.core.data.model.CurrentUser
import com.waivera.waivera.core.designsystem.theme.WaiveraMotionScheme
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.core.screenshottesting.ScreenshotSdk
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The profile header keeps the role and the address apart at 200% on the small phone, where both
 * wrap, so "coordinator" and "alex.rivera@" don't read as one phrase (UI7-04).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ScreenshotSdk.VALUE], qualifiers = "w360dp-h640dp-notnight-xhdpi")
class IdentityCardLayoutTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun theAddressSitsApartFromTheRoleAtLargeFont() {
        val user = CurrentUser(
            userId = "user-1",
            email = EMAIL,
            displayName = "Alex Rivera",
            roles = listOf(ROLE),
        )
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, LARGE_FONT)) {
                WaiveraTheme(darkTheme = false, motion = WaiveraMotionScheme.Reduced, hapticsEnabled = false) {
                    IdentityCard(user = user, profile = null, celebration = null)
                }
            }
        }
        compose.waitForIdle()
        val role = compose.onNodeWithText(ProfileFormat.roleLabel(ROLE), useUnmergedTree = true).getBoundsInRoot()
        val address = compose.onNodeWithText(EMAIL, useUnmergedTree = true).getBoundsInRoot()
        val gap = address.top - role.bottom
        assertTrue("the address is $gap under the role", gap >= WaiveraSpacing.ExtraSmall)
    }

    private companion object {
        const val EMAIL = "alex.rivera@example.com"
        const val ROLE = "Senior support coordinator"
        const val LARGE_FONT = 2f
    }
}
