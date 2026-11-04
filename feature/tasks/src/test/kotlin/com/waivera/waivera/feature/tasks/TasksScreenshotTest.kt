package com.waivera.waivera.feature.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import com.waivera.waivera.core.data.model.DelayFilter
import com.waivera.waivera.core.data.model.RequestKind
import com.waivera.waivera.core.data.model.TaskState
import com.waivera.waivera.core.designsystem.component.WaiveraSheetFrame
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.core.screenshottesting.ScreenshotSdk
import com.waivera.waivera.core.screenshottesting.captureScreenshots
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Tasks tab, a task and the Log activity sheet across the shared matrix. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotSdk.VALUE])
class TasksScreenshotTest {
    @Test
    fun tasksPopulated() = captureScreenshots("tasks_populated") {
        TasksContent(state = TasksPreviewStates.populated(), actions = remember { TasksActions() })
    }

    @Test
    fun tasksEmpty() = captureScreenshots("tasks_empty") {
        TasksContent(state = TasksUiState(phase = TasksPhase.Empty), actions = remember { TasksActions() })
    }

    @Test
    fun taskDetail() = captureScreenshots("task_detail") {
        TaskDetailContent(
            state = TaskPreviewData.detailState(),
            snackbar = remember { SnackbarHostState() },
            actions = TaskDetailActions.None,
        )
    }

    @Test
    fun taskDetailFinished() = captureScreenshots("task_detail_finished") {
        TaskDetailContent(
            state = TaskPreviewData.detailState(TaskState.Completed),
            snackbar = remember { SnackbarHostState() },
            actions = TaskDetailActions.None,
        )
    }

    @Test
    fun logActivitySheet() = captureScreenshots("task_log_activity") {
        WaiveraSheetFrame(title = "Log activity") {
            LogActivityFields(
                form = TaskPreviewData.logForm(),
                task = TaskPreviewData.task(),
                actions = LogActivityActions.None,
                onChooseType = {},
            )
        }
    }

    @Test
    fun requests() = captureScreenshots("tasks_requests", heightDp = 1100) {
        RequestsContent(
            state = TaskPreviewData.requestsState(),
            snackbar = remember { SnackbarHostState() },
            actions = remember { RequestsActions() },
        )
    }

    @Test
    fun requestReview() = captureScreenshots("tasks_request_review", heightDp = 1000) {
        WaiveraSheetFrame(title = "More time") {
            ReviewFields(item = TaskPreviewData.waitingRequest(), onApprove = {}, onReject = {}, onOpenTask = {})
        }
    }

    @Test
    fun askChooser() = captureScreenshots("tasks_ask_choices") {
        WaiveraSheetFrame(title = "Ask for a change") {
            AskChoices(TaskPreviewData.askForm(null), TaskPreviewData.task(), TaskPreviewData.today) {}
        }
    }

    @Test
    fun askMoreTime() = captureScreenshots("tasks_ask_more_time", heightDp = 1100) {
        WaiveraSheetFrame(title = "Ask for more time") {
            AskFieldsShot(AskKind.MoreTime)
        }
    }

    @Test
    fun askDelay() = captureScreenshots("tasks_ask_delay", heightDp = 1100) {
        WaiveraSheetFrame(title = "Report a delay") {
            AskFieldsShot(AskKind.Delay)
        }
    }

    @Test
    fun askClosure() = captureScreenshots("tasks_ask_closure", heightDp = 1100) {
        WaiveraSheetFrame(title = "Ask to close") {
            AskFieldsShot(AskKind.Closure)
        }
    }

    @Test
    fun taskDetailPending() = captureScreenshots("task_detail_pending") {
        TaskDetailContent(
            state = TaskPreviewData.detailState().copy(
                pending = PendingBanner(RequestKind.MoreTime, newDue = "Thu, Nov 19"),
            ),
            snackbar = remember { SnackbarHostState() },
            actions = TaskDetailActions.None,
        )
    }

    @Test
    fun tasksDelaysHidden() = captureScreenshots("tasks_delays_hidden") {
        val populated = TasksPreviewStates.populated()
        TasksContent(
            state = populated.copy(
                delays = DelayFilter.Hide,
                sections = populated.sections.filter { it.group != DueGroup.Delayed },
            ),
            actions = remember { TasksActions() },
        )
    }

    @Test
    fun tasksDelayedOnlyEmpty() = captureScreenshots("tasks_delayed_only_empty") {
        TasksContent(
            state = TasksUiState(phase = TasksPhase.Empty, delays = DelayFilter.Only),
            actions = remember { TasksActions() },
        )
    }

    @Test
    fun delaysChip() = captureScreenshots("tasks_delays_chip", heightDp = 320) {
        androidx.compose.foundation.layout.Column(
            modifier = androidx.compose.ui.Modifier
                .background(WaiveraTheme.colors.background)
                .padding(WaiveraSpacing.Gutter),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(WaiveraSpacing.Small),
        ) {
            DelaysChip(selected = DelayFilter.Show, onSelect = {})
            DelaysChip(selected = DelayFilter.Hide, onSelect = {})
            DelaysChip(selected = DelayFilter.Only, onSelect = {})
        }
    }

    @Test
    fun delaysSheet() = captureScreenshots("tasks_delays_sheet") {
        WaiveraSheetFrame(title = "Delayed tasks") {
            DelaysOptions(selected = DelayFilter.Show, onSelect = {})
        }
    }

    @Test
    fun taskDetailDelayed() = captureScreenshots("task_detail_delayed") {
        val base = TaskPreviewData.detailState()
        TaskDetailContent(
            state = base.copy(
                due = DueText.WasOn("Oct 30"),
                dueDate = "Fri, Oct 30",
                delay = DelayNote(
                    through = "Nov 20",
                    reason = "Participant hospitalized",
                    explanation = "Jordan is in the hospital until next week; the visit moves to after discharge.",
                ),
            ),
            snackbar = remember { SnackbarHostState() },
            actions = TaskDetailActions.None,
        )
    }

    @androidx.compose.runtime.Composable
    private fun androidx.compose.foundation.layout.ColumnScope.AskFieldsShot(kind: AskKind) = AskFields(
        form = TaskPreviewData.askForm(kind),
        task = TaskPreviewData.task(),
        today = TaskPreviewData.today,
        actions = AskActions(),
        onPickReason = {},
        onPickFile = {},
    )
}
