package com.waivera.waivera.feature.today.notices

import com.waivera.waivera.core.data.model.NoticeKind
import com.waivera.waivera.core.network.NetworkConfig
import com.waivera.waivera.feature.today.TEST_LINKS
import com.waivera.waivera.feature.today.notice
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.assertEquals
import org.junit.Test

class NoticeRulesTest {
    private val zone = ZoneId.of("America/Chicago")

    @Test
    fun groups_followTheEmployeesZoneAcrossTheDstEnd() {
        // 1 Nov 2026 is the day DST ends in Chicago. 04:30 UTC is still 31 Oct at 23:30 CDT.
        val now = Instant.parse("2026-11-01T16:30:00Z")
        val groups = NoticeRules.groups(
            listOf(
                notice("today", at = Instant.parse("2026-11-01T06:30:00Z")),
                notice("yesterday", at = Instant.parse("2026-11-01T04:30:00Z")),
                notice("week", at = Instant.parse("2026-10-28T12:00:00Z")),
                notice("old", at = Instant.parse("2026-10-01T12:00:00Z")),
            ),
            NoticeFilter.All,
            TEST_LINKS,
            now,
            zone,
        )
        assertEquals(
            listOf(NoticeDay.Today, NoticeDay.Yesterday, NoticeDay.ThisWeek, NoticeDay.Earlier),
            groups.map { it.day },
        )
        assertEquals(listOf("today", "yesterday", "week", "old"), groups.map { it.rows.single().id })
    }

    @Test
    fun day_buckets() {
        val today = LocalDate.of(2026, 11, 3)
        assertEquals(NoticeDay.Today, NoticeRules.day(today, today))
        assertEquals(NoticeDay.Yesterday, NoticeRules.day(today.minusDays(1), today))
        assertEquals(NoticeDay.ThisWeek, NoticeRules.day(today.minusDays(6), today))
        assertEquals(NoticeDay.Earlier, NoticeRules.day(today.minusDays(7), today))
        assertEquals(NoticeDay.Earlier, NoticeRules.day(null, today))
    }

    @Test
    fun filters() {
        assertEquals(true, NoticeRules.matches(NoticeKind.TaskActivity, NoticeFilter.Tasks))
        assertEquals(true, NoticeRules.matches(NoticeKind.Signature, NoticeFilter.Approvals))
        assertEquals(false, NoticeRules.matches(NoticeKind.Survey, NoticeFilter.Meetings))
    }

    @Test
    fun links_preferTheEventThenTheWebPathThenTheTaskPage() {
        assertEquals(
            NoticeDestination.Event("evt_1"),
            TEST_LINKS.destination(notice("a", eventId = "evt_1").copy(webPath = "/calendar?eventId=evt_1")),
        )
        assertEquals(
            NoticeDestination.Web("https://app.example.test/survey/launch?x=1"),
            TEST_LINKS.destination(notice("b").copy(webPath = "/survey/launch?x=1")),
        )
        assertEquals(NoticeDestination.Task(42), TEST_LINKS.destination(notice("c")))
        assertEquals(NoticeDestination.Task(42), TEST_LINKS.destination(notice("d").copy(webPath = "/task/42")))
        assertEquals(
            NoticeDestination.Web("https://app.example.test/task/42/files"),
            TEST_LINKS.destination(notice("e").copy(webPath = "/task/42/files")),
        )
    }

    @Test
    fun links_openRequestsInTheApp() {
        assertEquals(NoticeDestination.Requests, TEST_LINKS.destination(notice("r").copy(opensRequests = true)))
    }

    @Test
    fun links_neverLeaveTheWebApp() {
        assertEquals(NoticeDestination.None, TEST_LINKS.destination(notice("a").copy(webPath = "//evil.example/x")))
        val unconfigured = NoticeLinks(NetworkConfig(apiBaseUrl = "https://api.example.test", webBaseUrl = ""))
        assertEquals(NoticeDestination.None, unconfigured.destination(notice("b").copy(webPath = "/survey/launch")))
    }
}
