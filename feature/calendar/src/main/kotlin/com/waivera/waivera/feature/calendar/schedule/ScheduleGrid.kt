package com.waivera.waivera.feature.calendar.schedule

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import com.waivera.waivera.core.designsystem.theme.WaiveraShapes
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTextStyles
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.feature.calendar.R
import com.waivera.waivera.feature.calendar.model.DayMarker
import com.waivera.waivera.feature.calendar.model.ScheduleText
import com.waivera.waivera.feature.calendar.model.ScheduleView
import com.waivera.waivera.feature.calendar.ui.CalendarShapes
import com.waivera.waivera.feature.calendar.ui.CalendarSizes
import com.waivera.waivera.feature.calendar.ui.currentLocale
import java.time.LocalDate

private const val DAYS_PER_WEEK = 7

/**
 * Month grid (always 42 cells) or week strip (7 cells). Each cell is one selectable control at
 * least 48dp tall whose label says the date, whether it is today, and how many events and
 * celebrations it has, so the dots are never the only signal.
 *
 * v5 decision 4: the selected day is a solid 40dp violet circle with a white number, the one bold
 * mark on the grid; today is a bold violet number (and weekday, in the week strip) with no disc,
 * and today when selected is the circle. The weekday never sits inside the selection. Event dots
 * take their owners' colours (accent when none), so a busy week reads at a glance.
 */
