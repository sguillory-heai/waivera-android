package com.waivera.waivera.feature.calendar.schedule

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.waivera.waivera.core.designsystem.component.WaiveraIconTile
import com.waivera.waivera.core.designsystem.component.WaiveraPressable
import com.waivera.waivera.core.designsystem.icon.WaiveraIcons
import com.waivera.waivera.core.designsystem.theme.WaiveraShapes
import com.waivera.waivera.core.designsystem.theme.WaiveraSizes
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.feature.calendar.R
import com.waivera.waivera.feature.calendar.model.AgendaItem
import com.waivera.waivera.feature.calendar.model.ScheduleText
import com.waivera.waivera.feature.calendar.ui.CalendarIcons
import com.waivera.waivera.feature.calendar.ui.CalendarSizes
import com.waivera.waivera.feature.calendar.ui.CelebrationText
import com.waivera.waivera.feature.calendar.ui.MilestoneAvatar
import com.waivera.waivera.feature.calendar.ui.agendaTimeText
import com.waivera.waivera.feature.calendar.ui.milestoneSubtitle
import com.waivera.waivera.feature.calendar.ui.resolve
import com.waivera.waivera.feature.calendar.ui.timeOffSubtitle

/** One agenda card. Rows grow with the font scale; titles wrap to two lines instead of clipping. */
@Composable
internal fun AgendaCard(
    item: AgendaItem,
    currentYear: Int,
    onOpenEvent: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (item) {
        is AgendaItem.MilestoneCard -> MilestoneCard(item, modifier)
        is AgendaItem.HolidayCard -> InfoCard(
            icon = WaiveraIcons.Calendar,
            iconTint = WaiveraTheme.colors.holidayIcon,
            title = item.holiday.title?.takeIf(String::isNotBlank)
                ?: stringResource(R.string.feature_calendar_holiday_default),
            subtitle = stringResource(R.string.feature_calendar_holiday_subtitle),
            modifier = modifier,
        )
        is AgendaItem.TimeOffCard -> InfoCard(
            icon = CalendarIcons.TimeOff,
            iconTint = WaiveraTheme.colors.success,
            title = item.entry.leaveType?.takeIf(String::isNotBlank)
                ?: stringResource(R.string.feature_calendar_time_off_default),
            subtitle = timeOffSubtitle(item.entry),
            modifier = modifier,
        )
        is AgendaItem.EventCard -> EventCard(item, currentYear, onOpenEvent, modifier)
    }
}

@Composable
private fun MilestoneCard(item: AgendaItem.MilestoneCard, modifier: Modifier = Modifier) {
    val fallbackName = stringResource(R.string.feature_calendar_celebration_name_fallback)
    val name = item.occurrence.milestone.displayName?.takeIf(String::isNotBlank) ?: fallbackName
    CardSurface(modifier = modifier.semantics(mergeDescendants = true) { }) {
        MilestoneAvatar(name = name, celebration = item.celebration, size = CalendarSizes.MilestoneCardAvatar)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.XxSmall)) {
            Text(
                text = CelebrationText.full(item.celebration, fallbackName).resolve(),
                style = MaterialTheme.typography.titleSmall,
                color = WaiveraTheme.colors.text,
            )
            Text(
                text = milestoneSubtitle(item.occurrence),
                style = MaterialTheme.typography.labelMedium,
                color = WaiveraTheme.colors.textSecondary,
            )
        }
    }
}

@Composable
private fun InfoCard(
    @DrawableRes icon: Int,
    iconTint: Color,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    CardSurface(modifier = modifier.semantics(mergeDescendants = true) { }) {
        Box(
            modifier = Modifier
                .size(WaiveraSizes.IconTile)
                .background(WaiveraTheme.colors.surfaceMuted, WaiveraShapes.Button),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(WaiveraSizes.Icon),
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.XxSmall)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall, color = WaiveraTheme.colors.text)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelMedium,
                color = WaiveraTheme.colors.textSecondary,
            )
        }
    }
}

/**
 * A pressable event card with a leading bar in the owner's colour (accent when none). The
 * colour is decoration only; the organizer is also named in text.
 */
@Composable
private fun EventCard(
    item: AgendaItem.EventCard,
    currentYear: Int,
    onOpenEvent: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = WaiveraTheme.colors
    val event = item.event
    val ownerColor = remember(event.ownerColor) { ScheduleText.parseOwnerColor(event.ownerColor)?.let { Color(it) } }
    val time = agendaTimeText(item.timing, currentYear)
    val subtitle = event.location?.takeIf(String::isNotBlank)
        ?.let { stringResource(R.string.feature_calendar_with_detail, time, it) }
        ?: time
    WaiveraPressable(
        onClick = { onOpenEvent(event.id) },
        onClickLabel = stringResource(R.string.feature_calendar_open_event),
        shape = WaiveraShapes.ListCard,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .heightIn(min = CalendarSizes.EventCardMinHeight)
                .clip(WaiveraShapes.ListCard)
                .background(colors.surface)
                .border(WaiveraSpacing.Hairline, colors.border, WaiveraShapes.ListCard),
        ) {
            Box(
                modifier = Modifier
                    .width(CalendarSizes.EventAccentBar)
                    .fillMaxHeight()
                    .background(ownerColor ?: colors.accent),
            )
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = WaiveraSpacing.MediumSmall, vertical = WaiveraSpacing.MediumSmall),
                horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.MediumSmall),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                WaiveraIconTile(
                    icon = if (event.online ||
                        event.isSprout
                    ) {
                        CalendarIcons.Video
                    } else {
                        WaiveraIcons.Schedule
                    },
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.XxSmall),
                ) {
                    Text(
                        text = event.title?.takeIf(String::isNotBlank)
                            ?: stringResource(R.string.feature_calendar_untitled_event),
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.text,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(text = subtitle, style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
                    event.ownerName?.takeIf(String::isNotBlank)?.let { owner ->
                        Text(
                            text = stringResource(R.string.feature_calendar_owner, owner),
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.textSecondary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CardSurface(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = WaiveraSizes.ListRowMinHeight)
            .clip(WaiveraShapes.ListCard)
            .background(WaiveraTheme.colors.surface)
            .border(WaiveraSpacing.Hairline, WaiveraTheme.colors.border, WaiveraShapes.ListCard)
            .padding(WaiveraSpacing.MediumSmall),
        horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.MediumSmall),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}
