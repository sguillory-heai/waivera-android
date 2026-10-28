package com.waivera.waivera.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.waivera.waivera.core.designsystem.text.withBreaksAfterHyphens
import com.waivera.waivera.core.screenshottesting.ScreenshotSdk
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Long hyphenated names and channel names wrap only at a space or after a hyphen, never
 * mid-word, in the title styles rows and headers use, at 100% and 200% (UI4-02, UI5-05).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotSdk.VALUE], qualifiers = "w360dp-h640dp-notnight-xhdpi")
class LongNameLineBreakTest {
    @get:Rule
    val compose = createComposeRule()

    private fun layout(
        text: String,
        fontScale: Float,
        style: (Typography) -> TextStyle,
        maxLines: Int,
        width: Dp,
    ): TextLayoutResult {
        var result: TextLayoutResult? = null
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                WaiveraTheme(darkTheme = false, motion = WaiveraMotionScheme.Reduced, hapticsEnabled = false) {
                    val measurer = rememberTextMeasurer()
                    val widthPx = with(LocalDensity.current) { width.roundToPx() }
                    result = measurer.measure(
                        text = text,
                        style = style(MaterialTheme.typography),
                        overflow = TextOverflow.Ellipsis,
                        maxLines = maxLines,
                        constraints = Constraints(maxWidth = widthPx),
                    )
                }
            }
        }
        compose.waitForIdle()
        return checkNotNull(result)
    }

    private fun assertBreaksBetweenWords(
        name: String,
        fontScale: Float,
        maxLines: Int = Int.MAX_VALUE,
        style: (Typography) -> TextStyle = { it.titleSmall },
        width: Dp = ROW_TEXT_WIDTH,
    ) {
        val text = name.withBreaksAfterHyphens()
        val layout = layout(text = text, fontScale = fontScale, style = style, maxLines = maxLines, width = width)
        for (line in 0 until layout.lineCount - 1) {
            val end = layout.getLineEnd(line)
            val before = text[end - 1]
            assertTrue(
                "\"$name\" at ${fontScale}x breaks mid-word after \"${text.substring(0, end)}\"",
                before == ' ' || before == '-' || before == ZERO_WIDTH_SPACE,
            )
        }
    }

    @Test
    fun channelNameAtDefaultSize() = assertBreaksBetweenWords(CHANNEL, fontScale = 1f)

    @Test
    fun channelNameAtLargeSize() = assertBreaksBetweenWords(CHANNEL, fontScale = 2f)

    @Test
    fun channelNameInATwoLineRow() = assertBreaksBetweenWords(CHANNEL, fontScale = 1f, maxLines = 2)

    @Test
    fun personNameAtDefaultSize() = assertBreaksBetweenWords(PERSON, fontScale = 1f)

    @Test
    fun personNameAtLargeSize() = assertBreaksBetweenWords(PERSON, fontScale = 2f)

    @Test
    fun notSentHeadlineAtLargeSize() =
        assertBreaksBetweenWords(name = "Your message to $PERSON", fontScale = 2f, maxLines = 2)

    /** The profile and person headers and the top bar title use headlineSmall (UI5-05). */
    @Test
    fun profileHeaderNameAtLargeSize() = assertBreaksBetweenWords(
        name = HEADER_PERSON,
        fontScale = 2f,
        style = { it.headlineSmall },
        width = HEADER_TEXT_WIDTH,
    )

    @Test
    fun profileHeaderNameAtDefaultSize() = assertBreaksBetweenWords(
        name = HEADER_PERSON,
        fontScale = 1f,
        style = { it.headlineSmall },
        width = HEADER_TEXT_WIDTH,
    )

    private companion object {
        const val CHANNEL = "old-pilot-program-coordination-and-scheduling"
        const val PERSON = "Maria-Fernanda Castellanos-Whitaker de la Cruz"
        const val ZERO_WIDTH_SPACE = '\u200B'
        const val HEADER_PERSON = "Alexandria Catherine Rivera-Montgomery"

        /** The profile header's text column at large font: the card's width less its padding. */
        val HEADER_TEXT_WIDTH = 296.dp

        /** A row's text column on a 360dp phone: gutter, avatar and gaps taken off. */
        val ROW_TEXT_WIDTH = 312.dp
    }
}
