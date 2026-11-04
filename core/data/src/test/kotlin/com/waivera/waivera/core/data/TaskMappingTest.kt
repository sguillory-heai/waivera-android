package com.waivera.waivera.core.data

import com.waivera.waivera.core.data.live.TaskMapping
import com.waivera.waivera.core.data.model.TaskDelay
import com.waivera.waivera.core.data.model.TaskState
import com.waivera.waivera.core.network.WaiveraJson
import com.waivera.waivera.core.network.dto.ActionSubTypeResponse
import com.waivera.waivera.core.network.dto.TaskActionResponse
import com.waivera.waivera.core.network.dto.TaskListResponse
import com.waivera.waivera.core.network.dto.TaskResponse
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TaskMappingTest {
    @Test
    fun a_task_reads_its_key_status_and_literal_due_date() {
        val dto = WaiveraJson.decodeFromString<TaskListResponse>(
            """
            {"id":12,"friendlyId":1042,"title":" Annual plan review ","organization":{"abbreviation":"HEA"},
             "endDate":"2026-11-01T00:00:00","effectiveEndDate":"2026-11-08T00:00:00",
             "taskStatus":{"name":"IN PROGRESS"},"participant":{"firstName":"Jordan","lastName":"Lee"},
             "somethingNew":true}
            """.trimIndent(),
        )
        val task = TaskMapping.summary(dto)
        assertEquals("HEA-1042", task.key)
        assertEquals("Annual plan review", task.title)
        // The extended date wins, and the date never shifts with a zone (Nov 1 2026 is a DST change).
        assertEquals(LocalDate.of(2026, 11, 8), task.due)
        assertEquals(TaskState.InProgress, task.state)
        assertEquals("Jordan Lee", task.about)
    }

    @Test
    fun an_approved_delay_marks_the_row_and_the_task_without_moving_the_due_date() {
        // The delay runs through Sunday Nov 1 2026, the US fall-back day: still a literal date.
        val row = WaiveraJson.decodeFromString<TaskListResponse>(
            """
            {"id":1,"endDate":"2026-10-25T00:00:00",
             "currentTaskDelay":{"id":9,"reasonName":"Hospitalized","explanation":"Back next week",
              "delayStartDate":"2026-10-20T00:00:00","delayEndDate":"2026-11-01T00:00:00"}}
            """.trimIndent(),
        )
        val summary = TaskMapping.summary(row)
        assertEquals(LocalDate.of(2026, 10, 25), summary.due)
        assertTrue(summary.delayed)
        assertEquals(LocalDate.of(2026, 11, 1), summary.delayedThrough)

        val detail = TaskMapping.detail(
            WaiveraJson.decodeFromString<TaskResponse>(
                """{"id":1,"endDate":"2026-10-25T00:00:00","currentTaskDelay":{"reasonName":" Hospitalized ",
                "explanation":"","delayStartDate":"2026-10-20T00:00:00","delayEndDate":"2026-11-01T00:00:00"}}""",
            ),
        )
        assertEquals(
            TaskDelay("Hospitalized", null, LocalDate.of(2026, 10, 20), LocalDate.of(2026, 11, 1)),
            detail.delay,
        )
        assertEquals(LocalDate.of(2026, 10, 25), detail.due)
    }

    @Test
    fun a_task_without_a_delay_has_none() {
        val summary = TaskMapping.summary(WaiveraJson.decodeFromString<TaskListResponse>("""{"id":1}"""))
        assertFalse(summary.delayed)
        assertNull(summary.delayedThrough)
        assertNull(TaskMapping.detail(WaiveraJson.decodeFromString<TaskResponse>("""{"id":1}""")).delay)
    }

    @Test
    fun unknown_statuses_are_kept_not_dropped() {
        val dto = WaiveraJson.decodeFromString<TaskListResponse>("""{"id":1,"taskStatus":{"name":"ON HOLD"}}""")
        assertEquals(TaskState.Other("ON HOLD"), TaskMapping.summary(dto).state)
    }

    @Test
    fun activity_hides_replies_and_deleted_rows_and_reads_utc_times_newest_first() {
        val rows = TaskMapping.activity(
            listOf(
                TaskActionResponse(id = 1, completionTime = "2026-11-01T06:30:00", durationInMinutes = 20),
                TaskActionResponse(id = 2, completionTime = "2026-11-02T15:00:00Z"),
                TaskActionResponse(id = 3, deleted = true),
                TaskActionResponse(id = 4, parentTaskActionId = 1),
            ),
        )
        assertEquals(listOf(2, 1), rows.map { it.id })
        assertEquals(Instant.parse("2026-11-01T06:30:00Z"), rows.last().at)
    }

    @Test
    fun only_loggable_types_without_files_are_offered() {
        val types = TaskMapping.activityTypes(
            listOf(
                ActionSubTypeResponse(id = 1, actionSubTypeName = "PHONE CALL", isUserLoggable = true),
                ActionSubTypeResponse(id = 2, actionSubTypeName = "Upload", isUserLoggable = true, requiresFile = 1),
                ActionSubTypeResponse(id = 3, actionSubTypeName = "System", isUserLoggable = false),
            ),
        )
        assertEquals(listOf("Phone call"), types.map { it.name })
    }
}
