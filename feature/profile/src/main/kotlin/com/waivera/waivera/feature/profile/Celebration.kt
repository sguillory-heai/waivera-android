package com.waivera.waivera.feature.profile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.waivera.waivera.core.data.calendar.CalendarMath
import com.waivera.waivera.core.data.model.Milestone
import com.waivera.waivera.core.designsystem.component.MilestoneKind
import com.waivera.waivera.core.designsystem.text.CelebrationCopy
import com.waivera.waivera.core.designsystem.text.resolve
import com.waivera.waivera.core.network.dto.MilestoneType
import java.time.LocalDate

/** What a person is celebrating today (spec 07 §4). Null-returning helpers mean "nothing today". */
@Immutable
internal data class Celebration(val birthday: Boolean, val anniversaryYears: Int?) {
    val milestoneKind: MilestoneKind
        get() = if (birthday) MilestoneKind.Birthday else MilestoneKind.WorkAnniversary

    companion object {
        /** Resolves [userId]'s milestones on [today] (the employee-zone date); null when there is none. */
        fun forUser(userId: String, milestones: List<Milestone>, today: LocalDate): Celebration? =
            index(milestones, today)[userId]

        /** One pass over the list for screens that show many avatars (spec 07 §4 performance note). */
        fun index(milestones: List<Milestone>, today: LocalDate): Map<String, Celebration> =
            CalendarMath.milestonesOn(milestones, today)
                .groupBy { it.milestone.userId }
                .mapNotNull { (userId, occurrences) ->
                    val birthday = occurrences.any { it.milestone.type == MilestoneType.BIRTHDAY }
                    val years = occurrences.firstOrNull { it.milestone.type == MilestoneType.WORK_ANNIVERSARY }?.years
                    if (!birthday && years == null) null else userId to Celebration(birthday, years)
                }
                .toMap()
    }
}

/** The emoji for the celebration note's tile; [fullLabel] carries none, so it shows once. */
@Composable
internal fun Celebration.emoji(): String = stringResource(
    // A birthday that is also an anniversary takes the party emoji, as on Today and the schedule.
    if (birthday && anniversaryYears == null) {
        R.string.feature_profile_celebration_emoji_birthday
    } else {
        R.string.feature_profile_celebration_emoji_anniversary
    },
)

/** The sentence shown beside a large avatar: "Happy birthday, Priya!", with the emoji in its tile. */
@Composable
internal fun Celebration.fullLabel(name: String): String {
    val who = name.ifBlank { stringResource(R.string.feature_profile_celebration_someone) }
    val years = anniversaryYears
    return when {
        birthday && years != null ->
            pluralStringResource(R.plurals.feature_profile_celebration_both, years, who, years)
        birthday -> stringResource(R.string.feature_profile_celebration_birthday, who)
        years != null -> pluralStringResource(R.plurals.feature_profile_celebration_anniversary, years, who, years)
        else -> ""
    }
}

/**
 * Your own celebration on your profile, to you by first name: "Happy birthday, Alex!". The
 * wording is the shared [CelebrationCopy] rule, so Profile, Today and Schedule say it the same way.
 */
@Composable
internal fun Celebration.selfLabel(firstName: String?): String =
    CelebrationCopy.self(birthday = birthday, anniversaryYears = anniversaryYears, firstName = firstName)
        ?.resolve()
        .orEmpty()

/** The short label for list rows: "Birthday today", "5-year anniversary". */
@Composable
internal fun Celebration.compactLabel(): String {
    val years = anniversaryYears
    return when {
        birthday && years != null ->
            pluralStringResource(R.plurals.feature_profile_celebration_both_compact, years, years)
        birthday -> stringResource(R.string.feature_profile_celebration_birthday_compact)
        years != null -> pluralStringResource(R.plurals.feature_profile_celebration_anniversary_compact, years, years)
        else -> ""
    }
}
