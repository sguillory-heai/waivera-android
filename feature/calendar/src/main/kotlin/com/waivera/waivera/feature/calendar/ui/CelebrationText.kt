package com.waivera.waivera.feature.calendar.ui

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.res.stringResource
import com.waivera.waivera.core.designsystem.text.CelebrationCopy
import com.waivera.waivera.core.designsystem.text.CelebrationCopySpec
import com.waivera.waivera.feature.calendar.R
import com.waivera.waivera.feature.calendar.model.Celebration
import com.waivera.waivera.feature.calendar.model.CelebrationType

/** A string resource plus its arguments, so label choice is testable without composition. */
@Immutable
// getString takes varargs; the spread copies a short argument list once per label.
@Suppress("SpreadOperator")
data class TextSpec(@StringRes val id: Int, val args: List<Any> = emptyList()) {
    fun resolve(context: Context): String = context.getString(id, *args.toTypedArray())
}

@Suppress("SpreadOperator")
@Composable
fun TextSpec.resolve(): String = stringResource(id, *args.toTypedArray())

/**
 * Celebration copy from 07 §4. [fallbackName] is "Teammate", resolved by the caller, used
 * when the milestone has no display name.
 */
object CelebrationText {
    fun compact(celebration: Celebration): TextSpec = when (celebration.type) {
        CelebrationType.MULTIPLE ->
            TextSpec(R.string.feature_calendar_celebration_compact_multiple, listOf(celebration.yearsOrZero))
        CelebrationType.BIRTHDAY -> TextSpec(R.string.feature_calendar_celebration_compact_birthday)
        CelebrationType.WORK_ANNIVERSARY ->
            TextSpec(R.string.feature_calendar_celebration_compact_anniversary, listOf(celebration.yearsOrZero))
    }

    fun full(celebration: Celebration, fallbackName: String): TextSpec {
        val name = celebration.displayName?.takeIf(String::isNotBlank) ?: fallbackName
        return when (celebration.type) {
            CelebrationType.MULTIPLE -> TextSpec(
                R.string.feature_calendar_celebration_full_multiple,
                listOf(name, celebration.yearsOrZero),
            )
            CelebrationType.BIRTHDAY -> TextSpec(R.string.feature_calendar_celebration_full_birthday, listOf(name))
            CelebrationType.WORK_ANNIVERSARY -> TextSpec(
                R.string.feature_calendar_celebration_full_anniversary,
                listOf(name, celebration.yearsOrZero),
            )
        }
    }

    /**
     * Your own card: to you by first name, in the second person, through the shared
     * [CelebrationCopy] rule that Profile and Today use too (UI9-05).
     */
    fun self(celebration: Celebration): CelebrationCopySpec? = CelebrationCopy.self(
        birthday = celebration.type != CelebrationType.WORK_ANNIVERSARY,
        anniversaryYears = celebration.years.takeIf { celebration.type != CelebrationType.BIRTHDAY },
        firstName = CelebrationCopy.firstName(celebration.displayName),
    )

    /** The emoji for the card's tile; the sentence from [full] carries none. */
    fun emoji(celebration: Celebration): TextSpec = TextSpec(
        when (celebration.type) {
            CelebrationType.MULTIPLE -> R.string.feature_calendar_celebration_emoji_multiple
            CelebrationType.BIRTHDAY -> R.string.feature_calendar_celebration_emoji_birthday
            CelebrationType.WORK_ANNIVERSARY -> R.string.feature_calendar_celebration_emoji_anniversary
        },
    )

    /** The avatar's TalkBack label: the full celebration sentence. */
    fun accessible(celebration: Celebration, fallbackName: String): TextSpec {
        val name = celebration.displayName?.takeIf(String::isNotBlank) ?: fallbackName
        return when (celebration.type) {
            CelebrationType.MULTIPLE -> TextSpec(
                R.string.feature_calendar_celebration_a11y_multiple,
                listOf(name, celebration.yearsOrZero),
            )
            CelebrationType.BIRTHDAY -> TextSpec(R.string.feature_calendar_celebration_a11y_birthday, listOf(name))
            CelebrationType.WORK_ANNIVERSARY -> TextSpec(
                R.string.feature_calendar_celebration_a11y_anniversary,
                listOf(name, celebration.yearsOrZero),
            )
        }
    }

    private val Celebration.yearsOrZero: Int get() = years ?: 0
}
