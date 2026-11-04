package com.waivera.waivera.core.data

import com.waivera.waivera.core.data.live.TaskMapping
import com.waivera.waivera.core.data.model.FileTemplate
import com.waivera.waivera.core.data.model.UploadProblem
import com.waivera.waivera.core.data.model.UploadState
import com.waivera.waivera.core.data.model.UploadTarget
import com.waivera.waivera.core.data.uploads.LiveTaskUploadRepository
import com.waivera.waivera.core.data.uploads.Sniffed
import com.waivera.waivera.core.data.uploads.StagedUpload
import com.waivera.waivera.core.data.uploads.TaskUploadProcessor
import com.waivera.waivera.core.data.uploads.TaskUploadScheduler
import com.waivera.waivera.core.data.uploads.TaskUploadStore
import com.waivera.waivera.core.data.uploads.UploadChecks
import com.waivera.waivera.core.data.uploads.UploadRefused
import com.waivera.waivera.core.data.uploads.UploadStager
import com.waivera.waivera.core.network.api.TaskApi
import com.waivera.waivera.core.network.dto.ActionSubTypeResponse
import com.waivera.waivera.core.network.dto.FileTypeResponse
import com.waivera.waivera.core.network.dto.TemplateFileResponse
import com.waivera.waivera.core.testing.FixtureServer
import java.io.File
import java.nio.file.Files
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest

/** Uploads follow the web's rules and form, and the server's refusals get the app's own reasons. */
class TaskUploadsTest {
    private val server = FixtureServer()
    private val directory: File = Files.createTempDirectory("uploads").toFile()
    private val clock = Clock.fixed(Instant.parse("2026-11-04T20:00:00Z"), ZoneOffset.UTC)
    private var scheduled = 0

    @AfterTest
    fun tearDown() {
        server.close()
        directory.deleteRecursively()
    }

    /** Stages `file://` paths as they are; anything named "refuse" is refused as too large. */
    private val stager = object : UploadStager {
        override fun stage(uri: String, directory: File): Result<StagedUpload> {
            if ("refuse" in uri) return Result.failure(UploadRefused(UploadProblem.TooLarge, "huge.pdf"))
            directory.mkdirs()
            val copy = File(directory, uri.substringAfterLast('/')).apply { writeText("%PDF-1.7 demo") }
            return Result.success(StagedUpload(copy, copy.name, "application/pdf"))
        }
    }

    private val store = TaskUploadStore(directory)

    private fun TestScope.repository() = LiveTaskUploadRepository(
        server.service<TaskApi>(),
        store,
        stager,
        TaskUploadScheduler { scheduled++ },
        clock,
        StandardTestDispatcher(testScheduler),
        backgroundScope,
    )

    private val processor get() = TaskUploadProcessor(server.service<TaskApi>(), store)

    @Test
    fun file_types_are_read_from_their_first_bytes() {
        assertEquals(Sniffed.Pdf, UploadChecks.sniff("%PDF-1.7".toByteArray()))
        assertEquals(Sniffed.Jpeg, UploadChecks.sniff(byteArrayOf(-1, -40, -1, -32)))
        assertEquals(Sniffed.Gif, UploadChecks.sniff("GIF89a".toByteArray()))
        val heic = byteArrayOf(0, 0, 0, 24) + "ftypheic".toByteArray()
        assertEquals(Sniffed.OtherImage, UploadChecks.sniff(heic))
        assertEquals(Sniffed.Unknown, UploadChecks.sniff("PK\u0003\u0004".toByteArray()))
    }

    @Test
    fun the_webs_limits_decide_what_is_sent() {
        assertEquals(UploadChecks.Plan.Send, UploadChecks.plan(Sniffed.Pdf, 1_000))
        assertEquals(UploadChecks.Plan.Refuse(UploadProblem.Empty), UploadChecks.plan(Sniffed.Pdf, 0))
        assertEquals(UploadChecks.Plan.Refuse(UploadProblem.Unsupported), UploadChecks.plan(Sniffed.Unknown, 10))
        val over = UploadChecks.MAX_BYTES + 1
        assertEquals(UploadChecks.Plan.Refuse(UploadProblem.TooLarge), UploadChecks.plan(Sniffed.Pdf, over))
        // A big photo shrinks instead of failing; HEIC always becomes a JPG.
        assertEquals(UploadChecks.Plan.Reencode, UploadChecks.plan(Sniffed.Jpeg, over))
        assertEquals(UploadChecks.Plan.Reencode, UploadChecks.plan(Sniffed.OtherImage, 500))
    }

