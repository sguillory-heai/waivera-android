package com.waivera.waivera.feature.today.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.waivera.waivera.core.designsystem.component.WaiveraDayTimeline
import com.waivera.waivera.core.designsystem.component.WaiveraPressable
import com.waivera.waivera.core.designsystem.component.WaiveraTileTone
import com.waivera.waivera.core.designsystem.component.WaiveraTimelineBlock
import com.waivera.waivera.core.designsystem.component.WaiveraTimelineLabel
import com.waivera.waivera.core.designsystem.component.WaiveraTimelineTone
import com.waivera.waivera.core.designsystem.component.tint
import com.waivera.waivera.core.designsystem.preview.WaiveraPreviews
import com.waivera.waivera.core.designsystem.theme.WaiveraShapes
import com.waivera.waivera.core.designsystem.theme.WaiveraSizes
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTextStyles
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.feature.today.R
import com.waivera.waivera.feature.today.model.DayBlock
import com.waivera.waivera.feature.today.model.DayHourLabel
import com.waivera.waivera.feature.today.model.DayTimeline
import com.waivera.waivera.feature.today.model.NextUpItem
import com.waivera.waivera.feature.today.model.UpcomingLabel
import java.time.Duration
import java.time.LocalTime

/**
 * Concept B's "Your day" card on Today's sheet: how many events and how much time today holds,
 * the day as a track (8 AM to 6 PM, wider when the day needs it) with a now-line, and today's
 * next timed event under it. Times are the employee's zone, worked out in the model.
 */
@Composable
internal fun YourDayCard(
    day: DayTimeline,
    next: NextUpItem?,
    onOpenEvent: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val formats = rememberTodayFormats()
    val summary = daySummary(day)
    val window = stringResource(
        R.string.feature_today_brand_day_track_a11y,
        summary,
        formats.time(day.windowStart),
        formats.time(day.windowEnd),
    )
    val labels = remember(day, formats) {
        day.labels.mapIndexed { index, label ->
            label.toTimeline(
                formats,
                isEnd =
                index == 0 || index == day.labels.lastIndex,
            )
        }
    }
    WaiveraDayTimeline(
        title = stringResource(R.string.feature_today_brand_day_title),
        summary = summary,
        trackDescription = window,
        blocks = remember(day) { day.blocks.map(DayBlock::toTimeline) },
        ticks = day.ticks,
        labels = labels,
        now = day.now,
        modifier = modifier.padding(horizontal = WaiveraSpacing.Gutter),
        footer = {
            if (next != null) DayNextRow(item = next, onClick = { onOpenEvent(next.eventId) })
        },
    )
}

/** "3 events · 2 hr scheduled", or "Your day is wide open." */
@Composable
private fun daySummary(day: DayTimeline): String {
    if (day.eventCount == 0) return stringResource(R.string.feature_today_brand_day_open)
    val events = pluralStringResource(R.plurals.feature_today_brand_day_events, day.eventCount, day.eventCount)
    return joined(events, scheduledText(day.scheduled))
}

@Composable
private fun scheduledText(scheduled: Duration): String? {
    val hours = scheduled.toHours().toInt()
    // Duration.toMinutesPart needs API 31; minSdk is 26.
    val minutes = (scheduled.toMinutes() % MINUTES_PER_HOUR).toInt()
    return when {
        hours > 0 && minutes > 0 -> stringResource(R.string.feature_today_brand_day_hours_minutes, hours, minutes)
        hours > 0 -> stringResource(R.string.feature_today_brand_day_hours, hours)
        minutes > 0 -> stringResource(R.string.feature_today_brand_day_minutes, minutes)
        else -> null
    }
}

/**
 * Today's next timed event under the track: the clock ("11:00 AM") with the countdown chip under
 * it while it is close, a teal dot, then the title with "Until 12:00 PM · place". The chip sits
 * under the clock rather than at the end so the title keeps the row's width on a small phone,
 * where it was squeezed into a narrow middle column ("Client home / visit"). It opens the event.
 */
@Composable
private fun DayNextRow(item: NextUpItem, onClick: () -> Unit) {
    val formats = rememberTodayFormats()
    val colors = WaiveraTheme.colors
    val teal = WaiveraTileTone.Teal.tint(colors)
    val start = item.start ?: return
    val title = item.title ?: stringResource(R.string.feature_today_invitation_untitled)
    val place = item.location ?: if (item.isOnline) stringResource(R.string.feature_today_online) else null
    val until = item.end?.let { stringResource(R.string.feature_today_brand_until, formats.time(it)) }
    val meta = until?.let { joined(it, place) } ?: place
    val upcoming = upcomingText(item.time, formats)
    val countdown = item.time is UpcomingLabel.InMinutes || item.time is UpcomingLabel.InHours
    val description = joined(joined(joined(formats.time(start), title), meta), upcoming)
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(WaiveraSpacing.Hairline)
                .background(colors.border),
        )
        WaiveraPressable(
            onClick = onClick,
            onClickLabel = stringResource(R.string.feature_today_open_event),
            shape = WaiveraShapes.Chip,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = description },
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = WaiveraSpacing.MediumSmall, bottom = WaiveraSpacing.XxSmall)
                    .clearAndSetSemantics { },
                horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.MediumSmall),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.ExtraSmall)) {
                    Text(text = formats.time(start), style = WaiveraTextStyles.ClockTime, color = colors.text)
                    if (countdown) CountdownChip(text = upcoming)
                }
                Box(
                    modifier = Modifier
                        .size(WaiveraSizes.TimelineNowDot + WaiveraSpacing.Small)
                        .background(teal.container, WaiveraShapes.Circle),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(WaiveraSizes.TimelineNowDot)
                            .background(teal.content, WaiveraShapes.Circle),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.text,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (meta != null) {
                        Text(
                            text = meta,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CountdownChip(text: String) {
    val teal = WaiveraTileTone.Teal.tint(WaiveraTheme.colors)
    Text(
        text = text,
        style = WaiveraTextStyles.Chip,
        color = teal.content,
        maxLines = 1,
        softWrap = false,
        modifier = Modifier
            .background(teal.container, WaiveraShapes.CountBadge)
            .padding(horizontal = WaiveraSpacing.Small, vertical = WaiveraSpacing.ExtraSmall),
    )
}

private fun DayBlock.toTimeline() = WaiveraTimelineBlock(
    start = start,
    end = end,
    tone = WaiveraTimelineTone.entries[tone % WaiveraTimelineTone.entries.size],
)

/** "8 AM" at the ends and at noon; the bare hour between ("10", "2"), as in the concept. */
private fun DayHourLabel.toTimeline(formats: TodayFormats, isEnd: Boolean) = WaiveraTimelineLabel(
    at = at,
    text = formats.hour(time, withPeriod = isEnd || time == LocalTime.NOON),
)

private const val MINUTES_PER_HOUR = 60

@WaiveraPreviews
@Composable
private fun YourDayCardPreview() {
    WaiveraTheme {
        Column(
            modifier = Modifier
                .background(WaiveraTheme.colors.background)
                .padding(vertical = WaiveraSpacing.Medium),
        ) {
            YourDayCard(day = TodayPreviewStates.day(), next = TodayPreviewStates.visit(), onOpenEvent = {})
        }
    }
}
