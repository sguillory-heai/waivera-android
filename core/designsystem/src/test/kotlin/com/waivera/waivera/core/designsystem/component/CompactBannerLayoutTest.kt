package com.waivera.waivera.core.designsystem.component

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import com.waivera.waivera.core.designsystem.icon.WaiveraIcons
import com.waivera.waivera.core.designsystem.theme.WaiveraMotionScheme
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.core.screenshottesting.ScreenshotSdk
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** At 200% on the small phone a compact banner is one row, with its action as an icon (UI3-08). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ScreenshotSdk.VALUE], qualifiers = "w360dp-h640dp-notnight-xhdpi")
class CompactBannerLayoutTest {
    @get:Rule
    val compose = createComposeRule()

    private fun render(title: String, message: String, actionLabel: String) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, LARGE_FONT)) {
                WaiveraTheme(darkTheme = false, motion = WaiveraMotionScheme.Reduced, hapticsEnabled = false) {
                    WaiveraBanner(
                        title = title,
                        message = message,
                        tone = BannerTone.Offline,
                        actionLabel = actionLabel,
                        onAction = {},
                        actionIcon = WaiveraIcons.Refresh,
                        onDismiss = {},
                    )
                }
            }
        }
    }

    @Test
    fun offlineBannerIsOneRowWithAnIconAction() {
        render(title = "You're offline", message = "Showing what you had at 10:18 AM.", actionLabel = "Retry now")
        compose.onNodeWithContentDescription("Retry now").assertHasClickAction()
        val text = compose.onNodeWithContentDescription("You're offline. Showing what you had at 10:18 AM.")
            .getBoundsInRoot()
        // One 15sp title line at 200% plus its padding; a second line would add another 40dp.
        assertTrue("banner text is ${text.height} tall", text.height <= ONE_ROW_MAX)
    }

    @Test
    fun aLongTitleEllipsizesInsteadOfWrapping() {
        render(
            title = "Still can't reach Waivera after several tries",
            message = "Showing saved conversations.",
            actionLabel = "Retry now",
        )
        val text = compose.onNodeWithContentDescription(
            "Still can't reach Waivera after several tries. Showing saved conversations.",
        ).getBoundsInRoot()
        assertTrue("banner text is ${text.height} tall", text.height <= ONE_ROW_MAX)
    }

    private companion object {
        const val LARGE_FONT = 2f
        val ONE_ROW_MAX = 64.dp
    }
}
