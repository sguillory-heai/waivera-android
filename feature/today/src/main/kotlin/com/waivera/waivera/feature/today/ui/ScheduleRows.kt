package com.waivera.waivera.feature.today.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.waivera.waivera.core.designsystem.component.WaiveraPressable
import com.waivera.waivera.core.designsystem.component.WaiveraTileTone
import com.waivera.waivera.core.designsystem.component.tint
import com.waivera.waivera.core.designsystem.icon.WaiveraIcons
import com.waivera.waivera.core.designsystem.preview.WaiveraPreviews
import com.waivera.waivera.core.designsystem.text.withBreaksAfterHyphens
import com.waivera.waivera.core.designsystem.theme.WaiveraElevation
import com.waivera.waivera.core.designsystem.theme.WaiveraShapes
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTextStyles
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.feature.today.R
import com.waivera.waivera.feature.today.model.NextUpItem
import com.waivera.waivera.feature.today.model.UpcomingLabel
import java.time.LocalTime

/** One of the next three schedule items: relative time first ("in 25 min"), then title, clock range and place. */
@Composable
internal fun NextUpRow(item: NextUpItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val formats = rememberTodayFormats()
    val colors = WaiveraTheme.colors
    val title = item.title ?: stringResource(R.string.feature_today_invitation_untitled)
    val time = upcomingText(item.time, formats)
    val range = timeRange(item, formats)
    val place = item.location ?: if (item.isOnline) stringResource(R.string.feature_today_online) else null
    val supporting = range?.let { joined(it, place) } ?: place
    val soon = item.isHappeningNow || item.time is UpcomingLabel.InMinutes
    val spokenNow = if (item.isHappeningNow) stringResource(R.string.feature_today_happening_now_a11y) else null
    TodayRow(
        headline = title,
        description = joined(joined(joined(time, title), supporting), spokenNow),
        onClick = onClick,
        onClickLabel = stringResource(R.string.feature_today_open_event),
        eyebrow = time,
        eyebrowColor = if (soon) WaiveraTileTone.Teal.tint(colors).content else colors.textSecondary,
        supporting = supporting,
        emphasized = item.isHappeningNow,
        showChevron = true,
        modifier = modifier,
        leading = {
            // Teal means "schedule" on every screen (UI8-11); "soon" is the accent eyebrow above.
            TodayTile(icon = WaiveraIcons.Schedule, tone = WaiveraTileTone.Teal)
        },
    )
}

/**
 * The first Up next item as concept A's large card: the place tile, title and place, a countdown
 * chip while it is close, and the clock time large ("11:00 AM – 12:00 PM"). It opens the event.
 */
@Composable
internal fun UpNextCard(item: NextUpItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val formats = rememberTodayFormats()
    val colors = WaiveraTheme.colors
    val teal = WaiveraTileTone.Teal.tint(colors)
    val title = item.title ?: stringResource(R.string.feature_today_invitation_untitled)
    val time = upcomingText(item.time, formats)
    val place = item.location ?: if (item.isOnline) stringResource(R.string.feature_today_online) else null
    val range = timeRange(item, formats)
    val showChip = item.isHappeningNow || item.time is UpcomingLabel.InMinutes || item.time is UpcomingLabel.InHours
    val spokenNow = if (item.isHappeningNow) stringResource(R.string.feature_today_happening_now_a11y) else null
    val description = joined(joined(joined(time, title), range?.let { joined(it, place) } ?: place), spokenNow)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = WaiveraSpacing.Gutter, vertical = WaiveraSpacing.ExtraSmall + WaiveraSpacing.Hairline)
            .shadow(elevation = WaiveraElevation.Card, shape = WaiveraShapes.UpNextCard, clip = false)
            .clip(WaiveraShapes.UpNextCard)
            .background(colors.surface),
    ) {
        WaiveraPressable(
            onClick = onClick,
            onClickLabel = stringResource(R.string.feature_today_open_event),
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = description },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(WaiveraSpacing.Medium)
                    .clearAndSetSemantics { },
                verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.MediumSmall),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.MediumSmall),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TodayTile(
                        icon = if (item.location != null) WaiveraIcons.Location else WaiveraIcons.Schedule,
                        tone = WaiveraTileTone.Teal,
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title.withBreaksAfterHyphens(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = colors.text,
                            maxLines = UP_NEXT_TITLE_LINES,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (place != null) {
                            Text(
                                text = place,
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    if (showChip) {
                        Text(
                            text = time,
                            style = WaiveraTextStyles.Chip,
                            color = teal.content,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier
                                .background(teal.container, WaiveraShapes.CountBadge)
                                .padding(horizontal = WaiveraSpacing.MediumSmall - WaiveraSpacing.XxSmall)
                                .padding(vertical = WaiveraSpacing.ExtraSmall),
                        )
                    }
                }
                val start = item.start
                if (start != null) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.Small),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        Text(text = formats.time(start), style = WaiveraTextStyles.DisplayTime, color = colors.text)
                        item.end?.let { end ->
                            Text(
                                text = stringResource(
                                    if (item.endsOnLaterDay) {
                                        R.string.feature_today_brand_until_next_day
                                    } else {
                                        R.string.feature_today_brand_until_dash
                                    },
                                    formats.time(end),
                                ),
                                style = WaiveraTextStyles.ClockTime,
                                color = colors.textSecondary,
                                modifier = Modifier.padding(bottom = WaiveraSpacing.XxSmall),
                            )
                        }
                    }
                } else if (!showChip) {
                    Text(text = time, style = WaiveraTextStyles.ClockTime, color = colors.text)
                }
            }
        }
    }
}

private const val UP_NEXT_TITLE_LINES = 3

@Composable
private fun timeRange(item: NextUpItem, formats: TodayFormats): String? {
    val start = item.start
    val end = item.end
    val format = if (item.endsOnLaterDay) {
        R.string.feature_today_time_range_next_day
    } else {
        R.string.feature_today_time_range
    }
    return when {
        start == null -> null
        end == null -> formats.time(start)
        else -> stringResource(format, formats.time(start), formats.time(end))
    }
}

@WaiveraPreviews
@Composable
private fun ScheduleRowsPreview() {
    WaiveraTheme {
        Column(Modifier.background(WaiveraTheme.colors.background)) {
            UpNextCard(
                NextUpItem(
                    eventId = "e0",
                    title = "Client home visit",
                    time = UpcomingLabel.InMinutes(30),
                    start = LocalTime.of(11, 0),
                    end = LocalTime.of(12, 0),
                    endsOnLaterDay = false,
                    location = "1200 W Lake St",
                    isOnline = false,
                ),
                onClick = {},
            )
            NextUpRow(
                NextUpItem(
                    eventId = "e1",
                    title = "Client home visit",
                    time = UpcomingLabel.InMinutes(25),
                    start = LocalTime.of(11, 0),
                    end = LocalTime.of(12, 0),
                    endsOnLaterDay = false,
                    location = "1200 W Lake St",
                    isOnline = false,
                ),
                onClick = {},
            )
            NextUpRow(
                NextUpItem(
                    eventId = "e2",
                    title = "Daylight saving ends",
                    time = UpcomingLabel.AllDayToday,
                    start = null,
                    end = null,
                    endsOnLaterDay = false,
                    location = null,
                    isOnline = false,
                ),
                onClick = {},
            )
        }
    }
}
