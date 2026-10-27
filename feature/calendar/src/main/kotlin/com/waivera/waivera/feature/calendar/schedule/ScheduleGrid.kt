package com.waivera.waivera.feature.calendar.schedule

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.waivera.waivera.core.designsystem.theme.WaiveraShapes
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTextStyles
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.feature.calendar.R
import com.waivera.waivera.feature.calendar.model.ScheduleText
import com.waivera.waivera.feature.calendar.model.ScheduleView
import com.waivera.waivera.feature.calendar.ui.CalendarSizes
import com.waivera.waivera.feature.calendar.ui.currentLocale
import java.time.LocalDate

private const val DAYS_PER_WEEK = 7

/**
 * Month grid (always 42 cells) or week strip (7 cells). Each cell is one selectable control at
 * least 48dp tall whose label says the date, whether it is today, and how many events and
 * celebrations it has, so the dots are never the only signal.
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
    val headers = remember(locale) { ScheduleText.weekdayHeaders(locale) }
    val gridLabel = stringResource(R.string.feature_calendar_grid_label)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = WaiveraSpacing.MediumSmall)
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
    val shape = WaiveraShapes.Card
    val background by animateColorAsState(
        targetValue = if (cell.isSelected) colors.accentSoft else Color.Transparent,
        animationSpec = WaiveraTheme.motion.state(),
        label = "cellBackground",
    )
    val numberColor = when {
        cell.isSelected -> colors.onAccentSoft
        !cell.inPeriod -> colors.textSecondary
        else -> colors.text
    }
    val label = cellLabel(cell)
    Box(
        modifier = modifier
            .padding(WaiveraSpacing.XxSmall)
            .heightIn(min = if (showWeekday) CalendarSizes.WeekCellMinHeight else WaiveraSpacing.MinTouchTarget)
            .clip(shape)
            .background(background)
            .border(CalendarSizes.CellBorder, if (cell.isToday) colors.accent else Color.Transparent, shape)
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
                Text(
                    text = ScheduleText.weekdayShort(cell.date, locale),
                    style = WaiveraTextStyles.Eyebrow,
                    color = if (cell.isSelected) colors.onAccentSoft else colors.textSecondary,
                )
            }
            Text(
                text = ScheduleText.dayOfMonth(cell.date, locale),
                style = if (showWeekday) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleSmall,
                fontWeight = if (cell.isToday || cell.isSelected) FontWeight.Black else FontWeight.SemiBold,
                color = numberColor,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(CalendarSizes.DotGap),
                modifier = Modifier.heightIn(min = CalendarSizes.Dot),
            ) {
                if (cell.marker.events > 0) Dot(colors.accent)
                if (cell.marker.milestones > 0) Dot(colors.milestoneDot)
            }
        }
    }
}

@Composable
private fun Dot(color: Color) {
    Box(
        modifier = Modifier
            .size(CalendarSizes.Dot)
            .background(color, WaiveraShapes.Circle),
    )
}

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
