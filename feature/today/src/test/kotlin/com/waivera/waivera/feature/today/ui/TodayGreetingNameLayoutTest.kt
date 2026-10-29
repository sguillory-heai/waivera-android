package com.waivera.waivera.feature.today.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import com.waivera.waivera.core.designsystem.theme.WaiveraMotionScheme
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.core.screenshottesting.ScreenshotSdk
import com.waivera.waivera.core.testing.LongFixtures
import com.waivera.waivera.feature.today.model.DayPart
import com.waivera.waivera.feature.today.model.Greeting
import com.waivera.waivera.feature.today.model.GreetingRules
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Today's greeting with the long-name fixtures on the small phone (wave 29 N2): it greets by the
 * first word, never breaks it mid-word, and a first word too long for the line ellipsizes on
 * one line. TalkBack reads the whole greeting.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotSdk.VALUE], qualifiers = "w360dp-h640dp-notnight-xhdpi")
class TodayGreetingNameLayoutTest {
    @get:Rule
    val compose = createComposeRule()

    private fun greetingLayout(firstName: String, fontScale: Float): Pair<TextLayoutResult, String> {
        val state = TodayPreviewStates.populated().copy(greeting = Greeting(DayPart.MORNING, firstName))
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                WaiveraTheme(darkTheme = false, motion = WaiveraMotionScheme.Reduced, hapticsEnabled = false) {
                    TodayHero(state = state, callbacks = TodayPreviewStates.callbacks())
                }
            }
        }
        compose.waitForIdle()
        val node = compose.onNode(hasText(GREETING, substring = true), useUnmergedTree = true)
        val results = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        val spoken = node.fetchSemanticsNode().config[SemanticsProperties.Text].joinToString("") { it.text }
        return results.first() to spoken
    }

    private fun assertGreeting(fixture: String, fontScale: Float) {
        val firstName = requireNotNull(GreetingRules.firstName(firstName = null, displayName = fixture))
        val (layout, spoken) = greetingLayout(firstName, fontScale)
        val text = layout.layoutInput.text.text
        assertEquals("$GREETING, $firstName", spoken.filterNot { it == ZERO_WIDTH_SPACE }, "TalkBack's greeting")
        for (line in 0 until layout.lineCount - 1) {
            val end = layout.getLineEnd(line)
            assertFalse(
                text[end - 1].isLetterOrDigit() && text[end].isLetterOrDigit(),
                "\"$firstName\" breaks mid-word at ${fontScale}x after \"${text.substring(0, end)}\"",
            )
        }
        for (line in 0 until layout.lineCount) {
            assertTrue(layout.getLineRight(line) <= layout.size.width, "line $line runs past the hero")
        }
        val nameLines = layout.lineCount - layout.getLineForOffset(NAME_START)
        assertTrue(nameLines <= 2, "\"$firstName\" takes $nameLines lines at ${fontScale}x")
    }

    @Test
    fun sixtyCharacterNameAtDefaultSize() = assertGreeting(LongFixtures.NAME_60, 1f)

    @Test
    fun sixtyCharacterNameAtLargeFont() = assertGreeting(LongFixtures.NAME_60, LARGE_FONT)

    @Test
    fun unbrokenNameAtDefaultSize() = assertGreeting(LongFixtures.NAME_UNBROKEN_40, 1f)

    @Test
    fun unbrokenNameAtLargeFont() = assertGreeting(LongFixtures.NAME_UNBROKEN_40, LARGE_FONT)

    @Test
    fun twoLongWordsAtLargeFont() = assertGreeting(LongFixtures.NAME_TWO_LONG_WORDS, LARGE_FONT)

    @Test
    fun oneCharacterNameAtLargeFont() = assertGreeting(LongFixtures.NAME_ONE_CHAR, LARGE_FONT)

    @Test
    fun emojiNameAtLargeFont() = assertGreeting(LongFixtures.NAME_EMOJI, LARGE_FONT)

    /** The unbroken first word gets one line and an ellipsis, not two or three broken lines. */
    @Test
    fun anUnbrokenFirstNameEllipsizesOnOneLine() {
        val (layout, _) = greetingLayout(LongFixtures.NAME_UNBROKEN_40, LARGE_FONT)
        val nameLine = layout.getLineForOffset(NAME_START)
        assertEquals(layout.lineCount - 1, nameLine, "the name is not on the greeting's last line")
        assertTrue(layout.isLineEllipsized(nameLine), "the unbroken name is not ellipsized")
    }

    /** A normal first name lays out as before: whole, with no ellipsis. */
    @Test
    fun aNormalFirstNameIsWhole() {
        val (layout, _) = greetingLayout("Alex", LARGE_FONT)
        assertFalse(layout.isLineEllipsized(layout.lineCount - 1))
        assertEquals(Int.MAX_VALUE, layout.layoutInput.maxLines)
    }

    private companion object {
        const val GREETING = "Good morning"

        /** The name starts after "Good morning, ". */
        const val NAME_START = GREETING.length + 2
        const val LARGE_FONT = 2f
        const val ZERO_WIDTH_SPACE = '\u200B'
    }
}
