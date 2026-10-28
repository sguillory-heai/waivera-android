@file:UseSerializers(LocalDateSerializer::class)

package com.waivera.waivera.core.data.model

import com.waivera.waivera.core.network.time.LocalDateSerializer
import java.time.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers

/**
 * How many "needs you" items the person has handled today, tallied on this device. The API has
 * no such count, so the app watches the open items and counts each one that leaves: a chat read,
 * a mention marked read, a message that finally went out, an invitation answered.
 *
 * [day] is a calendar date in the employee's saved zone (never the device's or UTC), so the tally
 * resets at the employee's local midnight, also on 23- and 25-hour DST days.
 *
 * @property open the keys that needed the person when last seen.
 * @property openUndoable the keys in [open] whose handling can be taken back (invitations).
 * @property undoable handled keys whose return means the handling was undone (an invitation
 *   answer the person took back or that failed), not a new item: their return takes one off.
 */
@Serializable
data class HandledTally(
    val userId: String,
    val day: LocalDate,
    val handled: Int,
    val open: Set<String>,
    val openUndoable: Set<String> = emptySet(),
    val undoable: Set<String> = emptySet(),
) {
    /** Everything that needed the person today: handled plus still open. */
    val total: Int get() = handled + open.size
}

/**
 * What needs the person right now. Only build one when every source behind the list has loaded
 * and none failed: a list missing a source would read as everything in it being handled.
 *
 * @property undoable the keys in [open] whose return after leaving would undo their handling
 *   rather than be new (invitations: an answer taken back or failed).
 */
data class HandledObservation(
    val userId: String,
    val day: LocalDate,
    val open: Set<String>,
    val undoable: Set<String> = emptySet(),
)

object HandledToday {
    /**
     * The tally after [observation]. A new day or a different person starts from zero with
     * today's open items (nothing that left before the first look counts). Otherwise each key
     * that left the open set counts once, and an undoable key that comes back takes its count
     * off again. A key that returns for a new reason (a new message in a chat read earlier) is
     * simply open again, and counts again when handled.
     */
    fun advance(previous: HandledTally?, observation: HandledObservation): HandledTally {
        if (previous == null || previous.userId != observation.userId || previous.day != observation.day) {
            return HandledTally(
                userId = observation.userId,
                day = observation.day,
                handled = 0,
                open = observation.open,
                openUndoable = observation.undoable,
            )
        }
        val resolved = previous.open - observation.open
        val returned = (observation.open - previous.open).filterTo(mutableSetOf()) { it in previous.undoable }
        return HandledTally(
            userId = observation.userId,
            day = observation.day,
            handled = (previous.handled + resolved.size - returned.size).coerceAtLeast(0),
            open = observation.open,
            openUndoable = observation.undoable,
            undoable = (previous.undoable - returned) + resolved.intersect(previous.openUndoable),
        )
    }
}
