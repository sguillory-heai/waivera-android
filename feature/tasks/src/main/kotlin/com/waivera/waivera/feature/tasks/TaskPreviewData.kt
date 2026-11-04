@file:Suppress("MagicNumber")

package com.waivera.waivera.feature.tasks

import com.waivera.waivera.core.data.model.ActivityType
import com.waivera.waivera.core.data.model.ChangeReason
import com.waivera.waivera.core.data.model.ChangeRequest
import com.waivera.waivera.core.data.model.DelayPeriod
import com.waivera.waivera.core.data.model.FileKind
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

    /** Oldest first: one from last year, yours and a coworker's, and a long note that is cut. */
    val activity: List<ActivityRow> = listOf(
        ActivityRow(
            id = 1,
            type = "Plan review",
            moment = TaskFormat.moment(Now.minusSeconds(DAY * 330), Zone, Today),
            byMe = false,
            author = "Priya Raman",
            description = "Reviewed last year's plan with the team.",
        ),
        ActivityRow(
            id = 2,
            type = "Home visit",
            moment = TaskFormat.moment(Now.minusSeconds(DAY * 9), Zone, Today),
            byMe = true,
            author = "Sam Carter",
            description = null,
        ),
        ActivityRow(
            id = 3,
            type = "Documentation",
            moment = TaskFormat.moment(Now.minusSeconds(DAY * 7), Zone, Today),
            byMe = false,
            author = "Priya Raman",
            description = "Drafted the goals section.",
        ),
        ActivityRow(
            id = 4,
            type = "Phone call",
            moment = TaskFormat.moment(Now.minusSeconds(DAY * 2), Zone, Today),
            byMe = true,
            author = "Sam Carter",
            description = "Called Jordan's mother to confirm Thursday's visit. She asked to move it to the " +
                "afternoon because of a school pickup, so I moved it to 2:30 PM and updated the calendar. " +
                "She also mentioned the new wheelchair is due next week and asked whether we can help " +
                "arrange transport for the fitting appointment at the clinic.",
        ),
    )

    /** A long-running task's log: enough entries for "Jump to latest". */
    fun longActivity(): List<ActivityRow> = (1..8).map { n ->
        ActivityRow(
            id = 100 + n,
            type = if (n % 2 == 0) "Phone call" else "Documentation",
            moment = TaskFormat.moment(Now.minusSeconds(DAY * (400 - n * 48L)), Zone, Today),
            byMe = n % 3 == 0,
            author = if (n % 3 == 0) "Sam Carter" else "Priya Raman",
            description = "Checked in on progress toward the monthly goals and noted next steps.",
        )
    } + activity.last().copy(id = 200)

    fun detailState(state: TaskState = TaskState.NotStarted): TaskDetailUiState {
        val task = task(state)
        return TaskDetailUiState(
            phase = DetailPhase.Content,
            task = task,
            due = TaskFormat.due(task.due, Today),
            dueDate = task.due?.let { TaskFormat.date(it, Today) },
            activity = Loadable.Ready(activity),
            files = Loadable.Ready(files.take(3)),
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

    val files = listOf(
        FileRow(41, "Care plan 2026.pdf", FileKind.Pdf, 482_000, "Oct 30", "Dana Ortiz", viewable = true),
        FileRow(40, "Home photo.png", FileKind.Image, 1_240_000, "Oct 28", "You", viewable = true),
        FileRow(39, "Visit notes.txt", FileKind.Text, 3_100, "Oct 21", "Sam Reyes", viewable = true),
        FileRow(38, "Intake packet.docx", FileKind.Word, 26_400_000, "Sep 12", "Dana Ortiz", viewable = false),
        FileRow(37, "Budget.xlsx", FileKind.Other, 88_000, "Sep 2", "Sam Reyes", viewable = false),
    )

    /** Worst-case names: a long file name and a long double-barrelled author. */
    val longFiles = listOf(
        FileRow(
            50,
            "2026 Individual Support Plan - Jordan Lee - signed by guardian and case manager (final v3).pdf",
            FileKind.Pdf,
            2_310_000,
            "Oct 30",
            "Maria Fernanda Castellanos-Villanueva",
            viewable = true,
        ),
        FileRow(49, "IMG_20261028_143215.jpg", FileKind.Image, 3_400_000, "Oct 28", "You", viewable = true),
    )

    val today: LocalDate get() = Today

    private const val DAY = 86_400L
}
