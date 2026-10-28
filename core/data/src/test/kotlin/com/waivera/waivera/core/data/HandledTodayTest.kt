package com.waivera.waivera.core.data

import com.waivera.waivera.core.data.model.HandledObservation
import com.waivera.waivera.core.data.model.HandledTally
import com.waivera.waivera.core.data.model.HandledToday
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.assertEquals
import org.junit.Test

/** The on-device handled-today rules: what counts, what undoes a count and when the day turns over. */
class HandledTodayTest {
    private val day = LocalDate.of(2026, 10, 28)

    private fun seen(vararg open: String, on: LocalDate = day, user: String = ME) = HandledObservation(
        userId = user,
        day = on,
        open = open.toSet(),
        undoable = open.filterTo(mutableSetOf()) { it.startsWith("invite-") },
    )

    private fun run(vararg steps: HandledObservation): HandledTally =
        steps.fold<HandledObservation, HandledTally?>(null) { tally, step -> HandledToday.advance(tally, step) }
            .let(::checkNotNull)

    @Test
    fun theFirstLookOfTheDayStartsAtZero() {
        val tally = run(seen("dm-1", "mention-2"))
        assertEquals(0, tally.handled)
        assertEquals(2, tally.total)
    }

    @Test
    fun eachItemThatLeavesCountsOnce() {
        val tally = run(seen("dm-1", "mention-2", "invite-3"), seen("mention-2", "invite-3"), seen("invite-3"), seen())
        assertEquals(3, tally.handled)
        assertEquals(3, tally.total)
        assertEquals(emptySet(), tally.open)
    }

    @Test
    fun anItemSeenAgainWithoutLeavingDoesNotCount() {
        val tally = run(seen("dm-1"), seen("dm-1"), seen("dm-1"))
        assertEquals(0, tally.handled)
    }

    @Test
    fun anUndoneInvitationAnswerTakesItsCountBack() {
        val answered = run(seen("invite-3", "dm-1"), seen("dm-1"))
        assertEquals(1, answered.handled)
        val undone = HandledToday.advance(answered, seen("invite-3", "dm-1"))
        assertEquals(0, undone.handled)
        assertEquals(2, undone.total)
        val answeredAgain = HandledToday.advance(undone, seen("dm-1"))
        assertEquals(1, answeredAgain.handled, "answering again counts again, once")
    }

    @Test
    fun aChatWithANewMessageIsOpenAgainAndCountsAgain() {
        // Read at 9, a new message at 10, read again at 11: two things handled.
        val tally = run(seen("dm-1"), seen(), seen("dm-1"), seen())
        assertEquals(2, tally.handled)
    }

    @Test
    fun aNewItemAddsToTheTotalNotTheHandledCount() {
        val tally = run(seen("dm-1"), seen("dm-1", "dm-2"))
        assertEquals(0, tally.handled)
        assertEquals(2, tally.total)
    }

    @Test
    fun anotherPersonStartsTheirOwnTally() {
        val mine = run(seen("dm-1"), seen())
        val theirs = HandledToday.advance(mine, seen("dm-9", user = "someone-else"))
        assertEquals(0, theirs.handled)
        assertEquals("someone-else", theirs.userId)
    }

    @Test
    fun theCountNeverGoesBelowZero() {
        val tally = HandledTally(userId = ME, day = day, handled = 0, open = emptySet(), undoable = setOf("invite-3"))
        assertEquals(0, HandledToday.advance(tally, seen("invite-3")).handled)
    }

    /**
     * The day is the employee's, in their saved zone. On November 1, 2026, Chicago falls back
     * and the day runs 25 hours (05:00Z to 06:00Z the next day): the tally holds through the
     * repeated hour and the evening (already November 2 in UTC), and resets at local midnight.
     */
    @Test
    fun time_theTallyKeepsTheEmployeesDayAcrossTheFallBackAndResetsAtLocalMidnight() {
        val chicago = ZoneId.of("America/Chicago")
        fun dayAt(instant: String) = Instant.parse(instant).atZone(chicago).toLocalDate()
        val firstRepeatedOneAm = dayAt("2026-11-01T06:30:00Z")
        val secondRepeatedOneAm = dayAt("2026-11-01T07:30:00Z")
        val evening = dayAt("2026-11-02T05:30:00Z")
        val nextMidnight = dayAt("2026-11-02T06:00:00Z")
        assertEquals(LocalDate.of(2026, 11, 1), firstRepeatedOneAm)
        assertEquals(LocalDate.of(2026, 11, 1), secondRepeatedOneAm)
        assertEquals(LocalDate.of(2026, 11, 1), evening, "23:30 CST is still November 1 though UTC says the 2nd")
        assertEquals(LocalDate.of(2026, 11, 2), nextMidnight)

        val held = run(
            seen("dm-1", "dm-2", "dm-3", on = firstRepeatedOneAm),
            seen("dm-2", "dm-3", on = secondRepeatedOneAm),
            seen("dm-3", on = evening),
        )
        assertEquals(2, held.handled, "one 25-hour day, one tally")
        val next = HandledToday.advance(held, seen("dm-3", on = nextMidnight))
        assertEquals(0, next.handled)
        assertEquals(LocalDate.of(2026, 11, 2), next.day)
    }

    /** March 8, 2026 is Chicago's 23-hour day: 23:30 CDT is still the 8th, though UTC says the 9th. */
    @Test
    fun time_theShortSpringDayIsOneTallyToo() {
        val chicago = ZoneId.of("America/Chicago")
        val morning = Instant.parse("2026-03-08T07:30:00Z").atZone(chicago).toLocalDate()
        val lateEvening = Instant.parse("2026-03-09T04:30:00Z").atZone(chicago).toLocalDate()
        assertEquals(morning, lateEvening)
        val tally = run(seen("dm-1", on = morning), seen(on = lateEvening))
        assertEquals(1, tally.handled)
    }

    private companion object {
        const val ME = "me"
    }
}
