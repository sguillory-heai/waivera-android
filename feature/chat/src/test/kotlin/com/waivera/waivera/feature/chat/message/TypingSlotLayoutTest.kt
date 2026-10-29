package com.waivera.waivera.feature.chat.message

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import com.waivera.waivera.core.designsystem.theme.WaiveraMotionScheme
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.core.screenshottesting.ScreenshotSdk
import com.waivera.waivera.core.testing.LongFixtures
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The typing line on the small phone (wave 29 N1): one line at any font scale, first names only,
 * the names ellipsized and never "is typing…", and the slot the same height whoever is typing,
 * so the composer never jumps. TalkBack hears the full names.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotSdk.VALUE], qualifiers = "w360dp-h640dp-notnight-xhdpi")
class TypingSlotLayoutTest {
    @get:Rule
    val compose = createComposeRule()

    private val resources = RuntimeEnvironment.getApplication().resources
    private var names by mutableStateOf(emptyList<String>())

    private fun show(fontScale: Float) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                WaiveraTheme(darkTheme = false, motion = WaiveraMotionScheme.Reduced, hapticsEnabled = false) {
                    TypingSlot(line = typingLine(names))
                }
            }
        }
        compose.waitForIdle()
    }

    private fun slotHeight(): Float = compose.onNodeWithTag(TYPING_SLOT_TAG).fetchSemanticsNode().boundsInRoot.height

    private fun layoutOf(tag: String): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag(tag, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        return results.first()
    }

    private val cases: List<List<String>> = listOf(
        listOf("Priya Raman"),
        listOf(LongFixtures.NAME_60),
        listOf(LongFixtures.NAME_UNBROKEN_40),
        listOf(LongFixtures.NAME_60, LongFixtures.NAME_UNBROKEN_40),
        listOf(LongFixtures.NAME_TWO_LONG_WORDS, LongFixtures.NAME_ONE_CHAR, LongFixtures.NAME_EMOJI),
        listOf(LongFixtures.NAME_UNBROKEN_40, LongFixtures.NAME_60, LongFixtures.NAME_TWO_LONG_WORDS),
    )

    private fun assertOneStableLine(fontScale: Float) {
        show(fontScale)
        val empty = slotHeight()
        cases.forEach { typing ->
            names = typing
            compose.waitForIdle()
            assertEquals(empty, slotHeight(), "the slot changes height for $typing at ${fontScale}x")
            val shown = layoutOf(TYPING_NAMES_TAG)
            assertEquals(1, shown.lineCount, "the names wrap for $typing at ${fontScale}x")
            assertFalse(shown.didOverflowHeight, "the names are cut vertically for $typing")
            val tails = compose.onAllNodesWithTag(TYPING_TAIL_TAG, useUnmergedTree = true).fetchSemanticsNodes()
            if (tails.isNotEmpty()) {
                val tail = layoutOf(TYPING_TAIL_TAG)
                assertEquals(1, tail.lineCount)
                assertFalse(tail.isLineEllipsized(0), "\"is typing\" is ellipsized for $typing at ${fontScale}x")
                assertTrue(tail.getLineRight(0) <= tail.size.width, "\"is typing\" is cut for $typing")
                val slot = compose.onNodeWithTag(TYPING_SLOT_TAG).fetchSemanticsNode().boundsInRoot
                val tailBounds = tails.single().boundsInRoot
                assertTrue(tailBounds.right <= slot.right + 1f, "\"is typing\" runs out of the slot for $typing")
            } else {
                assertFalse(shown.isLineEllipsized(0), "the whole line is ellipsized for $typing")
            }
        }
        names = emptyList()
        compose.waitForIdle()
        assertEquals(empty, slotHeight(), "the empty slot changes height at ${fontScale}x")
    }

    @Test
    fun oneStableLineAtDefaultSize() = assertOneStableLine(fontScale = 1f)

    @Test
    fun oneStableLineAtLargeFont() = assertOneStableLine(fontScale = LARGE_FONT)

    /** The full names go to TalkBack, on the slot's one live region. */
    @Test
    fun talkBackHearsTheFullNames() {
        show(LARGE_FONT)
        names = listOf(LongFixtures.NAME_60, LongFixtures.NAME_UNBROKEN_40)
        compose.waitForIdle()
        val node = compose.onNodeWithTag(TYPING_SLOT_TAG).fetchSemanticsNode()
        assertEquals(
            listOf("${LongFixtures.NAME_60} and ${LongFixtures.NAME_UNBROKEN_40} are typing…"),
            node.config[SemanticsProperties.ContentDescription],
        )
    }

    /** A normal name fits, so the line is one piece exactly as before. */
    @Test
    fun aNormalNameIsOneUncutPiece() {
        show(fontScale = 1f)
        names = listOf("Priya Raman")
        compose.waitForIdle()
        assertTrue(compose.onAllNodesWithTag(TYPING_TAIL_TAG, useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
        assertFalse(layoutOf(TYPING_NAMES_TAG).isLineEllipsized(0))
    }

    @Test
    fun theLineSaysFirstNamesAndCountsTheRest() {
        assertNull(TypingLine.of(emptyList(), resources))
        val one = assertNotNull(TypingLine.of(listOf("Priya Raman"), resources))
        assertEquals("Priya is typing…", one.shown)
        assertEquals("Priya", one.shown.substring(0, one.namesEnd))
        assertEquals("Priya Raman is typing…", one.spoken)

        val two = assertNotNull(TypingLine.of(listOf(LongFixtures.NAME_60, "Jordan Lee"), resources))
        assertEquals("Maria-Fernanda and Jordan are typing…", two.shown)
        assertEquals(" are typing…", two.shown.substring(two.namesEnd))

        val many = assertNotNull(TypingLine.of(listOf("Priya Raman", "Jordan Lee", "Sam Okafor"), resources))
        assertEquals("Priya and 2 others are typing…", many.shown)
        assertEquals("Priya and 2 others", many.shown.substring(0, many.namesEnd))
        assertEquals("Priya Raman and 2 others are typing…", many.spoken)

        val unbroken = assertNotNull(TypingLine.of(listOf(LongFixtures.NAME_UNBROKEN_40), resources))
        assertEquals(LongFixtures.NAME_UNBROKEN_40, unbroken.shown.substring(0, unbroken.namesEnd))
    }

    private companion object {
        const val LARGE_FONT = 2f
    }
}
