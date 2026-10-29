package com.waivera.waivera.core.notifications.presentation

import android.content.Context
import com.waivera.waivera.core.notifications.R

/**
 * The sender as a group chat notification names them. The system draws a collapsed or heads-up
 * group message as "<sender>: <text>" on one line, so a 60-character name filled the line and
 * the text never showed (wave 29 N6). A name up to [MAX_LENGTH] characters is kept as it is;
 * a longer one becomes the first name and the last name's initial ("Maria-Fernanda R."), cut
 * with an ellipsis when even that is too long. Characters are counted and cut by code point, so
 * an emoji is never split. The full name stays in the person's key, which is what the system
 * uses to tell senders apart.
 */
internal object SenderName {
    const val MAX_LENGTH = 24

    /**
     * [withInitial] formats the first name and the initial ("%1$s %2$s."), and [cut] adds the
     * ellipsis to a name cut short ("%1$s…"); both come from string resources.
     */
    fun short(
        fullName: String,
        withInitial: (first: String, initial: String) -> String,
        cut: (String) -> String,
    ): String {
        val name = fullName.trim()
        if (name.codePointCount(0, name.length) <= MAX_LENGTH) return name
        val words = name.split(WHITESPACE)
        val first = words.first()
        val last = words.last()
        val initial = last.substring(0, last.offsetByCodePoints(0, 1))
        val shortened = if (words.size > 1) withInitial(first, initial) else first
        return if (shortened.codePointCount(0, shortened.length) <= MAX_LENGTH) {
            shortened
        } else {
            cut(shortened.substring(0, shortened.offsetByCodePoints(0, MAX_LENGTH - 1)))
        }
    }

    /** [short] with the app's wording. */
    fun short(context: Context, fullName: String): String = short(
        fullName = fullName,
        withInitial = { first, initial -> context.getString(R.string.core_notifications_sender_short, first, initial) },
        cut = { context.getString(R.string.core_notifications_sender_cut, it) },
    )

    private val WHITESPACE = Regex("\\s+")
}
