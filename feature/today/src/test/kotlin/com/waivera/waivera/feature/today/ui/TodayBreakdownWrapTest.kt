package com.waivera.waivera.feature.today.ui

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import com.waivera.waivera.core.designsystem.theme.WaiveraTextStyles
import com.waivera.waivera.core.screenshottesting.ScreenshotSdk
import com.waivera.waivera.feature.today.R
import com.waivera.waivera.feature.today.model.NeedsYouKind
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Wave 30 R1: the hero's waiting line ("1 message · 1 invite · 1 ticket") wraps only between
 * kinds. At every width from the narrowest that holds one kind to a wide card, at 100% and 200%,
 * no line ends on a bare count with its noun on the next line, and no line starts with the
 * separator's dot.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotSdk.VALUE], qualifiers = "w360dp-h640dp-notnight-xhdpi")
class TodayBreakdownWrapTest {
    @get:Rule
    val compose = createComposeRule()

    private val context: Context = RuntimeEnvironment.getApplication()

    private fun line(vararg counts: Pair<NeedsYouKind, Int>): String =
        counts.joinToString(context.getString(R.string.feature_today_brand_kind_separator)) { (kind, n) ->
            context.resources.getQuantityString(kind.pluralRes, n, n)
        }

    private fun layouts(text: String, fontScale: Float): List<TextLayoutResult> {
        var results = emptyList<TextLayoutResult>()
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                val measurer = rememberTextMeasurer()
                // Narrower than its widest unbreakable piece, any text has to break inside a word.
                val widest = text.split(' ').maxOf { measurer.measure(it, WaiveraTextStyles.HeroBody).size.width }
                results = (MIN_WIDTH_DP..MAX_WIDTH_DP)
                    .map { widthDp -> (widthDp * density.density).toInt() }
                    .filter { it >= widest }
                    .map { width ->
                        measurer.measure(
                            text = text,
                            style = WaiveraTextStyles.HeroBody,
                            constraints = Constraints(maxWidth = width),
                        )
                    }
            }
        }
        compose.waitForIdle()
        return results
    }

    private fun assertWrapsBetweenKinds(text: String, fontScale: Float) {
        val all = layouts(text, fontScale)
        assertTrue(all.any { it.lineCount > 1 }, "no width wrapped \"$text\", so this checks nothing")
        all.forEach { layout ->
            for (line in 0 until layout.lineCount - 1) {
                val end = layout.getLineEnd(line)
                val head = text.substring(0, end).trimEnd()
                val tail = text.substring(end).trimStart()
                assertFalse(
                    head.last().isDigit(),
                    "at ${layout.size.width}px and ${fontScale}x a count is stranded: \"$head\" / \"$tail\"",
                )
                assertFalse(
                    tail.startsWith("·"),
                    "at ${layout.size.width}px and ${fontScale}x a line starts with the dot: \"$tail\"",
                )
            }
        }
    }

    private val waiting = listOf(
        line(NeedsYouKind.Message to 1, NeedsYouKind.Invitation to 1, NeedsYouKind.Ticket to 1),
        line(
            NeedsYouKind.NotSent to 1,
            NeedsYouKind.Message to 12,
            NeedsYouKind.Mention to 3,
            NeedsYouKind.Ticket to 2,
        ),
    )

    @Test
    fun wrapsOnlyBetweenKindsAtDefaultSize() = waiting.forEach { assertWrapsBetweenKinds(it, 1f) }

    @Test
    fun wrapsOnlyBetweenKindsAtLargeFont() = waiting.forEach { assertWrapsBetweenKinds(it, 2f) }

    private companion object {
        const val MIN_WIDTH_DP = 60
        const val MAX_WIDTH_DP = 360
    }
}
