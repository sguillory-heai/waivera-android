package com.waivera.waivera.feature.calendar

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.waivera.waivera.core.designsystem.theme.WaiveraMotionScheme
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.core.screenshottesting.ScreenshotSdk
import com.waivera.waivera.feature.calendar.model.ScheduleText
import java.time.LocalDate
import java.util.Locale
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Week and Month agenda's day heading at 200% on the small phone, beside the Today chip
 * (UI11-04): the day number never sits alone on the last line ("Sunday, / November / 1").
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotSdk.VALUE], qualifiers = "w360dp-h640dp-notnight-xhdpi")
class AgendaDayHeadingLayoutTest {
    @get:Rule
    val compose = createComposeRule()

    private var date by mutableStateOf(LocalDate.of(2026, 11, 1))

    @Test
    fun theDayNumberNeverStandsAloneAtLargeFont() {
        show()
        // 1 November 2026 is the DST fall-back day in the demo zone; the others have two-digit days.
        for (day in listOf(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 9, 30), LocalDate.of(2026, 12, 23))) {
            date = day
            compose.waitForIdle()
            val shown = ScheduleText.weekdayMonthDay(day, Locale.US, shortMonth = true)
            val layout = layoutOf(shown)
            val lastLine = shown.substring(layout.getLineStart(layout.lineCount - 1)).trim()
            assertFalse("\"$shown\" leaves \"$lastLine\" on a line of its own", lastLine.all(Char::isDigit))
        }
    }

    private fun show() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, LARGE_FONT)) {
                WaiveraTheme(darkTheme = false, motion = WaiveraMotionScheme.Reduced, hapticsEnabled = false) {
                    Box(Modifier.width(HEADER_WIDTH)) { AgendaDayHeading(date = date, isToday = true) }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun layoutOf(shown: String): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(shown, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        return results.first()
    }

    private companion object {
        const val LARGE_FONT = 2f

        /** 360dp less the 16dp gutters and the header card's 12dp padding. */
        val HEADER_WIDTH = 304.dp
    }
}
