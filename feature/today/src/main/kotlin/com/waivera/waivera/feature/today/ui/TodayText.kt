package com.waivera.waivera.feature.today.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.waivera.waivera.core.network.dto.ChatActivityType
import com.waivera.waivera.feature.today.R
import com.waivera.waivera.feature.today.model.ActivitySummary
import com.waivera.waivera.feature.today.model.CelebrationItem
import com.waivera.waivera.feature.today.model.MessageSnippet
import com.waivera.waivera.feature.today.model.PastLabel
import com.waivera.waivera.feature.today.model.UpcomingLabel
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Clock and date formats. Times are always 12-hour (pitfall 39); values arrive already in the employee's zone. */
internal class TodayFormats(locale: Locale) {
    private val time = DateTimeFormatter.ofPattern("h:mm a", locale)
    private val day = DateTimeFormatter.ofPattern("EEE, MMM d", locale)
    private val longDay = DateTimeFormatter.ofPattern("EEEE, MMMM d", locale)

    fun time(value: LocalTime): String = time.format(value)

    fun day(value: LocalDate): String = day.format(value)

    fun longDay(value: LocalDate): String = longDay.format(value)
}

@Composable
internal fun rememberTodayFormats(): TodayFormats {
    val locale = LocalConfiguration.current.locales[0]
    return remember(locale) { TodayFormats(locale) }
}

@Composable
internal fun upcomingText(label: UpcomingLabel, formats: TodayFormats): String = when (label) {
    is UpcomingLabel.Now -> label.until
        ?.let { stringResource(R.string.feature_today_upcoming_now_until, formats.time(it)) }
        ?: stringResource(R.string.feature_today_upcoming_now)
    is UpcomingLabel.InMinutes -> if (label.minutes <= 0) {
        stringResource(R.string.feature_today_upcoming_starting_now)
    } else {
        pluralStringResource(R.plurals.feature_today_upcoming_in_minutes, label.minutes.toInt(), label.minutes.toInt())
    }
    is UpcomingLabel.InHours -> if (label.minutes == 0L) {
        pluralStringResource(R.plurals.feature_today_upcoming_in_hours, label.hours.toInt(), label.hours.toInt())
    } else {
        stringResource(R.string.feature_today_upcoming_in_hours_minutes, label.hours.toInt(), label.minutes.toInt())
    }
    is UpcomingLabel.TodayAt -> stringResource(R.string.feature_today_upcoming_today_at, formats.time(label.time))
    is UpcomingLabel.TomorrowAt -> stringResource(R.string.feature_today_upcoming_tomorrow_at, formats.time(label.time))
    is UpcomingLabel.OnDayAt ->
        stringResource(R.string.feature_today_upcoming_day_at, formats.day(label.date), formats.time(label.time))
    UpcomingLabel.AllDayToday -> stringResource(R.string.feature_today_all_day_today)
    UpcomingLabel.AllDayTomorrow -> stringResource(R.string.feature_today_all_day_tomorrow)
    is UpcomingLabel.AllDayOn -> stringResource(R.string.feature_today_all_day_on, formats.day(label.date))
}

/** The compact label shown on rows ("Now", "5m", "9:12 AM", "Yesterday", "Oct 30"). */
@Composable
internal fun pastText(label: PastLabel, formats: TodayFormats): String = when (label) {
    PastLabel.JustNow -> stringResource(R.string.feature_today_past_now)
    is PastLabel.MinutesAgo -> stringResource(R.string.feature_today_past_minutes, label.minutes.toInt())
    is PastLabel.TodayAt -> formats.time(label.time)
    PastLabel.Yesterday -> stringResource(R.string.feature_today_past_yesterday)
    is PastLabel.OnDate -> formats.day(label.date)
}

/** The spoken form: "5 minutes ago" instead of "5m". */
@Composable
internal fun pastSpokenText(label: PastLabel, formats: TodayFormats): String = when (label) {
    is PastLabel.MinutesAgo ->
        pluralStringResource(R.plurals.feature_today_past_minutes_a11y, label.minutes.toInt(), label.minutes.toInt())
    else -> pastText(label, formats)
}

@Composable
internal fun staleText(label: PastLabel, formats: TodayFormats): String = when (label) {
    PastLabel.JustNow -> stringResource(R.string.feature_today_stale_minutes, 0)
    is PastLabel.MinutesAgo -> stringResource(R.string.feature_today_stale_minutes, label.minutes.toInt())
    is PastLabel.TodayAt -> stringResource(R.string.feature_today_stale_at, formats.time(label.time))
    PastLabel.Yesterday -> stringResource(R.string.feature_today_stale_yesterday)
    is PastLabel.OnDate -> stringResource(R.string.feature_today_stale_on, formats.day(label.date))
}

@Composable
internal fun snippetText(snippet: MessageSnippet): String = when (snippet) {
    is MessageSnippet.Text -> snippet.text
    MessageSnippet.Photo -> stringResource(R.string.feature_today_snippet_photo)
    is MessageSnippet.File -> snippet.name ?: stringResource(R.string.feature_today_snippet_file)
    MessageSnippet.Deleted -> stringResource(R.string.feature_today_snippet_deleted)
    MessageSnippet.None -> stringResource(R.string.feature_today_snippet_none)
}

@Composable
internal fun summaryText(summary: ActivitySummary): String = when (summary) {
    is ActivitySummary.Server -> summary.text
    is ActivitySummary.Reaction -> {
        val actor = summary.actor ?: stringResource(R.string.feature_today_activity_someone)
        summary.emoji?.let { stringResource(R.string.feature_today_activity_reacted_with, actor, it) }
            ?: stringResource(R.string.feature_today_activity_reacted, actor)
    }
    ActivitySummary.Fallback -> stringResource(R.string.feature_today_activity_fallback)
}

@Composable
internal fun activityKindText(type: ChatActivityType): String = stringResource(
    when (type) {
        ChatActivityType.MENTION -> R.string.feature_today_kind_mention
        ChatActivityType.THREAD_REPLY -> R.string.feature_today_kind_reply
        ChatActivityType.REACTION -> R.string.feature_today_kind_reaction
        ChatActivityType.UNKNOWN -> R.string.feature_today_kind_update
    },
)

@Composable
internal fun celebrationText(item: CelebrationItem): String {
    val name = item.name ?: stringResource(R.string.feature_today_celebration_name_fallback)
    val years = item.anniversaryYears
    return when {
        item.isBirthday && years != null -> stringResource(R.string.feature_today_celebration_both, name, years)
        years != null -> stringResource(R.string.feature_today_celebration_anniversary, name, years)
        else -> stringResource(R.string.feature_today_celebration_birthday, name)
    }
}

@Composable
internal fun joined(first: String, second: String?): String =
    if (second.isNullOrEmpty()) first else stringResource(R.string.feature_today_separator, first, second)
