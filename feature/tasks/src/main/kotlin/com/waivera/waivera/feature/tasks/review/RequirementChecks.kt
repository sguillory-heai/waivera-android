package com.waivera.waivera.feature.tasks.review

import com.waivera.waivera.core.data.model.FieldType
import com.waivera.waivera.core.data.model.PlanDates
import com.waivera.waivera.core.data.model.StageRequirement
import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * sprout_web `WorkflowRequirementValidators`: required fields for every requirement, and for new
 * plan (CPOC) dates the date rules and the "expected date" warnings a reviewer confirms past.
 * "Today" is the employee's saved-zone day.
 */
internal object RequirementChecks {
    data class Result(val errors: Map<String, RequirementIssue>, val warnings: Map<String, RequirementIssue>)

    fun check(requirement: StageRequirement, values: Map<String, String>, plan: PlanDates?, today: LocalDate): Result {
        val errors = required(requirement, values).toMutableMap()
        val warnings = mutableMapOf<String, RequirementIssue>()
        if (requirement.isPlanDates) planDates(values, plan, today, errors, warnings)
        return Result(errors, if (errors.isEmpty()) warnings else emptyMap())
    }

    /** The fields' starting values: the server's defaults, as the web fills them. */
    fun initial(requirements: List<StageRequirement>): Map<Int, Map<String, String>> = requirements.associate { req ->
        req.actionId to req.fields.associate { field ->
            val value = field.defaultValue.orEmpty()
            // A date default may carry a time; the field holds the date part only.
            field.key to if (field.type == FieldType.Date) value.take(ISO_DATE_LENGTH) else value
        }
    }

    fun date(value: String?): LocalDate? = value?.takeIf { it.length == ISO_DATE_LENGTH }?.let {
        try {
            LocalDate.parse(it)
        } catch (_: DateTimeParseException) {
            null
        }
    }

    private fun required(requirement: StageRequirement, values: Map<String, String>): Map<String, RequirementIssue> =
        requirement.fields
            .filter { it.required && values[it.key].isNullOrBlank() }
            .associate { it.key to RequirementIssue(IssueKind.Required) }

    private fun planDates(
        values: Map<String, String>,
        plan: PlanDates?,
        today: LocalDate,
        errors: MutableMap<String, RequirementIssue>,
        warnings: MutableMap<String, RequirementIssue>,
    ) {
        val startText = values[StageRequirement.PLAN_START].orEmpty()
        val endText = values[StageRequirement.PLAN_END].orEmpty()
        val start = date(startText)
        val end = date(endText)
        dateErrors(startText to start, endText to end, errors)
        if (errors.isEmpty()) dateWarnings(start, end, plan, today, warnings)
    }

    private fun dateErrors(
        start: Pair<String, LocalDate?>,
        end: Pair<String, LocalDate?>,
        errors: MutableMap<String, RequirementIssue>,
    ) {
        if (start.first.isNotBlank() && start.second == null) {
            errors[StageRequirement.PLAN_START] = RequirementIssue(IssueKind.InvalidDate)
        }
        if (end.first.isNotBlank() && end.second == null) {
            errors[StageRequirement.PLAN_END] = RequirementIssue(IssueKind.InvalidDate)
        }
        val startDate = start.second
        val endDate = end.second
        if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
            errors[StageRequirement.PLAN_END] = RequirementIssue(IssueKind.EndBeforeStart)
        }
    }

    private fun dateWarnings(
        start: LocalDate?,
        end: LocalDate?,
        plan: PlanDates?,
        today: LocalDate,
        warnings: MutableMap<String, RequirementIssue>,
    ) {
        val yearAgo = today.minusYears(1)
        if (start?.isBefore(yearAgo) == true) {
            warnings[StageRequirement.PLAN_START] = RequirementIssue(IssueKind.OverAYearAgo)
        }
        if (end?.isBefore(yearAgo) == true) {
            warnings[StageRequirement.PLAN_END] = RequirementIssue(IssueKind.OverAYearAgo)
        }
        val expectedStart = plan?.end?.plusDays(1)
        if (start != null && expectedStart != null && start != expectedStart) {
            warnings[StageRequirement.PLAN_START] = RequirementIssue(IssueKind.ExpectedStart, expectedStart)
        }
        val expectedEnd = start?.plusYears(1)?.minusDays(1)
        if (end != null && expectedEnd != null && end != expectedEnd) {
            warnings[StageRequirement.PLAN_END] = RequirementIssue(IssueKind.ExpectedEnd, expectedEnd)
        }
    }

    private const val ISO_DATE_LENGTH = 10
}