    @Test
    fun names_take_the_extension_their_content_has() {
        assertEquals("scan.png", UploadChecks.fileName("scan.jpeg", Sniffed.Png, "upload"))
        assertEquals("IMG_1.jpeg", UploadChecks.fileName("IMG_1.jpeg", Sniffed.Jpeg, "upload"))
        assertEquals("IMG_2.jpg", UploadChecks.fileName("IMG_2.HEIC", Sniffed.Jpeg, "upload"))
        assertEquals("Signed plan.pdf", UploadChecks.fileName("../Signed plan", Sniffed.Pdf, "upload"))
        assertEquals("upload.pdf", UploadChecks.fileName("..", Sniffed.Pdf, "upload"))
        assertEquals("a-b.pdf", UploadChecks.fileName("a:b.pdf", Sniffed.Pdf, "upload"))
    }

    @Test
    fun required_files_keep_only_named_requirements_and_mark_forms() = runTest {
        server.json(
            "GET /api/tasks/requiredAttachments/42",
            """[
              {"id":7,"templateFile":{"id":3,"fileName":"Signed service plan","type":{"id":5,"type":"pdf"}}},
              {"id":8,"templateFile":{"id":4,"fileName":"Risk screen","type":{"id":6},"form":{"id":2,"name":"Risk screen v2"}},
               "removalRequestPending":true},
              {"id":9,"templateFile":{"id":5,"fileName":"  "}}
            ]""",
        )
        val files = repository().requiredFiles(42).getOrThrow()
        assertEquals(listOf(7, 8), files.map { it.id })
        assertEquals(5, files[0].typeId)
        assertFalse(files[0].isForm)
        assertEquals("Risk screen v2", files[1].formName)
        assertTrue(files[1].removalPending)
    }

    @Test
    fun a_queued_file_is_sent_with_the_webs_form_and_finishes_with_the_saved_name() = runTest {
        server.json("POST /api/file/upload", """{"fileId":"f-1","fileName":"Signed service plan (2).pdf"}""")
        val repository = repository()
        val target = UploadTarget(requirementId = 7, requirementName = "Signed service plan", typeId = 5)
        repository.add(42, listOf("file:///picked/plan.pdf"), target)
        assertEquals(1, scheduled)

        assertFalse(processor.process())

        val body = server.requestsTo("/api/file/upload").single().body?.utf8().orEmpty()
        listOf(
            "name=\"file.File\"; filename=\"plan.pdf\"",
            "name=\"file.FileName\"",
            "Signed service plan",
            "name=\"file.RequiredAttachmentId\"",
            "name=\"file.TypeId\"",
            "name=\"file.TaskId\"",
            "name=\"file.RegionId\"",
            "name=\"file.MimeType\"",
        ).forEach { assertTrue(it in body, "missing $it") }
        assertFalse("file.DocumentCategoryId" in body)
        val upload = repository.uploads(42).first().single()
        assertEquals(UploadState.Done, upload.state)
        assertEquals("Signed service plan (2).pdf", upload.fileName)
    }

    @Test
    fun a_refused_pick_is_listed_with_its_reason_and_never_sent() = runTest {
        val repository = repository()
        repository.add(42, listOf("file:///picked/refuse.pdf"), UploadTarget())
        val upload = repository.uploads(42).first().single()
        assertEquals(UploadState.Failed, upload.state)
        assertEquals(UploadProblem.TooLarge, upload.problem)
        assertEquals("huge.pdf", upload.fileName)
        assertFalse(processor.process())
        assertTrue(server.requestsTo("/api/file/upload").isEmpty())
    }