@Composable
internal fun ScheduleGrid(
    view: ScheduleView,
    cells: List<GridCell>,
    onSelectDate: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (view == ScheduleView.DAY || cells.isEmpty()) return
    val locale = currentLocale()
    val narrow = usesCompactCalendar(LocalDensity.current.fontScale)
    val headers = remember(locale, narrow) { ScheduleText.weekdayHeaders(locale, narrow) }
    val gridLabel = stringResource(R.string.feature_calendar_grid_label)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = WaiveraSpacing.ExtraSmall)
            .semantics { contentDescription = gridLabel }
            .selectableGroup(),
    ) {
        if (view == ScheduleView.MONTH) {
            Row(modifier = Modifier.fillMaxWidth().clearAndSetSemantics { }) {
                headers.forEach { header ->
                    Text(
                        text = header,
                        style = WaiveraTextStyles.Eyebrow,
                        color = WaiveraTheme.colors.textSecondary,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = WaiveraSpacing.ExtraSmall),
                    )
                }
            }
        }
        cells.chunked(DAYS_PER_WEEK).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth()) {
                week.forEach { cell ->
                    DayCell(
                        cell = cell,
                        showWeekday = view == ScheduleView.WEEK,
                        onClick = { onSelectDate(cell.date) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun DayCell(cell: GridCell, showWeekday: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = WaiveraTheme.colors
    val locale = currentLocale()
    val circle by animateFloatAsState(
        targetValue = if (cell.isSelected) 1f else 0f,
        animationSpec = WaiveraTheme.motion.state(),
        label = "dayCircle",
    )
    val numberColor = when {
        cell.isSelected -> colors.onAccent
        cell.isToday -> colors.link
        // The other month's dates recede, so this month's read as the grid (UI10-16).
        !cell.inPeriod -> colors.textTertiary
        else -> colors.text
    }
    val label = cellLabel(cell)
    val narrow = usesCompactCalendar(LocalDensity.current.fontScale)
    Box(
        modifier = modifier
            .padding(WaiveraSpacing.XxSmall)
            .heightIn(min = if (showWeekday) CalendarSizes.WeekCellMinHeight else WaiveraSpacing.MinTouchTarget)
            .clip(WaiveraShapes.Tile)
            .selectable(selected = cell.isSelected, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label }
            .padding(vertical = WaiveraSpacing.ExtraSmall),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.XxSmall),
            modifier = Modifier.clearAndSetSemantics { },
        ) {
            if (showWeekday) {
                // The weekday sits outside the selection; today's is bold violet, as its number is.
                Text(
                    text = if (narrow) {
                        ScheduleText.weekdayNarrow(cell.date, locale)
                    } else {
                        ScheduleText.weekdayShort(cell.date, locale)
                    },
                    style = WaiveraTextStyles.Eyebrow,
                    color = if (cell.isToday) colors.link else colors.textSecondary,
                    fontWeight = if (cell.isToday) FontWeight.ExtraBold else null,
                    maxLines = 1,
                    softWrap = false,
                )
            }
            val disc = if (showWeekday) CalendarSizes.DayCircleLarge else CalendarSizes.DayCircle
            val type = MaterialTheme.typography
            val numberStyle = if (showWeekday) type.titleLarge else type.titleSmall
            val fill = colors.accentFill
            Box(
                modifier = Modifier
                    .defaultMinSize(minWidth = disc, minHeight = disc)
                    // v5: the selected day is a solid violet circle with a white number. It fades in
                    // on the state token and snaps under reduced motion. At large font a wide number
                    // stretches it into a stadium rather than clipping.
                    .drawBehind {
                        if (circle > 0f) {
                            drawRoundRect(color = fill, alpha = circle, cornerRadius = CornerRadius(size.height / 2f))
                        }
                    }
                    .padding(horizontal = WaiveraSpacing.ExtraSmall),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = ScheduleText.dayOfMonth(cell.date, locale),
                    style = numberStyle,
                    fontWeight = if (cell.isToday || cell.isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                    color = numberColor,
                    maxLines = 1,
                    softWrap = false,
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(CalendarSizes.DotGap),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.heightIn(min = CalendarSizes.MilestoneDot),
            ) {
                EventDots(cell.marker)
                if (cell.marker.milestones > 0) {
                    Dot(color = colors.milestoneDot, shape = CalendarShapes.Diamond, size = CalendarSizes.MilestoneDot)
                }
            }
        }
    }
}

/**
 * One circle per event colour on the day (accent for an event without one). The dots sit under
 * the selection circle, on the card, so they keep their owners' colours on the selected day too.
 */
@Composable
private fun EventDots(marker: DayMarker) {
    if (marker.events == 0) return
    val accent = WaiveraTheme.colors.accent
    val dots = marker.eventColors.ifEmpty { listOf(null) }
    dots.forEach { argb ->
        val color = argb?.let { Color(it) } ?: accent
        Dot(color = color, shape = WaiveraShapes.Circle, size = CalendarSizes.Dot)
    }
}

/** Events are circles and celebrations diamonds, so the two read apart without colour. */
@Composable
private fun Dot(color: Color, shape: Shape, size: Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .background(color, shape),
    )
}

/** At 150% font and above the week strip uses one-letter weekdays and Month defaults to Week. */
internal fun usesCompactCalendar(fontScale: Float): Boolean = fontScale >= COMPACT_CALENDAR_FONT_SCALE

internal const val COMPACT_CALENDAR_FONT_SCALE = 1.5f

/** "Sunday, November 1, today, 2 events, 1 celebration". Selection is announced by the selectable. */
@Composable
private fun cellLabel(cell: GridCell): String {
    val parts = buildList {
        add(ScheduleText.fullDay(cell.date, currentLocale()))
        if (cell.isToday) add(stringResource(R.string.feature_calendar_cell_today))
        if (!cell.inPeriod) add(stringResource(R.string.feature_calendar_cell_other_month))
        if (cell.marker.events > 0) {
            add(pluralStringResource(R.plurals.feature_calendar_cell_events, cell.marker.events, cell.marker.events))
        }
        if (cell.marker.milestones > 0) {
            add(
                pluralStringResource(
                    R.plurals.feature_calendar_cell_celebrations,
                    cell.marker.milestones,
                    cell.marker.milestones,
                ),
            )
        }
    }
    return parts.joinToString(", ")
}
