package com.waivera.waivera.core.designsystem.component

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.height
import com.waivera.waivera.core.designsystem.theme.WaiveraMotionScheme
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.core.screenshottesting.ScreenshotSdk
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A text field keeps its height when the first letter replaces the placeholder (UI7-02): at
 * 200% on the small phone the placeholder made sign-in's Email field and the new-chat search
 * 9dp taller, so the content under them jumped on the first keystroke.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotSdk.VALUE], qualifiers = "w360dp-h640dp-notnight-xhdpi")
class WaiveraTextFieldLayoutTest {
    @get:Rule
    val compose = createComposeRule()

    private var value by mutableStateOf("")

    private fun render(placeholder: String) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, LARGE_FONT)) {
                WaiveraTheme(darkTheme = false, motion = WaiveraMotionScheme.Reduced, hapticsEnabled = false) {
                    WaiveraTextField(
                        value = value,
                        onValueChange = { value = it },
                        label = "Email",
                        placeholder = placeholder,
                        modifier = Modifier
                            .padding(WaiveraSpacing.Medium)
                            .testTag(FIELD),
                    )
                }
            }
        }
    }

    private fun assertSameHeightWithText(placeholder: String) {
        value = ""
        render(placeholder)
        val empty = compose.onNodeWithTag(FIELD).getBoundsInRoot().height
        value = "a"
        compose.waitForIdle()
        val typed = compose.onNodeWithTag(FIELD).getBoundsInRoot().height
        assertEquals("\"$placeholder\" against \"a\"", typed.value, empty.value, TOLERANCE_DP)
    }

    @Test
    fun signInEmailKeepsItsHeight() = assertSameHeightWithText("you@organization.com")

    @Test
    fun newChatSearchKeepsItsHeight() = assertSameHeightWithText("Type a name")

    /** A placeholder wider than the field stays on one line in a single-line field. */
    @Test
    fun aLongPlaceholderStaysOnOneLine() = assertSameHeightWithText("Search staff by name, role, team or location")

    private companion object {
        const val FIELD = "field"
        const val LARGE_FONT = 2f
        const val TOLERANCE_DP = 0.5f
    }
}
