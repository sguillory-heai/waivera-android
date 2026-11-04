package com.waivera.waivera.feature.tasks.review

import com.waivera.waivera.core.data.model.FieldType
import com.waivera.waivera.core.data.model.PlanDates
import com.waivera.waivera.core.data.model.RequirementField
import com.waivera.waivera.core.data.model.StageRequirement
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RequirementChecksTest {
    private val planDates = StageRequirement(
        actionId = 5,
        key = "profile",
        actionType = StageRequirement.PLAN_DATES,
        title = "Update plan dates",
        description = null,
        fields = listOf(
            RequirementField(
                StageRequirement.PLAN_START,
                "Start",
                FieldType.Date,
                required = true,
                defaultValue = "2027-01-01T00:00:00",
            ),
            RequirementField(StageRequirement.PLAN_END, "End", FieldType.Date, required = false, defaultValue = null),
        ),
    )
    private val today = LocalDate.of(2026, 11, 4)

    @Test
    fun date_defaults_keep_only_the_day() {
        assertEquals(
            mapOf(5 to mapOf("cpocDate" to "2027-01-01", "cpocEndDate" to "")),
            RequirementChecks.initial(listOf(planDates)),
        )
    }

    @Test
    fun a_date_that_is_not_a_day_is_an_error() {
        val result = RequirementChecks.check(planDates, mapOf("cpocDate" to "2026-02-30"), null, today)
        assertEquals(IssueKind.InvalidDate, result.errors["cpocDate"]?.kind)
        assertTrue(result.warnings.isEmpty())
        assertNull(RequirementChecks.date("2026-1-5"))
    }

    @Test
    fun a_start_over_a_year_ago_warns() {
        val result = RequirementChecks.check(planDates, mapOf("cpocDate" to "2025-11-03"), null, today)
        assertEquals(IssueKind.OverAYearAgo, result.warnings["cpocDate"]?.kind)
        assertTrue(
            RequirementChecks.check(planDates, mapOf("cpocDate" to "2025-11-04"), null, today).warnings.isEmpty(),
        )
    }

    @Test
    fun a_plan_through_the_dst_change_expects_a_year_less_a_day() {
        // Starting the day after the last plan ended, on the spring-forward day, ending a year less a day later.
        val result = RequirementChecks.check(
            planDates,
            mapOf("cpocDate" to "2026-03-08", "cpocEndDate" to "2027-03-07"),
            PlanDates(LocalDate.of(2025, 3, 8), LocalDate.of(2026, 3, 7)),
            today,
        )
        assertTrue(result.errors.isEmpty())
        assertTrue(result.warnings.isEmpty())
    }
}
