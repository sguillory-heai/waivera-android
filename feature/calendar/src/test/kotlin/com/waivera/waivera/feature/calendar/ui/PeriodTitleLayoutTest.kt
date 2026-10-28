package com.waivera.waivera.feature.calendar.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.waivera.waivera.core.designsystem.theme.WaiveraMotionScheme
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.core.screenshottesting.ScreenshotSdk
import com.waivera.waivera.feature.calendar.R
import com.waivera.waivera.feature.calendar.model.ScheduleText
import java.time.LocalDate
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Schedule period title at 200% in the slot it gets on the small phone, between the two 48dp
 * arrows (360dp less the gutters and arrows, about 232dp): a wrap never splits a number, and the
 * month keeps its day (UI5-04).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotSdk.VALUE], qualifiers = "w360dp-h640dp-notnight-xhdpi")
class PeriodTitleLayoutTest {
    @get:Rule
    val compose = createComposeRule()

    private val date = LocalDate.of(2026, 11, 1)

    @Test
    fun weekTitleNeverSplitsTheYear() {
        val title = RuntimeEnvironment.getApplication().getString(
            R.string.feature_calendar_period_week_in_month,
            ScheduleText.monthDay(date, Locale.US),
            ScheduleText.dayOfMonth(date.plusDays(WEEK_END_OFFSET), Locale.US),
            ScheduleText.year(date, Locale.US),
        )
        val layout = render(title)
        assertNoNumberSplit(title, layout)
        assertMonthKeepsItsDay(title, layout)
    }

    @Test
    fun dayTitleKeepsTheMonthWithItsDay() {
        val title = ScheduleText.weekdayMonthDay(date, Locale.US)
        val layout = render(title)
        assertNoNumberSplit(title, layout)
        assertMonthKeepsItsDay(title, layout)
    }

    private fun render(title: String): TextLayoutResult {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, LARGE_FONT)) {
                WaiveraTheme(darkTheme = false, motion = WaiveraMotionScheme.Reduced, hapticsEnabled = false) {
                    Box(Modifier.width(SLOT_WIDTH)) {
                        Text(text = title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                    }
                }
            }
        }
        compose.waitForIdle()
        val results = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(title).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        return results.first()
    }

    private fun assertNoNumberSplit(title: String, layout: TextLayoutResult) {
        for (line in 0 until layout.lineCount - 1) {
            val end = layout.getLineEnd(line)
            assertFalse(
                "\"$title\" splits a number at $end",
                title[end - 1].isDigit() && title.getOrNull(end)?.isDigit() == true,
            )
        }
    }

    private fun assertMonthKeepsItsDay(title: String, layout: TextLayoutResult) {
        val month = title.indexOf(MONTH)
        assertEquals(
            "\"$title\" leaves the day apart from \"$MONTH\"",
            layout.getLineForOffset(month),
            layout.getLineForOffset(month + MONTH.length + 1),
        )
    }

    private companion object {
        const val LARGE_FONT = 2f
        const val WEEK_END_OFFSET = 6L
        const val MONTH = "November"
        val SLOT_WIDTH = 232.dp
    }
}
