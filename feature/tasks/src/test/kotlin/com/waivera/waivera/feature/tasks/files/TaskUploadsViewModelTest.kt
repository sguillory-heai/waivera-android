package com.waivera.waivera.feature.tasks.files

import app.cash.turbine.test
import com.waivera.waivera.core.data.model.DocumentCategory
import com.waivera.waivera.core.data.model.FileKind
import com.waivera.waivera.core.data.model.RequiredFile
import com.waivera.waivera.core.data.model.TaskUpload
import com.waivera.waivera.core.data.model.UploadProblem
import com.waivera.waivera.core.data.model.UploadState
import com.waivera.waivera.core.data.model.UploadTarget
import com.waivera.waivera.core.network.error.WaiveraError
import com.waivera.waivera.core.testing.MainDispatcherRule
import com.waivera.waivera.feature.tasks.FakeTaskUploads
import com.waivera.waivera.feature.tasks.Loadable
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

/** Required files and uploads on a task: loading, empty, error, the sheet's rules and how a finished file settles. */
class TaskUploadsViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val repository = FakeTaskUploads(categories = CATEGORIES)

    @Test
    fun required_files_load_and_an_empty_list_reads_as_ready() = runTest {
        repository.required = Result.success(emptyList())
        val viewModel = TaskUploadsViewModel(TASK, repository)
        viewModel.uiState.test {
            assertEquals(Loadable.Ready(emptyList()), awaitReady().required)
        }
    }

    @Test
    fun a_failed_load_shows_an_error_and_retry_recovers() = runTest {
        repository.required = Result.failure(WaiveraError.Network(null))
        val viewModel = TaskUploadsViewModel(TASK, repository)
        viewModel.uiState.test {
            assertEquals(Loadable.Failed, expectMostRecentItem().required)
            repository.required = Result.success(listOf(PLAN))
            viewModel.loadRequired()
            assertEquals(listOf(PLAN.id), (awaitItem().required as Loadable.Ready).value.map { it.id })
        }
    }

    @Test
    fun a_reload_failure_keeps_the_list_already_shown() = runTest {
        repository.required = Result.success(listOf(PLAN))
        val viewModel = TaskUploadsViewModel(TASK, repository)
        viewModel.uiState.test {
            awaitReady()
            repository.required = Result.failure(WaiveraError.Network(null))
            viewModel.loadRequired()
            runCurrent()
            expectNoEvents()
            assertTrue(viewModel.uiState.value.required is Loadable.Ready)
        }
    }

    @Test
    fun a_required_file_takes_one_file_under_its_name_and_forms_open_no_sheet() = runTest {
        repository.required = Result.success(listOf(PLAN, FORM))
        val viewModel = TaskUploadsViewModel(TASK, repository)
        viewModel.uiState.test {
            awaitReady()
            viewModel.startRequired(FORM.id)
            runCurrent()
            assertNull(viewModel.uiState.value.sheet)

            viewModel.startRequired(PLAN.id)
            val sheet = awaitItem().sheet
            assertEquals(PLAN.name, sheet?.title)
            assertTrue(sheet?.single == true)
            viewModel.picked(listOf("file:///a.pdf", "file:///b.pdf"))
            runCurrent()
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(listOf("file:///a.pdf"), repository.added.single().first)
        val target = UploadTarget(requirementId = PLAN.id, requirementName = PLAN.name, typeId = 5)
        assertEquals(target, repository.added.single().second)
    }

    @Test
    fun an_extra_file_on_an_employee_task_waits_for_a_category() = runTest {
        repository.required = Result.success(emptyList())
        val viewModel = TaskUploadsViewModel(TASK, repository).apply { employeeTask = true }
        viewModel.uiState.test {
            viewModel.startExtra()
            runCurrent()
            val sheet = expectMostRecentItem().sheet
            assertEquals(Loadable.Ready(CATEGORIES), sheet?.categories)
            assertFalse(sheet?.ready ?: true)

            viewModel.picked(listOf("file:///a.pdf"))
            runCurrent()
            assertTrue(repository.added.isEmpty())

            viewModel.pickCategory(CATEGORIES[1].id)
            viewModel.picked(listOf("file:///a.pdf", "file:///b.pdf"))
            runCurrent()
            assertNull(expectMostRecentItem().sheet)
        }
        val (uris, target) = repository.added.single()
        assertEquals(2, uris.size)
        assertEquals(CATEGORIES[1].id, target.categoryId)
    }

    @Test
    fun backing_out_of_the_picker_keeps_the_sheet() = runTest {
        repository.required = Result.success(emptyList())
        val viewModel = TaskUploadsViewModel(TASK, repository)
        viewModel.uiState.test {
            viewModel.startExtra()
            viewModel.picked(emptyList())
            runCurrent()
            assertTrue(expectMostRecentItem().sheet != null)
        }
        assertTrue(repository.added.isEmpty())
    }

    @Test
    fun a_required_files_upload_shows_on_its_row_and_moves_out_once_the_requirement_is_gone() = runTest {
        repository.required = Result.success(listOf(PLAN))
        repository.uploads.value = listOf(upload("u1", requirementId = PLAN.id, state = UploadState.Uploading))
        val viewModel = TaskUploadsViewModel(TASK, repository)
        viewModel.uiState.test {
            val ready = awaitReady()
            assertEquals("u1", (ready.required as Loadable.Ready).value.single().upload?.id)
            assertTrue(ready.uploads.isEmpty())

            repository.required = Result.success(emptyList())
            viewModel.loadRequired()
            runCurrent()
            assertEquals(listOf("u1"), expectMostRecentItem().uploads.map { it.id })
        }
    }

    @Test
    fun a_finished_upload_shows_as_done_then_reloads_the_lists_and_leaves() = runTest {
        repository.required = Result.success(listOf(PLAN))
        val viewModel = TaskUploadsViewModel(TASK, repository)
        viewModel.effects.test {
            runCurrent()
            val loadsBefore = repository.requiredLoads
            repository.uploads.value = listOf(upload("u1", requirementId = PLAN.id, state = UploadState.Done))
            runCurrent()
            advanceTimeBy(SETTLE_MILLIS - 1)
            runCurrent()
            expectNoEvents()
            assertTrue(repository.dismissed.isEmpty())

            advanceTimeBy(2)
            assertEquals(TaskUploadsEffect.FilesChanged, awaitItem())
            runCurrent()
            assertEquals(listOf("u1"), repository.dismissed)
            assertEquals(loadsBefore + 1, repository.requiredLoads)
        }
    }

    @Test
    fun a_requirement_filled_elsewhere_reloads_the_required_files_once() = runTest {
        repository.required = Result.success(listOf(PLAN))
        val viewModel = TaskUploadsViewModel(TASK, repository)
        viewModel.uiState.test {
            awaitReady()
            val loadsBefore = repository.requiredLoads
            val failed = upload("u1", PLAN.id, UploadState.Failed, UploadProblem.AlreadyFilled)
            repository.uploads.value = listOf(failed)
            runCurrent()
            repository.uploads.value = listOf(failed, upload("u2", null, UploadState.Queued))
            runCurrent()
            assertEquals(loadsBefore + 1, repository.requiredLoads)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun retry_and_dismiss_go_to_the_repository() = runTest {
        repository.required = Result.success(emptyList())
        val viewModel = TaskUploadsViewModel(TASK, repository)
        viewModel.retry("u1")
        viewModel.dismiss("u2")
        assertEquals(listOf("u1"), repository.retried)
        assertEquals(listOf("u2"), repository.dismissed)
    }

    private suspend fun app.cash.turbine.ReceiveTurbine<TaskUploadsUiState>.awaitReady(): TaskUploadsUiState {
        var state = awaitItem()
        while (state.required !is Loadable.Ready) state = awaitItem()
        return state
    }

    private fun upload(id: String, requirementId: Int?, state: UploadState, problem: UploadProblem? = null) =
        TaskUpload(
            id = id,
            taskId = TASK,
            fileName = "$id.pdf",
            kind = FileKind.Pdf,
            sizeBytes = 1_000,
            requirementId = requirementId,
            state = state,
            problem = problem,
        )

    private companion object {
        const val TASK = 42
        const val SETTLE_MILLIS = 2_500L
        val PLAN = RequiredFile(7, "Signed service plan", typeId = 5, formName = null, removalPending = false)
        val FORM = RequiredFile(8, "Risk screen", typeId = 6, formName = "Risk screen v2", removalPending = false)
        val CATEGORIES = listOf(DocumentCategory(1, "Certifications"), DocumentCategory(2, "Training"))
    }
}
