package com.waivera.waivera.feature.calendar.model

import com.waivera.waivera.core.data.model.Milestone
import com.waivera.waivera.core.network.dto.MilestoneType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MilestoneIndexTest {
    private fun birthday(user: String, month: Int, day: Int, name: String = user) =
        Milestone(MilestoneType.BIRTHDAY, user, name, month = month, day = day)

    private fun anniversary(user: String, month: Int, day: Int, startYear: Int?, name: String = user) =
        Milestone(MilestoneType.WORK_ANNIVERSARY, user, name, month = month, day = day, startYear = startYear)

    @Test
    fun leapDayBirthdayFallsOn28FebruaryInACommonYear() {
        val index = MilestoneIndex(listOf(birthday("u1", 2, 29)))
        assertEquals(1, index.on(LocalDate.of(2027, 2, 28)).size)
        assertTrue(index.on(LocalDate.of(2027, 3, 1)).isEmpty())
        // In a leap year it stays on the 29th and not the 28th.
        assertEquals(1, index.on(LocalDate.of(2028, 2, 29)).size)
        assertTrue(index.on(LocalDate.of(2028, 2, 28)).isEmpty())
    }

    @Test
    fun anniversaryInItsStartYearIsNotShown() {
        val index = MilestoneIndex(listOf(anniversary("u1", 11, 1, startYear = 2026)))
        assertTrue(index.on(LocalDate.of(2026, 11, 1)).isEmpty())
        assertNull(index.celebrationFor("u1", LocalDate.of(2026, 11, 1)))
        assertEquals(1, index.on(LocalDate.of(2027, 11, 1)).single().years)
    }

    @Test
    fun anniversaryWithoutStartYearIsNotShown() {
        val index = MilestoneIndex(listOf(anniversary("u1", 11, 1, startYear = null)))
        assertTrue(index.on(LocalDate.of(2026, 11, 1)).isEmpty())
        assertNull(index.celebrationFor("u1", LocalDate.of(2026, 11, 1)))
    }

    @Test
    fun birthdayAndAnniversaryOnTheSameDayIsAMultipleCelebration() {
        val index = MilestoneIndex(
            listOf(birthday("u1", 11, 1, "Sam Ortiz"), anniversary("u1", 11, 1, startYear = 2023, name = "Sam Ortiz")),
        )
        val celebration = index.celebrationFor("u1", LocalDate.of(2026, 11, 1))
        assertEquals(Celebration(CelebrationType.MULTIPLE, 3, "Sam Ortiz"), celebration)
        assertEquals(4, index.celebrationFor("u1", LocalDate.of(2027, 11, 1))?.years)
        assertNull(index.celebrationFor("u1", LocalDate.of(2026, 11, 2)))
        assertNull(index.celebrationFor(null, LocalDate.of(2026, 11, 1)))
    }

    @Test
    fun singleCelebrationsKeepTheirType() {
        val index = MilestoneIndex(listOf(birthday("b", 3, 8), anniversary("a", 3, 8, startYear = 2016)))
        assertEquals(CelebrationType.BIRTHDAY, index.celebrationFor("b", LocalDate.of(2026, 3, 8))?.type)
        val anniversary = index.celebrationFor("a", LocalDate.of(2026, 3, 8))
        assertEquals(CelebrationType.WORK_ANNIVERSARY, anniversary?.type)
        assertEquals(10, anniversary?.years)
    }

    @Test
    fun monthGridLookupDoesNotScanEveryMilestonePerCell() {
        // 400 employees spread over the year; each cell only evaluates its own month/day bucket.
        val milestones = (0 until 400).map { i -> birthday("u$i", i % 12 + 1, i % 28 + 1) }
        val index = MilestoneIndex(milestones)
        val cells = SchedulePeriods.cellsFor(ScheduleView.MONTH, LocalDate.of(2026, 10, 1))
        val evaluated = cells.sumOf { index.candidates(it).size }
        // A per-cell scan would evaluate 42 × 400 rows; the index touches fewer than one full pass.
        assertTrue(evaluated < milestones.size, "evaluated $evaluated candidates for 42 cells")
        assertEquals(milestones.count { it.month == 10 && it.day == 5 }, index.on(LocalDate.of(2026, 10, 5)).size)
    }

    @Test
    fun milestoneOnTheFallBackDateMatchesTheEmployeesLocalDate() {
        // 04:30Z on 1 Nov is still 31 Oct (23:30 CDT) in Chicago, but already 1 Nov in UTC and Tokyo.
        val chicago = ZoneId.of("America/Chicago")
        val index = MilestoneIndex(listOf(birthday("u1", 11, 1)))
        val lateEvening = Instant.parse("2026-11-01T04:30:00Z").atZone(chicago).toLocalDate()
        assertTrue(index.on(lateEvening).isEmpty())
        val afterMidnight = Instant.parse("2026-11-01T05:30:00Z").atZone(chicago).toLocalDate()
        assertEquals(1, index.on(afterMidnight).size)
        // Still 1 Nov after the repeated hour (07:30Z is 01:30 CST).
        assertEquals(1, index.on(Instant.parse("2026-11-01T07:30:00Z").atZone(chicago).toLocalDate()).size)
    }
}
