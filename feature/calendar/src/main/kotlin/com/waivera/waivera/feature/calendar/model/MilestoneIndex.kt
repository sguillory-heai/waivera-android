package com.waivera.waivera.feature.calendar.model

import androidx.compose.runtime.Immutable
import com.waivera.waivera.core.data.calendar.CalendarMath
import com.waivera.waivera.core.data.model.Milestone
import com.waivera.waivera.core.data.model.MilestoneOccurrence
import com.waivera.waivera.core.network.dto.MilestoneType
import java.time.LocalDate
import java.time.Month
import java.time.Year

/** What an avatar badge celebrates on a date (07 §4 `EmployeeCelebration`). */
enum class CelebrationType { BIRTHDAY, WORK_ANNIVERSARY, MULTIPLE }

/** A person's celebration on one date; [years] is set when an anniversary is part of it. */
@Immutable
data class Celebration(val type: CelebrationType, val years: Int?, val displayName: String?)

/**
 * Milestones indexed by month/day and by user, built once per milestone list, so the 42 cells
 * of a month grid and every avatar look up their milestones without scanning the whole list
 * (07 §4 performance note). Resolution rules (Feb 29 on Feb 28 in common years, anniversaries
 * need a start year and at least one full year) come from [CalendarMath.resolve].
 *
 * Dates passed in are literal dates in the employee's saved zone; the index never reads a clock.
 */
class MilestoneIndex(milestones: List<Milestone>) {
    private val byMonthDay: Map<Int, List<Milestone>> = milestones.groupBy { key(it.month, it.day) }
    private val byUser: Map<String, List<Milestone>> = milestones.groupBy { it.userId }

    val isEmpty: Boolean = milestones.isEmpty()

    /** Milestones that could fall on [date]; the only rows [on] evaluates. */
    internal fun candidates(date: LocalDate): List<Milestone> {
        val direct = byMonthDay[key(date.monthValue, date.dayOfMonth)].orEmpty()
        val leapDayFallback = if (date.month == Month.FEBRUARY &&
            date.dayOfMonth == LEAP_DAY - 1 &&
            !Year.isLeap(date.year.toLong())
        ) {
            byMonthDay[key(Month.FEBRUARY.value, LEAP_DAY)].orEmpty()
        } else {
            emptyList()
        }
        return direct + leapDayFallback
    }

    fun on(date: LocalDate): List<MilestoneOccurrence> = candidates(date).mapNotNull { CalendarMath.resolve(it, date) }
        .sortedWith(compareBy({ it.milestone.type.ordinal }, { it.milestone.displayName.orEmpty() }))

    fun hasAny(date: LocalDate): Boolean = candidates(date).any { CalendarMath.resolve(it, date) != null }

    /** The celebration badge for [userId] on [date], or null when there is nothing to celebrate. */
    fun celebrationFor(userId: String?, date: LocalDate): Celebration? {
        if (userId.isNullOrBlank()) return null
        val resolved = byUser[userId].orEmpty().mapNotNull { CalendarMath.resolve(it, date) }
        return celebrationOf(resolved)
    }

    companion object {
        private const val LEAP_DAY = 29
        private const val MONTH_FACTOR = 100

        val Empty = MilestoneIndex(emptyList())

        private fun key(month: Int, day: Int) = month * MONTH_FACTOR + day

        /** Combines one person's resolved milestones into a single badge (07 §4 table). */
        fun celebrationOf(resolved: List<MilestoneOccurrence>): Celebration? {
            val birthday = resolved.firstOrNull { it.milestone.type == MilestoneType.BIRTHDAY }
            val anniversary = resolved.firstOrNull { it.milestone.type == MilestoneType.WORK_ANNIVERSARY }
            val name = (birthday ?: anniversary)?.milestone?.displayName
            return when {
                birthday != null && anniversary != null ->
                    Celebration(CelebrationType.MULTIPLE, anniversary.years, name)
                birthday != null -> Celebration(CelebrationType.BIRTHDAY, null, name)
                anniversary != null -> Celebration(CelebrationType.WORK_ANNIVERSARY, anniversary.years, name)
                else -> null
            }
        }
    }
}