    @Test
    fun server_refusals_use_the_apps_reasons() = runTest {
        server.json(
            "POST /api/file/upload",
            """{"statusCode":400,"message":"This PDF is password-protected.","errorCode":"PDF_PASSWORD_REQUIRED"}""",
            code = 400,
        )
        val repository = repository()
        repository.add(42, listOf("file:///picked/locked.pdf"), UploadTarget())
        processor.process()
        val upload = repository.uploads(42).first().single()
        assertEquals(UploadProblem.PdfLocked, upload.problem)
        assertFalse(upload.problem?.canRetry ?: true)
    }

    @Test
    fun a_locked_task_and_a_filled_requirement_are_told_apart() = runTest {
        server.json(
            "POST /api/file/upload",
            """{"statusCode":409,"message":"Already on this task.","errorCode":"FILE_ALREADY_UPLOADED"}""",
            code = 409,
        )
        val repository = repository()
        repository.add(42, listOf("file:///picked/a.pdf"), UploadTarget(requirementId = 7))
        processor.process()
        assertEquals(UploadProblem.AlreadyFilled, repository.uploads(42).first().single().problem)
    }

    @Test
    fun a_server_error_can_be_tried_again() = runTest {
        server.status("POST /api/file/upload", 500)
        val repository = repository()
        repository.add(42, listOf("file:///picked/a.pdf"), UploadTarget())
        processor.process()
        val failed = repository.uploads(42).first().single()
        assertEquals(UploadProblem.Failed, failed.problem)

        server.json("POST /api/file/upload", """{"fileId":"f-2","fileName":"a.pdf"}""")
        repository.retry(failed.id)
        // The retry lands on the app scope; wait for it to put the file back in line.
        repository.uploads(42).first { it.single().state == UploadState.Queued }
        assertEquals(2, scheduled)
        processor.process()
        assertEquals(UploadState.Done, repository.uploads(42).first().single().state)
    }

    @Test
    fun an_activity_file_takes_the_templates_name_with_its_own_extension() {
        assertEquals("Signed visit form.pdf", UploadChecks.templateName("Signed visit form", "application/pdf"))
        assertEquals("Signed visit form.jpg", UploadChecks.templateName("Signed visit form", "image/jpeg"))
        assertEquals("Visit.PDF", UploadChecks.templateName("Visit.PDF", "application/pdf"))
        assertEquals("Form v1.2.png", UploadChecks.templateName("Form v1.2", "image/png"))
        assertEquals("a-b.pdf", UploadChecks.templateName("a/b", "application/pdf"))
    }

    @Test
    fun activity_types_that_need_a_file_carry_their_template() {
        val types = TaskMapping.activityTypes(
            listOf(
                ActionSubTypeResponse(id = 1, actionSubTypeName = "Home visit", isUserLoggable = true),
                ActionSubTypeResponse(
                    id = 2,
                    actionSubTypeName = "Signed form",
                    isUserLoggable = true,
                    requiresFile = 903,
                    recommendedTimeInMinutes = 45,
                ),
                ActionSubTypeResponse(
                    id = 3,
                    actionSubTypeName = "Old form",
                    isUserLoggable = true,
                    requiresFile = 999,
                ),
            ),
            listOf(TemplateFileResponse(903, "Signed visit form", FileTypeResponse(4), employeeDocumentCategoryId = 6)),
        )
        val (visit, old, signed) = types
        assertFalse(visit.needsFile)
        assertEquals(FileTemplate(903, "Signed visit form", 4, 6), signed.fileTemplate)
        assertEquals(45, signed.recommendedMinutes)
        // A template the organization removed still needs a file; it goes up under its own name.
        assertTrue(old.needsFile)
        assertNull(old.fileTemplate)
    }

