package com.waivera.waivera.feature.today.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.waivera.waivera.core.data.image.AvatarModel
import com.waivera.waivera.core.designsystem.component.MilestoneKind
import com.waivera.waivera.core.designsystem.component.WaiveraAvatar
import com.waivera.waivera.core.designsystem.icon.WaiveraIcons
import com.waivera.waivera.core.designsystem.preview.WaiveraPreviews
import com.waivera.waivera.core.designsystem.theme.WaiveraSizes
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.feature.today.R
import com.waivera.waivera.feature.today.model.CelebrationItem
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
        eyebrowColor = if (soon) colors.accent else colors.textSecondary,
        supporting = supporting,
        emphasized = item.isHappeningNow,
        showChevron = true,
        modifier = modifier,
        leading = { TodayIconTile(icon = WaiveraIcons.Schedule, muted = !soon) },
    )
}

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

/** A birthday or work anniversary today. The avatar ring and the sentence carry the same meaning. */
@Composable
internal fun CelebrationRow(item: CelebrationItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val sentence = celebrationText(item)
    val name = item.name ?: stringResource(R.string.feature_today_celebration_name_fallback)
    TodayRow(
        headline = sentence,
        description = sentence,
        onClick = onClick,
        onClickLabel = stringResource(R.string.feature_today_open_schedule),
        modifier = modifier,
        leading = {
            WaiveraAvatar(
                name = name,
                imageModel = AvatarModel.of(item.userId),
                size = WaiveraSizes.IconTile,
                milestone = if (item.isBirthday) MilestoneKind.Birthday else MilestoneKind.WorkAnniversary,
                contentDescription = null,
            )
        },
    )
}

@WaiveraPreviews
@Composable
private fun ScheduleRowsPreview() {
    WaiveraTheme {
        Column(Modifier.background(WaiveraTheme.colors.background)) {
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
                NextUpItem("e2", "Daylight saving ends", UpcomingLabel.AllDayToday, null, null, false, null, false),
                onClick = {},
            )
            CelebrationRow(CelebrationItem("u1", "Jordan Lee", isBirthday = false, anniversaryYears = 5), onClick = {})
        }
    }
}
