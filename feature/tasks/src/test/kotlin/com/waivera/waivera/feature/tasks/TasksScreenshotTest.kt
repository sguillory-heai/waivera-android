package com.waivera.waivera.feature.tasks

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import com.waivera.waivera.core.data.model.TaskState
import com.waivera.waivera.core.designsystem.component.WaiveraSheetFrame
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
}
