package com.waivera.waivera.feature.profile

import com.waivera.waivera.core.data.model.Milestone
import com.waivera.waivera.core.data.model.TimeOffBalance
import com.waivera.waivera.core.data.model.TimeOffRequest
import com.waivera.waivera.core.data.model.TimeOffSummary
import com.waivera.waivera.core.designsystem.component.ConnectionBannerState
import com.waivera.waivera.core.network.dto.MilestoneType
import com.waivera.waivera.core.network.time.WaiveraTime
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import java.util.TimeZone
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProfileFormatTest {
    private val original: TimeZone = TimeZone.getDefault()

    @AfterTest
    fun restoreZone() = TimeZone.setDefault(original)

    @Test
    fun birthdayHasNoYearAndNeverShiftsOnAnyDeviceZone() {
        val birthday = requireNotNull(WaiveraTime.parseLocalDate("1990-04-02T00:00:00"))
        listOf("Pacific/Honolulu", "America/Los_Angeles", "UTC", "Asia/Tokyo", "Pacific/Kiritimati").forEach { zone ->
            TimeZone.setDefault(TimeZone.getTimeZone(zone))
            assertEquals("Apr 2", ProfileFormat.birthday(birthday, Locale.US), "device zone $zone")
        }
    }

    @Test
    fun hireDateRendersAsTheCalendarDate() {
        assertEquals("Mar 14, 2022", ProfileFormat.date(LocalDate.parse("2022-03-14"), Locale.US))
    }

    @Test
    fun hireDateOnTheSpringForwardDayIsUnchangedAcrossTheDstBoundary() {
        // 2026-03-08 is the US spring-forward day; a midnight-UTC parse would show Mar 7 in Chicago.
        val hire = requireNotNull(WaiveraTime.parseLocalDate("2026-03-08T00:00:00"))
        listOf("America/Chicago", "America/Los_Angeles", "Europe/London").forEach { zone ->
            TimeZone.setDefault(TimeZone.getTimeZone(zone))
            assertEquals("Mar 8, 2026", ProfileFormat.date(hire, Locale.US))
        }
    }

    @Test
    fun instantsAreDatedInTheEmployeesZoneAcrossTheFallBackBoundary() {
        val chicago = ZoneId.of("America/Chicago")

        // 1 November 2026: 01:30 CDT (before the change) and 01:30 CST (after) are both Nov 1.
        fun dated(utc: String) = ProfileFormat.instantDate(Instant.parse(utc), chicago, Locale.US)
        assertEquals("Nov 1, 2026", dated("2026-11-01T06:30:00Z"))
        assertEquals("Nov 1, 2026", dated("2026-11-01T07:30:00Z"))
        // 04:30 UTC is still 31 October in Chicago, though it is 1 November on a UTC device.
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        assertEquals("Oct 31, 2026", dated("2026-11-01T04:30:00Z"))
    }

    @Test
    fun dateRangeCollapsesSingleDays() {
        val day = LocalDate.parse("2026-11-03")
        assertEquals("Nov 3, 2026", ProfileFormat.dateRange(day, day, Locale.US))
        assertEquals("Nov 3, 2026 – Nov 5, 2026", ProfileFormat.dateRange(day, day.plusDays(2), Locale.US))
        assertNull(ProfileFormat.dateRange(null, null, Locale.US))
    }
}

class RoleLabelTest {
    @Test
    fun systemRoleNamesShowInTitleCase() {
        assertEquals("Support Coordinator", ProfileFormat.roleLabel("SUPPORT COORDINATOR", Locale.US))
        assertEquals("Care Team Lead", ProfileFormat.roleLabel("CARE_TEAM_LEAD", Locale.US))
    }

    @Test
    fun displayTextIsLeftAlone() {
        assertEquals("Senior support coordinator", ProfileFormat.roleLabel("Senior support coordinator", Locale.US))
    }
}

/** One connection signal per screen (UI-04). */
class ConnectionSignalTest {
    @Test
    fun loadErrorSilencesTheConnectionBanner() {
        assertEquals(
            ConnectionBannerState.Connected,
            bannerFor(ConnectionBannerState.Reconnecting, showsLoadError = true),
        )
        assertEquals(ConnectionBannerState.Offline, bannerFor(ConnectionBannerState.Offline, showsLoadError = false))
    }

    @Test
    fun refreshFailureShowsOnlyWhileConnected() {
        assertTrue(showsRefreshFailed(ConnectionBannerState.Connected, refreshFailed = true))
        assertFalse(showsRefreshFailed(ConnectionBannerState.Offline, refreshFailed = true))
        assertFalse(showsRefreshFailed(ConnectionBannerState.Reconnecting, refreshFailed = true))
        assertFalse(showsRefreshFailed(ConnectionBannerState.Connected, refreshFailed = false))
    }
}

