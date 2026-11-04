package com.waivera.waivera.feature.tasks.files

import androidx.lifecycle.ViewModelStore
import com.waivera.waivera.core.data.model.FileKind
import com.waivera.waivera.core.data.model.TaskAttachment
import com.waivera.waivera.core.data.repository.TaskFileRepository
import com.waivera.waivera.core.network.error.WaiveraError
import com.waivera.waivera.core.testing.MainDispatcherRule
import com.waivera.waivera.feature.tasks.FakeTasks
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The read-only viewer: what each kind becomes, failures, and that the copy is deleted on close. */
@RunWith(RobolectricTestRunner::class)
class TaskFileViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    @get:Rule
    val folder = TemporaryFolder()

    private val tasks = FakeTasks()
    private val files = FakeTaskFiles()

    @Test
    fun a_text_file_shows_its_text_and_is_deleted_when_the_viewer_closes() = runTest {
        val file = folder.newFile("notes").apply { writeText("Visit moved to 2:30 PM.") }
        files.result = Result.success(file)
        tasks.attachments = Result.success(listOf(attachment(FileKind.Text, "Visit notes.txt")))

        val store = ViewModelStore()
        val viewModel = viewModel().also { store.put("file", it) }
        val content = awaitReady(viewModel)

        assertEquals("Visit notes.txt", viewModel.uiState.value.name)
        assertEquals(FileContent.Text("Visit moved to 2:30 PM.", cut = false), content)
        store.clear()
        assertEquals(listOf(file), files.closed)
    }

    @Test
    fun a_word_file_becomes_html_without_pictures() = runTest {
        files.result = Result.success(docx("Plan goals", "Ride to the clinic on Thursdays."))
        tasks.attachments = Result.success(listOf(attachment(FileKind.Word, "Plan.docx")))

        val content = assertIs<FileContent.Word>(awaitReady(viewModel()))

        assertTrue(content.html.contains("Plan goals"))
        assertTrue(content.html.contains("Ride to the clinic on Thursdays."))
        assertFalse(content.html.contains("<img"))
    }

    @Test
    fun a_failed_download_shows_the_error_and_retry_tries_again() = runTest {
        tasks.attachments = Result.success(listOf(attachment(FileKind.Pdf, "Care plan.pdf")))
        files.result = Result.failure(WaiveraError.Network())

        val viewModel = viewModel()
        awaitPhase(viewModel) { it == FilePhase.Failed }

        files.result = Result.success(folder.newFile("text").apply { writeText("ok") })
        tasks.attachments = Result.success(listOf(attachment(FileKind.Text, "notes.txt")))
        viewModel.load()
        assertIs<FileContent.Text>(awaitReady(viewModel))
        assertEquals(2, files.opened)
    }

    @Test
    fun a_file_no_longer_on_the_task_fails_without_downloading() = runTest {
        tasks.attachments = Result.success(emptyList())

        val viewModel = viewModel()
        awaitPhase(viewModel) { it == FilePhase.Failed }

        assertEquals(0, files.opened)
    }

    private fun viewModel() = TaskFileViewModel(TASK, ATTACHMENT, tasks, files, Dispatchers.Unconfined)

    private fun awaitReady(viewModel: TaskFileViewModel): FileContent =
        (awaitPhase(viewModel) { it is FilePhase.Ready } as FilePhase.Ready).content

    /** The view model works on real files, so give its IO a moment instead of a virtual clock. */
    private fun awaitPhase(viewModel: TaskFileViewModel, done: (FilePhase) -> Boolean): FilePhase {
        repeat(WAIT_STEPS) {
            val phase = viewModel.uiState.value.phase
            if (done(phase)) return phase
            Thread.sleep(WAIT_STEP_MILLIS)
        }
        error("Still ${viewModel.uiState.value.phase}")
    }

    private fun attachment(kind: FileKind, name: String) = TaskAttachment(
        attachmentId = ATTACHMENT,
        fileId = "f1",
        name = name,
        sizeBytes = SIZE,
        addedAt = null,
        addedBy = null,
        employeeFile = false,
        kind = kind,
    )

    /** The smallest Word file mammoth reads: a heading paragraph and a body paragraph. */
    private fun docx(vararg paragraphs: String): File {
        val file = folder.newFile("plan.docx")
        val body = paragraphs.joinToString("") { "<w:p><w:r><w:t>$it</w:t></w:r></w:p>" }
        ZipOutputStream(file.outputStream()).use { zip ->
            fun put(path: String, xml: String) {
                zip.putNextEntry(ZipEntry(path))
                zip.write(xml.toByteArray())
                zip.closeEntry()
            }
            val xml = """<?xml version="1.0"?>"""
            val pkg = "http://schemas.openxmlformats.org/package/2006"
            val office = "http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument"
            val types = "application/vnd.openxmlformats-"
            val main = "${types}officedocument.wordprocessingml.document.main+xml"
            put(
                "[Content_Types].xml",
                "$xml<Types xmlns=\"$pkg/content-types\">" +
                    "<Default Extension=\"rels\" ContentType=\"${types}package.relationships+xml\"/>" +
                    "<Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
                    "<Override PartName=\"/word/document.xml\" ContentType=\"$main\"/></Types>",
            )
            put(
                "_rels/.rels",
                "$xml<Relationships xmlns=\"$pkg/relationships\">" +
                    "<Relationship Id=\"rId1\" Type=\"$office\" Target=\"word/document.xml\"/></Relationships>",
            )
            put(
                "word/document.xml",
                "$xml<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">" +
                    "<w:body>$body</w:body></w:document>",
            )
        }
        return file
    }

    private class FakeTaskFiles : TaskFileRepository {
        var result: Result<File> = Result.failure(WaiveraError.Unexpected())
        var opened = 0
        val closed = mutableListOf<File>()

        override suspend fun open(taskId: Int, file: TaskAttachment): Result<File> {
            opened++
            return result
        }

        override fun close(file: File) {
            closed += file
        }
    }

    private companion object {
        const val TASK = 12
        const val ATTACHMENT = 41
        const val SIZE = 1_000L
        const val WAIT_STEPS = 100
        const val WAIT_STEP_MILLIS = 20L
    }
}
