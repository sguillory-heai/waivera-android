package com.waivera.waivera.core.designsystem.screenshot

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.waivera.waivera.core.designsystem.component.WaiveraAvatar
import com.waivera.waivera.core.designsystem.component.WaiveraBrandSurface
import com.waivera.waivera.core.designsystem.component.WaiveraButton
import com.waivera.waivera.core.designsystem.component.WaiveraButtonVariant
import com.waivera.waivera.core.designsystem.component.WaiveraEmojiTile
import com.waivera.waivera.core.designsystem.icon.WaiveraIcons
import com.waivera.waivera.core.designsystem.theme.WaiveraSizes
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.screenshottesting.ScreenshotSdk
import com.waivera.waivera.core.screenshottesting.captureScreenshots
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * What sits on the brand wash (avatars, the celebration tile, "Send wishes") across the matrix.
 * Separate from [DesignSystemScreenshotTest] so each class stays a readable size.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotSdk.VALUE])
class BrandWashScreenshotTest {
    /**
     * Initials and fallback avatars on the brand wash (UI8-05): the fill alone sets each one
     * apart, with no border or ring, in the one rounded-square shape at every size.
     */
    @Test
    fun avatarsOnTheWash() = capture("AvatarsOnWash") {
        WaiveraBrandSurface {
            Row(
                horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.MediumSmall),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                WaiveraAvatar(name = "Alex Rivera", size = WaiveraSizes.AvatarLarge)
                WaiveraAvatar(name = "Jordan Lee")
                WaiveraAvatar(name = "Sam Ortiz", size = WaiveraSizes.AvatarSmall)
                WaiveraAvatar(name = "")
            }
        }
    }

    /**
     * "Send wishes" on the brand wash (UI9-08): the on-wash tonal fill reads as a button at every
     * point of the wash, beside the plain tonal style it replaces there and a celebration tile.
     */
    @Test
    fun tonalOnTheWash() = capture("TonalOnWash") {
        WaiveraBrandSurface {
            Row(
                horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.MediumSmall),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                WaiveraEmojiTile(emoji = "🎉")
                WaiveraButton(
                    text = "Send wishes",
                    onClick = {},
                    variant = WaiveraButtonVariant.TonalOnWash,
                    leadingIcon = WaiveraIcons.Chat,
                )
            }
            WaiveraButton(text = "Send wishes", onClick = {
            }, variant = WaiveraButtonVariant.TonalOnWash, fullWidth = true)
        }
    }

    private fun capture(name: String, content: @Composable () -> Unit) {
        captureScreenshots(name = name) {
            Column(
                modifier = Modifier.padding(WaiveraSpacing.Medium),
                verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.Small),
            ) {
                content()
            }
        }
    }
}
