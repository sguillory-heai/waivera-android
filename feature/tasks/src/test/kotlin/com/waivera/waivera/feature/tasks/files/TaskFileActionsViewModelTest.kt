package com.waivera.waivera.feature.tasks.files

import app.cash.turbine.test
import com.waivera.waivera.core.data.model.FileKind
import com.waivera.waivera.core.data.model.RequestAccess
import com.waivera.waivera.core.data.model.TaskAttachment
import com.waivera.waivera.core.data.repository.TaskFileRepository
import com.waivera.waivera.core.network.error.WaiveraError
import com.waivera.waivera.core.testing.MainDispatcherRule
import com.waivera.waivera.feature.tasks.FakeRequests
import com.waivera.waivera.feature.tasks.FakeTasks
import com.waivera.waivera.feature.tasks.R
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Tapping a task file: who gets the open / save / share sheet, and that every download is cleaned up. */
class TaskFileActionsViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    @get:Rule
    val folder = TemporaryFolder()

    private val tasks = FakeTasks()
    private val files = ExportFiles()
    private val export = FakeExport()

    @Test
    fun without_the_download_permission_a_tap_just_opens_the_file() = runTest {
        tasks.attachments = Result.success(listOf(attachment(employeeFile = false)))
        val viewModel = viewModel(RequestAccess(canDownloadEmployeeFiles = true, loaded = true))

        viewModel.effects.test {
            viewModel.choose(ATTACHMENT)
            assertEquals(FileActionEffect.Open(ATTACHMENT), awaitItem())
        }
        assertNull(viewModel.sheet.value)
        assertEquals(0, files.exported)
    }

    @Test
    fun employee_files_follow_the_employee_download_permission() = runTest {
        tasks.attachments = Result.success(listOf(attachment(employeeFile = true)))
        val viewModel = viewModel(RequestAccess(canDownloadEmployeeFiles = true, loaded = true))

        viewModel.choose(ATTACHMENT)

        assertEquals("Care plan.pdf", viewModel.sheet.value?.name)
    }

    @Test
    fun open_from_the_sheet_closes_it_and_opens_the_file() = runTest {
        val viewModel = withSheet()

        viewModel.effects.test {
            viewModel.open()
            assertEquals(FileActionEffect.Open(ATTACHMENT), awaitItem())
        }
        assertNull(viewModel.sheet.value)
    }

    @Test
    fun share_hands_over_a_copy_and_deletes_the_download() = runTest {
        val downloaded = folder.newFile("download")
        files.result = Result.success(downloaded)
        val viewModel = withSheet()

        viewModel.effects.test {
            viewModel.share()
            assertEquals(
                FileActionEffect.Share("content://share/Care plan.pdf", "application/pdf", "Care plan.pdf"),
                awaitItem(),
            )
        }
        assertEquals(listOf(downloaded), files.closed)
        assertNull(viewModel.sheet.value)
    }

    @Test
    fun save_asks_where_then_writes_there_and_deletes_the_download() = runTest {
        val downloaded = folder.newFile("download")
        files.result = Result.success(downloaded)
        val viewModel = withSheet()

        viewModel.effects.test {
            viewModel.save()
            assertEquals(FileActionEffect.PickSaveLocation("Care plan.pdf"), awaitItem())
            assertEquals(FileAction.Save, viewModel.sheet.value?.busy)
            viewModel.saveTo("content://docs/care-plan")
            assertEquals(FileActionEffect.Message(R.string.feature_tasks_file_saved), awaitItem())
        }
        assertEquals(listOf(downloaded to "content://docs/care-plan"), export.saved)
        assertEquals(listOf(downloaded), files.closed)
        assertNull(viewModel.sheet.value)
    }

    @Test
    fun backing_out_of_the_save_picker_saves_nothing_and_says_nothing() = runTest {
        val downloaded = folder.newFile("download")
        files.result = Result.success(downloaded)
        val viewModel = withSheet()

        viewModel.effects.test {
            viewModel.save()
            awaitItem()
            viewModel.saveTo(null)
            expectNoEvents()
        }
        assertEquals(emptyList(), export.saved)
        assertEquals(listOf(downloaded), files.closed)
    }

    @Test
    fun a_failed_download_keeps_the_sheet_open_to_try_again() = runTest {
        files.result = Result.failure(WaiveraError.Network())
        val viewModel = withSheet()

        viewModel.effects.test {
            viewModel.share()
            assertEquals(FileActionEffect.Message(R.string.feature_tasks_file_download_failed), awaitItem())
        }
        assertEquals(null, viewModel.sheet.value?.busy)
        assertEquals("Care plan.pdf", viewModel.sheet.value?.name)
    }

    @Test
    fun a_tap_without_the_file_list_still_opens_the_file() = runTest {
        tasks.attachments = Result.failure(WaiveraError.Network())
        val viewModel = viewModel(RequestAccess(canDownloadCaseFiles = true, loaded = true))

        viewModel.effects.test {
            viewModel.choose(ATTACHMENT)
            assertEquals(FileActionEffect.Open(ATTACHMENT), awaitItem())
        }
        assertNull(viewModel.sheet.value)
    }

    @Test
    fun closing_the_sheet_stops_a_running_download() = runTest {
        val gate = CompletableDeferred<Unit>()
        files.gate = gate
        files.result = Result.success(folder.newFile("download"))
        val viewModel = withSheet()

        viewModel.effects.test {
            viewModel.share()
            assertEquals(FileAction.Share, viewModel.sheet.value?.busy)
            viewModel.dismiss()
            gate.complete(Unit)
            expectNoEvents()
        }
        assertNull(viewModel.sheet.value)
    }

    private fun withSheet(): TaskFileActionsViewModel {
        tasks.attachments = Result.success(listOf(attachment(employeeFile = false)))
        return viewModel(RequestAccess(canDownloadCaseFiles = true, loaded = true)).also { it.choose(ATTACHMENT) }
    }

    private fun viewModel(access: RequestAccess) =
        TaskFileActionsViewModel(TASK, tasks, FakeRequests(access), files, export)

    private fun attachment(employeeFile: Boolean) = TaskAttachment(
        attachmentId = ATTACHMENT,
        fileId = "f1",
        name = "Care plan.pdf",
        sizeBytes = SIZE,
        addedAt = null,
        addedBy = null,
        employeeFile = employeeFile,
        kind = FileKind.Pdf,
    )

    private class ExportFiles : TaskFileRepository {
        var result: Result<File> = Result.failure(WaiveraError.Unexpected())
        var exported = 0
        var gate: CompletableDeferred<Unit>? = null
        val closed = mutableListOf<File>()

        override suspend fun open(taskId: Int, file: TaskAttachment): Result<File> = result

        override suspend fun export(taskId: Int, file: TaskAttachment): Result<File> {
            exported++
            gate?.await()
            return result
        }

        override suspend fun certificate(requestId: Int): Result<File> = result

        override fun close(file: File) {
            closed += file
        }
    }

    private class FakeExport : TaskFileExport {
        val saved = mutableListOf<Pair<File, String>>()

        override suspend fun saveTo(file: File, destinationUri: String): Result<Unit> {
            saved += file to destinationUri
            return Result.success(Unit)
        }

        override suspend fun shareableUri(file: File, fileName: String): Result<String> =
            Result.success("content://share/$fileName")

        override fun mimeType(fileName: String): String = "application/pdf"
    }

    private companion object {
        const val TASK = 12
        const val ATTACHMENT = 41
        const val SIZE = 1_000L
    }
}
