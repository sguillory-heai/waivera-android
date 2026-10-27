package com.waivera.waivera.feature.calendar.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.waivera.waivera.core.data.model.MilestoneOccurrence
import com.waivera.waivera.core.data.model.TimeOffEntry
import com.waivera.waivera.core.network.dto.InviteStatus
import com.waivera.waivera.core.network.dto.MilestoneType
import com.waivera.waivera.feature.calendar.R
import com.waivera.waivera.feature.calendar.model.EventTiming
import com.waivera.waivera.feature.calendar.model.PeriodTitle
import com.waivera.waivera.feature.calendar.model.ScheduleClock
import com.waivera.waivera.feature.calendar.model.ScheduleLabels
import com.waivera.waivera.feature.calendar.model.ScheduleText
import com.waivera.waivera.feature.calendar.model.ZoneNotice
import java.time.ZonedDateTime
import java.util.Locale

/** The person's first locale; dates and clocks follow it, zones never follow the device. */
@Composable
@ReadOnlyComposable
internal fun currentLocale(): Locale = LocalConfiguration.current.locales.get(0) ?: Locale.ROOT

@Composable
internal fun periodTitleText(title: PeriodTitle): String {
    val locale = currentLocale()
    return when (title) {
        is PeriodTitle.Month -> ScheduleText.monthYear(title.month.atDay(1), locale)
        is PeriodTitle.Day -> ScheduleText.weekdayMonthDay(title.date, locale)
        is PeriodTitle.WeekInMonth -> stringResource(
            R.string.feature_calendar_period_week_in_month,
            ScheduleText.monthDay(title.start, locale),
            ScheduleText.dayOfMonth(title.endInclusive, locale),
            ScheduleText.year(title.endInclusive, locale),
        )
        is PeriodTitle.WeekAcrossMonths -> stringResource(
            R.string.feature_calendar_period_week_across_months,
            ScheduleText.monthDay(title.start, locale),
            ScheduleText.monthDay(title.endInclusive, locale),
        )
    }
}

/**
 * Agenda subtitle time: "10:00 AM – 11:00 AM", "Sat 10:00 PM – 6:00 AM" for an overnight shift
 * seen on its second day, "All day", or "All day · Sun, Nov 1 – Tue, Nov 3".
 */
@Composable
internal fun agendaTimeText(timing: EventTiming, currentYear: Int): String {
    val locale = currentLocale()
    return when (timing) {
        is EventTiming.AllDay -> {
            val allDay = stringResource(R.string.feature_calendar_all_day)
            if (!timing.isMultiDay) {
                allDay
            } else {
                stringResource(
                    R.string.feature_calendar_with_detail,
                    allDay,
                    stringResource(
                        R.string.feature_calendar_time_range,
                        ScheduleText.shortDay(timing.start, currentYear, locale),
                        ScheduleText.shortDay(timing.endInclusive, currentYear, locale),
                    ),
                )
            }
        }
        is EventTiming.Timed -> {
            val start = if (timing.startsBeforeDay) dayAndClock(timing.start, locale) else clock(timing.start, locale)
            val end = timing.end ?: return start
            stringResource(
                R.string.feature_calendar_time_range,
                start,
                if (timing.endsAfterDay) dayAndClock(end, locale) else clock(end, locale),
            )
        }
    }
}

/** Event screen time lines: the start ("Sun, Nov 1, 10:00 PM") and "Until …" or "All day". */
@Composable
internal fun eventTimeLines(timing: EventTiming, currentYear: Int): Pair<String, String?> {
    val locale = currentLocale()
    return when (timing) {
        is EventTiming.AllDay -> {
            val first = ScheduleText.shortDay(timing.start, currentYear, locale)
            val second = if (timing.isMultiDay) {
                stringResource(
                    R.string.feature_calendar_until,
                    ScheduleText.shortDay(timing.endInclusive, currentYear, locale),
                )
            } else {
                stringResource(R.string.feature_calendar_all_day)
            }
            first to second
        }
        is EventTiming.Timed -> {
            val first = longDayAndClock(timing.start, currentYear, locale)
            val end = timing.end
            val second = end?.let {
                val endText = if (it.toLocalDate() == timing.start.toLocalDate()) {
                    clock(it, locale)
                } else {
                    longDayAndClock(it, currentYear, locale)
                }
                stringResource(R.string.feature_calendar_until, endText)
            }
            first to second
        }
    }
}

