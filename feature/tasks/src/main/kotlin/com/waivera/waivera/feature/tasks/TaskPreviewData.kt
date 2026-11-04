@file:Suppress("MagicNumber")

package com.waivera.waivera.feature.tasks

import com.waivera.waivera.core.data.model.ActivityType
import com.waivera.waivera.core.data.model.ChangeReason
import com.waivera.waivera.core.data.model.ChangeRequest
import com.waivera.waivera.core.data.model.DelayPeriod
import com.waivera.waivera.core.data.model.RequestKind
import com.waivera.waivera.core.data.model.RequestStatus
import com.waivera.waivera.core.data.model.TaskDetail
import com.waivera.waivera.core.data.model.TaskFile
import com.waivera.waivera.core.data.model.TaskState
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Fixed content for previews and screenshot tests. */
internal object TaskPreviewData {
    private val Zone: ZoneId = ZoneId.of("America/Chicago")
    private val Today: LocalDate = LocalDate.of(2026, 11, 4)
    private val Now: Instant = Today.atTime(14, 30).atZone(Zone).toInstant()

    fun task(state: TaskState = TaskState.NotStarted) = TaskDetail(
        id = 1042,
        key = "HEA-1042",
        title = "Annual plan review",
        description = "Review the annual plan with the family and update goals.",
        state = state,
        type = "Plan review",
        start = Today.minusDays(7),
        due = Today.plusDays(2),
        assignedBy = "Priya Raman",
        about = "Jordan Lee",
        webPath = "/task/HEA-1042",
    )

    fun detailState(state: TaskState = TaskState.NotStarted): TaskDetailUiState {
        val task = task(state)
        return TaskDetailUiState(
            phase = DetailPhase.Content,
            task = task,
            due = TaskFormat.due(task.due, Today),
            dueDate = task.due?.let { TaskFormat.date(it, Today) },
            activity = Loadable.Ready(
                listOf(
                    ActivityRow(
                        1,
                        "Phone call",
                        20,
                        TaskFormat.moment(Now.minusSeconds(DAY * 2), Zone, Today),
                        true,
                        "You",
                        null,
                    ),
                    ActivityRow(
                        2,
                        "Documentation",
                        30,
                        TaskFormat.moment(Now.minusSeconds(DAY * 7), Zone, Today),
                        false,
                        "Priya Raman",
                        "Drafted the goals section.",
                    ),
                ),
            ),
        )
    }

    fun logForm() = LogForm(
        types = Loadable.Ready(
            listOf(ActivityType(1, "Home visit", null, null), ActivityType(2, "Phone call", 15, null)),
        ),
        typeId = 1,
        minutes = 75,
        completedAt = Now,
        zone = Zone,
        whenText = TaskFormat.moment(Now, Zone, Today),
    )

    private fun request(
        id: Int,
        kind: RequestKind,
        status: RequestStatus = RequestStatus.Pending,
        who: String = "Priya Raman",
    ) = ChangeRequest(
        id = id,
        kind = kind,
        typeName = kind.name,
        status = status,
        requesterId = "usr_$id",
        requesterName = who,
        approverName = null,
        requestedAt = Now.minusSeconds(DAY / 6 * id),
        decidedAt = null,
        taskId = 1040 + id,
        taskTitle = "Annual plan review",
        reason = "Family rescheduled",
        explanation = "The family asked to move the visit after the holiday.",
        currentDue = Today.plusDays(1),
        requestedDue = Today.plusDays(15),
        delayStart = Today.minusDays(3),
        delayEnd = Today.plusDays(4),
    )

    private fun item(request: ChangeRequest) = RequestItem(
        request = request,
        asked = request.requestedAt?.let { TaskFormat.moment(it, Zone, Today) },
        change = when (request.kind) {
            RequestKind.MoreTime -> RequestChange.DueMove("Thu, Nov 5", "Thu, Nov 19", 14)
            RequestKind.Delay -> RequestChange.DelayWindow("Sun, Nov 1", "Sun, Nov 8")
            RequestKind.Closure -> RequestChange.Closure
            RequestKind.Other -> RequestChange.Other
        },
        due = TaskFormat.due(request.currentDue, Today),
    )

    fun waitingRequest() = item(request(1, RequestKind.MoreTime))

    fun requestsState() = RequestsUiState(
        waiting = Loadable.Ready(
            listOf(waitingRequest(), item(request(2, RequestKind.Delay, who = "Marcus Hill"))),
        ),
        mine = Loadable.Ready(
            listOf(
                item(request(3, RequestKind.Closure, RequestStatus.Approved, who = "You")),
                item(request(4, RequestKind.MoreTime, RequestStatus.Rejected, who = "You")),
            ),
        ),
    )

    private val reasons = listOf(
        ChangeReason(1, "Family rescheduled", null, requiresEvidence = false),
        ChangeReason(2, "Medical", "A doctor's note is needed.", requiresEvidence = true),
    )

    fun askForm(kind: AskKind?) = AskForm(
        choices = AskKind.entries,
        kind = kind,
        reasons = Loadable.Ready(reasons),
        reasonId = if (kind == null) null else 2,
        newDue = Today.plusDays(14),
        explanation = "The family asked to move the visit after the holiday.",
        files = Loadable.Ready(listOf(TaskFile(7, "doctor-note.pdf"))),
        evidenceId = 7,
        currentDue = Today.plusDays(2),
        delay = DelayPeriod(Today.minusDays(3), Today.plusDays(4), renewal = false),
    )

    val today: LocalDate get() = Today

    private const val DAY = 86_400L
}
