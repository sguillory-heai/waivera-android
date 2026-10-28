package com.waivera.waivera.core.designsystem.screenshot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import com.waivera.waivera.core.designsystem.component.WaiveraGradientButton
import com.waivera.waivera.core.designsystem.component.WaiveraHeroHeader
import com.waivera.waivera.core.designsystem.component.WaiveraHeroLogo
import com.waivera.waivera.core.designsystem.theme.WaiveraHero
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.screenshottesting.ScreenshotSdk
import com.waivera.waivera.core.screenshottesting.captureScreenshots
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The brand hero pieces (header with the light-on-dark logo, the gradient button) across the matrix. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotSdk.VALUE])
class HeroScreenshotTest {
    @Test
    fun heroHeader() = captureScreenshots(name = "HeroHeader") {
        Column(verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.Medium)) {
            WaiveraHeroHeader(modifier = Modifier.height(WaiveraHero.HeaderHeight)) {
                WaiveraHeroLogo(height = WaiveraHero.Lockup)
            }
            WaiveraHeroHeader(modifier = Modifier.height(WaiveraHero.HeaderHeightCompact)) {
                WaiveraHeroLogo(height = WaiveraHero.LockupCompact)
            }
        }
    }

    @Test
    fun gradientButton() = captureScreenshots(name = "GradientButton") {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(WaiveraHero.colors.sheet)
                .padding(WaiveraSpacing.Medium),
            verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.Small),
        ) {
            WaiveraGradientButton(text = "Sign in", onClick = {})
            WaiveraGradientButton(text = "Sign in", onClick = {}, loadingText = "Signing you in…")
            WaiveraGradientButton(text = "Sign in", onClick = {}, enabled = false)
        }
    }
}