/** "Approved · half day am" (07 §3); the status is omitted when the API sends none. */
@Composable
internal fun timeOffSubtitle(entry: TimeOffEntry): String {
    val type = ScheduleLabels.requestTypeLabel(entry.requestType)
    val status = ScheduleLabels.sentenceCase(entry.status)
    return if (status == null) {
        ScheduleLabels.sentenceCase(type).orEmpty()
    } else {
        stringResource(R.string.feature_calendar_with_detail, status, type)
    }
}

@Composable
internal fun milestoneSubtitle(occurrence: MilestoneOccurrence): String = when (occurrence.milestone.type) {
    MilestoneType.WORK_ANNIVERSARY -> {
        val years = occurrence.years ?: 0
        pluralStringResource(R.plurals.feature_calendar_milestone_anniversary, years, years)
    }
    else -> stringResource(R.string.feature_calendar_milestone_birthday)
}

@Composable
internal fun zoneNoticeText(notice: ZoneNotice): String {
    val name = ScheduleClock.zoneName(notice.zone, currentLocale())
    return if (notice.isFallback) {
        stringResource(R.string.feature_calendar_zone_notice_fallback, name)
    } else {
        stringResource(R.string.feature_calendar_zone_notice, name)
    }
}

/** Attendee status label, or null when the API sent nothing we know. */
@Composable
internal fun inviteStatusText(status: InviteStatus): String? = when (status) {
    InviteStatus.INVITED -> stringResource(R.string.feature_calendar_status_invited)
    InviteStatus.ACCEPTED -> stringResource(R.string.feature_calendar_status_accepted)
    InviteStatus.DECLINED -> stringResource(R.string.feature_calendar_status_declined)
    InviteStatus.TENTATIVE -> stringResource(R.string.feature_calendar_status_tentative)
    InviteStatus.CANCELLED -> stringResource(R.string.feature_calendar_status_cancelled)
    InviteStatus.UNKNOWN -> null
}

@Composable
internal fun clock(time: ZonedDateTime, locale: Locale = currentLocale()): String = ScheduleClock.clock(time, locale)

/**
 * "Join opens today at 8:30 AM", "tomorrow at …", "Mon at …" within the week, else the short date
 * (UI2-20): a bare clock time reads as today even when the meeting is days away.
 */
@Composable
internal fun joinOpensText(opensAt: ZonedDateTime, daysAway: Long?, currentYear: Int): String {
    val locale = currentLocale()
    val time = ScheduleClock.clock(opensAt, locale)
    val date = opensAt.toLocalDate()
    return when {
        daysAway == null -> stringResource(R.string.feature_calendar_join_opens, time)
        daysAway <= 0L -> stringResource(R.string.feature_calendar_join_opens_today, time)
        daysAway == 1L -> stringResource(R.string.feature_calendar_join_opens_tomorrow, time)
        else -> {
            val day = if (daysAway < DAYS_IN_WEEK) {
                ScheduleText.weekdayShort(date, locale)
            } else {
                ScheduleText.shortDay(date, currentYear, locale)
            }
            stringResource(R.string.feature_calendar_join_opens_on, day, time)
        }
    }
}

private const val DAYS_IN_WEEK = 7L

@Composable
private fun dayAndClock(time: ZonedDateTime, locale: Locale): String = stringResource(
    R.string.feature_calendar_day_time,
    ScheduleText.weekdayShort(time.toLocalDate(), locale),
    ScheduleClock.clock(time, locale),
)

@Composable
private fun longDayAndClock(time: ZonedDateTime, currentYear: Int, locale: Locale): String = stringResource(
    R.string.feature_calendar_day_time_long,
    ScheduleText.shortDay(time.toLocalDate(), currentYear, locale),
    ScheduleClock.clock(time, locale),
)