class TimeOffMathTest {
    @Test
    fun fortyPercentUsedFillsSixtyPercent() {
        assertEquals(0.6f, TimeOffMath.remainingFraction(40.0), 0.0001f)
    }

    @Test
    fun outOfRangePercentagesAreClamped() {
        assertEquals(0f, TimeOffMath.remainingFraction(140.0))
        assertEquals(1f, TimeOffMath.remainingFraction(-10.0))
    }

    @Test
    fun usedShareComesFromUsedAndAllocatedDays() {
        val balance = TimeOffBalance(remainingDays = 6.0, usedDays = 4.0, allocated = 10.0)
        assertEquals(40.0, TimeOffMath.usedPercentage(balance))
        assertEquals(40.0, TimeOffMath.usedPercentage(TimeOffBalance(remainingDays = 6.0, allocated = 10.0)))
        assertNull(TimeOffMath.usedPercentage(TimeOffBalance(usedDays = 4.0)))
    }

    @Test
    fun theServersUsedPercentageWinsOverTheDerivedOne() {
        val balance = TimeOffBalance(remainingDays = 6.0, usedDays = 4.0, allocated = 10.0, usedPercentage = 55.0)
        assertEquals(55.0, TimeOffMath.usedPercentage(balance))
        assertEquals(25.0, TimeOffMath.usedPercentage(TimeOffBalance(usedPercentage = 25.0)))
    }

    @Test
    fun fiveEntriesShowThreeAndSayTwoMore() {
        val summary = TimeOffSummary(requests = (1L..5L).map { TimeOffRequest(it) })
        assertEquals(2, TimeOffMath.hiddenCount(summary))
        assertEquals(3, summary.requests.take(TimeOffMath.VISIBLE_REQUESTS).size)
    }

    @Test
    fun emptyResponseHasNoCard() {
        assertFalse(TimeOffMath.hasContent(TimeOffSummary()))
        assertTrue(TimeOffMath.hasContent(TimeOffSummary(balance = TimeOffBalance(remainingDays = 1.0))))
    }

    @Test
    fun cancellationPendingWinsOverApproved() {
        val request = TimeOffRequest(1, isApproved = true, isCancellationPending = true)
        assertEquals(TimeOffStatus.CancellationPending, TimeOffStatus.of(request))
        assertEquals(TimeOffStatus.Rejected, TimeOffStatus.of(TimeOffRequest(2, isRejected = true, isApproved = true)))
        assertEquals(TimeOffStatus.Approved, TimeOffStatus.of(TimeOffRequest(3, isApproved = true)))
        assertEquals(TimeOffStatus.Pending, TimeOffStatus.of(TimeOffRequest(4)))
    }
}

class CelebrationTest {
    private val today = LocalDate.parse("2026-11-01")

    @Test
    fun birthdayToday() {
        val list = listOf(Milestone(MilestoneType.BIRTHDAY, "u1", month = 11, day = 1))
        assertEquals(Celebration(birthday = true, anniversaryYears = null), Celebration.forUser("u1", list, today))
    }

    @Test
    fun anniversaryCarriesYearsAndHireDayItselfIsNotOne() {
        val list = listOf(
            Milestone(MilestoneType.WORK_ANNIVERSARY, "u1", month = 11, day = 1, startYear = 2021),
            Milestone(MilestoneType.WORK_ANNIVERSARY, "u2", month = 11, day = 1, startYear = 2026),
        )
        assertEquals(Celebration(birthday = false, anniversaryYears = 5), Celebration.forUser("u1", list, today))
        assertNull(Celebration.forUser("u2", list, today))
    }

    @Test
    fun birthdayAndAnniversaryTogether() {
        val list = listOf(
            Milestone(MilestoneType.BIRTHDAY, "u1", month = 11, day = 1),
            Milestone(MilestoneType.WORK_ANNIVERSARY, "u1", month = 11, day = 1, startYear = 2016),
        )
        assertEquals(Celebration(birthday = true, anniversaryYears = 10), Celebration.forUser("u1", list, today))
    }

    @Test
    fun leapDayBirthdayFallsOnFebruary28InCommonYears() {
        val list = listOf(Milestone(MilestoneType.BIRTHDAY, "u1", month = 2, day = 29))
        assertEquals(true, Celebration.forUser("u1", list, LocalDate.parse("2027-02-28"))?.birthday)
        assertNull(Celebration.forUser("u1", list, LocalDate.parse("2028-02-28")))
    }

    @Test
    fun otherDaysHaveNothing() {
        val list = listOf(Milestone(MilestoneType.BIRTHDAY, "u1", month = 11, day = 2))
        assertNull(Celebration.forUser("u1", list, today))
    }
}
