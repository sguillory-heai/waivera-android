package com.waivera.waivera.feature.calendar.schedule

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import com.waivera.waivera.core.designsystem.component.WaiveraBrandSurface
import com.waivera.waivera.core.designsystem.component.WaiveraEmojiTile
import com.waivera.waivera.core.designsystem.component.WaiveraIconTile
import com.waivera.waivera.core.designsystem.component.WaiveraPressable
import com.waivera.waivera.core.designsystem.component.WaiveraTileTone
import com.waivera.waivera.core.designsystem.icon.WaiveraIcons
import com.waivera.waivera.core.designsystem.theme.WaiveraBrand
import com.waivera.waivera.core.designsystem.theme.WaiveraShapes
import com.waivera.waivera.core.designsystem.theme.WaiveraSizes
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.feature.calendar.R
import com.waivera.waivera.feature.calendar.model.AgendaItem
import com.waivera.waivera.feature.calendar.model.ScheduleClock
import com.waivera.waivera.feature.calendar.model.ScheduleLabels
import com.waivera.waivera.feature.calendar.ui.CalendarIcons
import com.waivera.waivera.feature.calendar.ui.CalendarSizes
import com.waivera.waivera.feature.calendar.ui.CelebrationText
import com.waivera.waivera.feature.calendar.ui.MilestoneAvatar
import com.waivera.waivera.feature.calendar.ui.agendaTimeText
import com.waivera.waivera.feature.calendar.ui.currentLocale
import com.waivera.waivera.feature.calendar.ui.milestoneSubtitle
import com.waivera.waivera.feature.calendar.ui.resolve
import com.waivera.waivera.feature.calendar.ui.timeOffSubtitle
import java.time.ZonedDateTime

/**
 * One agenda card. Rows grow with the font scale; titles wrap to two lines instead of clipping.
 * An [ended] event or time off (today, before now) is drawn quieter: secondary text, no accent
 * bar, and announced as ended (UI4-12).
 */
@Composable
internal fun AgendaCard(
    item: AgendaItem,
    currentYear: Int,
    onOpenEvent: (String) -> Unit,
    modifier: Modifier = Modifier,
    ended: Boolean = false,
) {
    val endedLabel = stringResource(R.string.feature_calendar_item_ended)
    val endedModifier = if (ended) modifier.semantics { stateDescription = endedLabel } else modifier
    when (item) {
        is AgendaItem.MilestoneCard -> MilestoneCard(item, modifier)
        is AgendaItem.HolidayCard -> InfoCard(
            icon = WaiveraIcons.Calendar,
            tone = WaiveraTileTone.Brand,
            title = item.holiday.title?.takeIf(String::isNotBlank)
                ?: stringResource(R.string.feature_calendar_holiday_default),
            subtitle = item.holiday.description?.takeIf(String::isNotBlank)
                ?: stringResource(R.string.feature_calendar_holiday_subtitle),
            modifier = modifier,
        )
        is AgendaItem.TimeOffCard -> InfoCard(
            icon = CalendarIcons.TimeOff,
            tone = WaiveraTileTone.Teal,
            title = item.entry.leaveType?.takeIf(String::isNotBlank)
                ?: stringResource(R.string.feature_calendar_time_off_default),
            subtitle = timeOffSubtitle(item.entry),
            ended = ended,
            modifier = endedModifier,
        )
        is AgendaItem.EventCard -> EventCard(
            item = item,
            currentYear = currentYear,
            onOpenEvent = onOpenEvent,
            ended = ended,
            modifier = endedModifier,
        )
    }
}

/**
 * The thin "Now · 10:30 AM" line in today's agenda, before the first item that has not ended,
 * so the day reads as before and after at a glance (UI4-12). The time is in the employee's
 * saved zone. The line is the brand gradient: a small bit of life on the one moving mark.
 */
@Composable
internal fun NowDivider(time: ZonedDateTime, modifier: Modifier = Modifier) {
    val colors = WaiveraTheme.colors
    val clock = ScheduleClock.clock(time, currentLocale())
    val label = stringResource(R.string.feature_calendar_now_marker, clock)
    val spoken = stringResource(R.string.feature_calendar_now_marker_a11y, clock)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = spoken },
        horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.Small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = colors.accent)
        Box(
            modifier = Modifier
                .weight(1f)
                .height(CalendarSizes.NowLine)
                .background(WaiveraBrand.horizontalGradient(), WaiveraShapes.Pill),
        )
    }
}

/**
 * A birthday or anniversary on the day: a small brand moment, on the gradient wash with the
 * celebration emoji in its own tile (the sentence carries none, so it shows once).
 */
@Composable
private fun MilestoneCard(item: AgendaItem.MilestoneCard, modifier: Modifier = Modifier) {
    val fallbackName = stringResource(R.string.feature_calendar_celebration_name_fallback)
    val name = item.occurrence.milestone.displayName?.takeIf(String::isNotBlank) ?: fallbackName
    WaiveraBrandSurface(
        modifier = modifier
            .heightIn(min = WaiveraSizes.ListRowMinHeight)
            .semantics(mergeDescendants = true) { },
        shape = WaiveraShapes.ListCard,
        contentPadding = PaddingValues(WaiveraSpacing.MediumSmall),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.MediumSmall),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MilestoneAvatar(
                name = name,
                celebration = item.celebration,
                size = CalendarSizes.MilestoneCardAvatar,
                userId = item.occurrence.milestone.userId,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.XxSmall),
            ) {
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
            WaiveraEmojiTile(emoji = CelebrationText.emoji(item.celebration).resolve())
        }
    }
}

@Composable
private fun InfoCard(
    @DrawableRes icon: Int,
    tone: WaiveraTileTone,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    ended: Boolean = false,
) {
    CardSurface(modifier = modifier.semantics(mergeDescendants = true) { }) {
        WaiveraIconTile(icon = icon, tone = if (ended) WaiveraTileTone.Neutral else tone)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.XxSmall)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = if (ended) WaiveraTheme.colors.textSecondary else WaiveraTheme.colors.text,
            )
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
    ended: Boolean = false,
) {
    val colors = WaiveraTheme.colors
    val event = item.event
    val ownerColor = remember(event.ownerColor) { ScheduleLabels.parseOwnerColor(event.ownerColor)?.let { Color(it) } }
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
                    // An ended item drops its colour bar, so what is still ahead stands out.
                    .background(if (ended) colors.border else ownerColor ?: colors.accent),
            )
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = WaiveraSpacing.MediumSmall, vertical = WaiveraSpacing.MediumSmall),
                horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.MediumSmall),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                WaiveraIconTile(
                    tone = WaiveraTileTone.Teal,
                    icon = if (event.online ||
                        event.isSprout
                    ) {
                        WaiveraIcons.Video
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
                        color = if (ended) colors.textSecondary else colors.text,
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