    @Test
    fun an_activity_file_is_sent_now_under_the_templates_name_and_type() = runTest {
        server.json("POST /api/file/upload", """{"fileId":"f-3","fileName":"Signed visit form (2).pdf"}""")
        val repository = repository()
        val file = repository.prepare("file:///picked/scan.pdf").getOrThrow()

        val saved = repository.sendNow(42, file, FileTemplate(903, "Signed visit form", 4, 6)).getOrThrow()

        assertEquals("Signed visit form (2).pdf", saved)
        val body = server.requestsTo("/api/file/upload").single().body?.utf8().orEmpty()
        listOf(
            "filename=\"Signed visit form.pdf\"",
            "name=\"file.TypeId\"",
            "name=\"file.DocumentCategoryId\"",
        ).forEach { assertTrue(it in body, "missing $it") }
        assertFalse("file.RequiredAttachmentId" in body)
        assertFalse(File(file.path).exists())
        // Nothing went through the background queue.
        assertTrue(repository.uploads(42).first().isEmpty())
    }

    @Test
    fun a_refused_activity_file_keeps_its_copy_for_another_try() = runTest {
        server.json(
            "POST /api/file/upload",
            """{"statusCode":400,"message":"x","errorCode":"FILE_EXTENSION_MISMATCH"}""",
            code = 400,
        )
        val repository = repository()
        val file = repository.prepare("file:///picked/scan.pdf").getOrThrow()
        val error = repository.sendNow(42, file, null).exceptionOrNull()
        assertEquals(UploadProblem.Mismatch, (error as? UploadRefused)?.problem)
        assertTrue(File(file.path).exists())
        repository.discard(file)
        assertFalse(File(file.path).exists())
    }

    @Test
    fun a_file_that_timed_out_and_is_then_already_there_counts_as_uploaded() = runTest {
        val repository = repository()
        repository.add(42, listOf("file:///picked/a.pdf"), UploadTarget())
        val id = store.snapshot().single().id
        // The first send timed out after the server may have saved it.
        store.update(id) { it.copy(mayHaveLanded = true) }
        server.json(
            "POST /api/file/upload",
            """{"statusCode":409,"message":"x","errorCode":"FILE_ALREADY_UPLOADED"}""",
            code = 409,
        )
        processor.process()
        assertEquals(UploadState.Done, repository.uploads(42).first().single().state)
    }

    @Test
    fun a_send_stopped_midway_goes_back_in_line() = runTest {
        server.json("POST /api/file/upload", """{"fileId":"f-1","fileName":"a.pdf"}""")
        val repository = repository()
        repository.add(42, listOf("file:///picked/a.pdf"), UploadTarget())
        val job = launch(Dispatchers.IO) { processor.process() }
        job.cancel()
        job.join()
        val state = store.snapshot().single().state
        assertTrue(state == UploadState.Queued || state == UploadState.Done, "stuck in $state")
    }

    @Test
    fun files_still_copying_at_sign_out_are_not_queued() = runTest {
        var signOut: suspend () -> Unit = {}
        val slowStager = object : UploadStager {
            override fun stage(uri: String, directory: File): Result<StagedUpload> {
                // Sign-out lands while this file is being copied.
                if ("second" in uri) runBlocking { signOut() }
                directory.mkdirs()
                val copy = File(directory, uri.substringAfterLast('/')).apply { writeText("%PDF-1.7 demo") }
                return Result.success(StagedUpload(copy, copy.name, "application/pdf"))
            }
        }
        val signingOut = LiveTaskUploadRepository(
            server.service<TaskApi>(),
            store,
            slowStager,
            TaskUploadScheduler { scheduled++ },
            clock,
            // Inline, so the sign-out inside the copy can finish without the test scheduler.
            Dispatchers.Unconfined,
            backgroundScope,
        )
        signOut = { signingOut.clear() }
        signingOut.add(42, listOf("file:///picked/first.pdf", "file:///picked/second.pdf"), UploadTarget())
        // The first was queued before the sign-out wiped the queue; the second never is.
        assertTrue(store.snapshot().isEmpty())
        assertEquals(0, scheduled)
    }

    @Test
    fun sign_out_forgets_queued_files_and_their_copies() = runTest {
        val repository = repository()
        repository.add(42, listOf("file:///picked/a.pdf"), UploadTarget())
        repository.clear()
        assertTrue(repository.uploads(42).first().isEmpty())
        assertFalse(File(directory, "files/a.pdf").exists())
        assertNull(store.snapshot().firstOrNull())
    }
}
