package com.waivera.waivera.feature.tasks

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.waivera.waivera.core.data.model.TaskState
import com.waivera.waivera.core.designsystem.component.BadgeTone
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * How a due date reads next to today. Due dates are literal calendar dates and "today" is the
 * employee's saved-zone day, so a task due Friday reads "Due Fri, Nov 6" wherever the phone is.
 */
@Immutable
sealed interface DueText {
    val overdue: Boolean get() = false

    data object None : DueText

    data object Today : DueText

    data object Tomorrow : DueText

    data class On(val label: String) : DueText

    data object Yesterday : DueText {
        override val overdue: Boolean get() = true
    }

    data class WasOn(val label: String) : DueText {
        override val overdue: Boolean get() = true
    }
}

object TaskFormat {
    // Read the locale on each call, so a language change while the app runs is picked up.
    // Clock times are 12-hour across the app (pitfall 39).
    private fun pattern(value: String): DateTimeFormatter = DateTimeFormatter.ofPattern(value, Locale.getDefault())
    private val SameYear get() = pattern("EEE, MMM d")
    private val OtherYear get() = pattern("MMM d, yyyy")
    private val PastSameYear get() = pattern("MMM d")
    private val Clock get() = pattern("h:mm a")
    private val Day get() = pattern("EEE, MMM d")

    fun due(due: LocalDate?, today: LocalDate): DueText = when {
        due == null -> DueText.None
        due == today -> DueText.Today
        due == today.plusDays(1) -> DueText.Tomorrow
        due == today.minusDays(1) -> DueText.Yesterday
        due.isBefore(today) -> DueText.WasOn(
            if (due.year ==
                today.year
            ) {
                due.format(PastSameYear)
            } else {
                due.format(OtherYear)
            },
        )
        else -> DueText.On(date(due, today))
    }

    fun date(date: LocalDate, today: LocalDate): String =
        if (date.year == today.year) date.format(SameYear) else date.format(OtherYear)

    /** A wall-clock moment in the employee's zone: "Today, 2:30 PM". */
    @Immutable
    data class Moment(val relativeDay: RelativeDay, val day: String, val time: String)

    enum class RelativeDay { Today, Yesterday, Other }

    fun moment(at: Instant, zone: ZoneId, today: LocalDate): Moment {
        val local = at.atZone(zone)
        val day = local.toLocalDate()
        val relative = when (ChronoUnit.DAYS.between(day, today)) {
            0L -> RelativeDay.Today
            1L -> RelativeDay.Yesterday
            else -> RelativeDay.Other
        }
        val dayLabel = if (day.year == today.year) local.format(Day) else local.format(OtherYear)
        return Moment(relative, dayLabel, local.format(Clock))
    }

    fun tone(state: TaskState, overdue: Boolean): BadgeTone = when {
        state == TaskState.Completed || state == TaskState.Closed -> BadgeTone.Success
        overdue -> BadgeTone.Danger
        state == TaskState.InProgress -> BadgeTone.Accent
        state == TaskState.InReview -> BadgeTone.Warning
        else -> BadgeTone.Neutral
    }
}

@Composable
internal fun DueText.text(): String = when (this) {
    DueText.None -> stringResource(R.string.feature_tasks_no_due)
    DueText.Today -> stringResource(R.string.feature_tasks_due_today)
    DueText.Tomorrow -> stringResource(R.string.feature_tasks_due_tomorrow)
    is DueText.On -> stringResource(R.string.feature_tasks_due_on, label)
    DueText.Yesterday -> stringResource(R.string.feature_tasks_was_due_yesterday)
    is DueText.WasOn -> stringResource(R.string.feature_tasks_was_due_on, label)
}

@Composable
internal fun TaskState.label(): String = when (this) {
    TaskState.NotStarted -> stringResource(R.string.feature_tasks_state_not_started)
    TaskState.InProgress -> stringResource(R.string.feature_tasks_state_in_progress)
    TaskState.InReview -> stringResource(R.string.feature_tasks_state_in_review)
    TaskState.Completed -> stringResource(R.string.feature_tasks_state_completed)
    TaskState.Closed -> stringResource(R.string.feature_tasks_state_closed)
    is TaskState.Other -> name.lowercase().replaceFirstChar(Char::uppercaseChar)
}

@Composable
internal fun TaskFormat.Moment.text(): String = when (relativeDay) {
    TaskFormat.RelativeDay.Today -> stringResource(R.string.feature_tasks_today_at, time)
    TaskFormat.RelativeDay.Yesterday -> stringResource(R.string.feature_tasks_yesterday_at, time)
    TaskFormat.RelativeDay.Other -> stringResource(R.string.feature_tasks_day_at, day, time)
}

/** "1 hr 15 min", "45 min", "2 hr". */
@Composable
internal fun durationText(minutes: Int): String {
    val hours = minutes / MINUTES_PER_HOUR
    val rest = minutes % MINUTES_PER_HOUR
    val hourText = pluralStringResource(R.plurals.feature_tasks_hours, hours, hours)
    val minuteText = pluralStringResource(R.plurals.feature_tasks_minutes, rest, rest)
    return when {
        hours == 0 -> minuteText
        rest == 0 -> hourText
        else -> stringResource(R.string.feature_tasks_hours_minutes, hourText, minuteText)
    }
}

internal const val MINUTES_PER_HOUR = 60
