package com.waivera.waivera.core.designsystem.text

import android.content.res.Resources
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.waivera.waivera.core.designsystem.R

/**
 * A celebration sentence as a resource and its arguments, so the choice is testable without
 * composition and resolves the same way in a composable and from [Resources].
 */
@Immutable
sealed interface CelebrationCopySpec {
    data class Text(@param:StringRes val id: Int, val args: List<Any> = emptyList()) : CelebrationCopySpec

    data class Plural(@param:PluralsRes val id: Int, val quantity: Int, val args: List<Any> = emptyList()) :
        CelebrationCopySpec

    // getString takes varargs; the spread copies a short argument list once per sentence.
    @Suppress("SpreadOperator")
    fun resolve(resources: Resources): String = when (this) {
        is Text -> resources.getString(id, *args.toTypedArray())
        is Plural -> resources.getQuantityString(id, quantity, *args.toTypedArray())
    }
}

/** [CelebrationCopySpec.resolve] in composition. */
@Suppress("SpreadOperator")
@Composable
fun CelebrationCopySpec.resolve(): String = when (this) {
    is CelebrationCopySpec.Text -> stringResource(id, *args.toTypedArray())
    is CelebrationCopySpec.Plural -> pluralStringResource(id, quantity, *args.toTypedArray())
}

/**
 * The one rule for your own celebration, wherever it shows (Profile, Today, Schedule): to you,
 * by first name, in the second person ("Happy birthday, Alex!"), never the third-person,
 * full-name sentence a teammate sees, which read like a system message (UI8-09, UI9-05). With no
 * usable name it simply says "Happy birthday!". No emoji: the card shows it once, in its tile.
 */
object CelebrationCopy {
    /** Your own celebration. [anniversaryYears] is set for a work anniversary of a year or more. */
    fun self(birthday: Boolean, anniversaryYears: Int?, firstName: String?): CelebrationCopySpec? {
        val name = firstName?.trim()?.takeIf(String::isNotEmpty)
        val years = anniversaryYears
        return when {
            birthday && years != null ->
                name
                    ?.let { CelebrationCopySpec.Text(R.string.core_designsystem_celebration_self_both, listOf(it)) }
                    ?: CelebrationCopySpec.Text(R.string.core_designsystem_celebration_self_unnamed_both)
            birthday ->
                name
                    ?.let { CelebrationCopySpec.Text(R.string.core_designsystem_celebration_self_birthday, listOf(it)) }
                    ?: CelebrationCopySpec.Text(R.string.core_designsystem_celebration_self_unnamed_birthday)
            years != null ->
                name
                    ?.let {
                        CelebrationCopySpec.Plural(
                            R.plurals.core_designsystem_celebration_self_anniversary,
                            years,
                            listOf(it, years),
                        )
                    }
                    ?: CelebrationCopySpec.Plural(
                        R.plurals.core_designsystem_celebration_self_unnamed_anniversary,
                        years,
                        listOf(years),
                    )
            else -> null
        }
    }

    /** The first word of a display name ("Alex" from "Alex Rivera"), or null when there is none. */
    fun firstName(displayName: String?): String? =
        displayName?.trim()?.split(WHITESPACE)?.firstOrNull()?.takeIf(String::isNotEmpty)

    /**
     * Which form of [displayName] a celebration [sentence] names, the full name (a teammate's
     * card) or the first name (your own), so the name text can hold just that part to its lines.
     * Empty when the sentence names neither.
     */
    fun nameIn(sentence: String, displayName: String?): String =
        listOfNotNull(displayName?.trim(), firstName(displayName))
            .firstOrNull { it.isNotEmpty() && it in sentence }
            .orEmpty()

    private val WHITESPACE = Regex("\\s+")
}
