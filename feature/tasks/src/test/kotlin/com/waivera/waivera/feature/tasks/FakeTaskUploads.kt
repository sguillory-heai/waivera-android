package com.waivera.waivera.feature.tasks

import com.waivera.waivera.core.data.model.DocumentCategory
import com.waivera.waivera.core.data.model.FileTemplate
import com.waivera.waivera.core.data.model.PreparedFile
import com.waivera.waivera.core.data.model.RequiredFile
import com.waivera.waivera.core.data.model.TaskUpload
import com.waivera.waivera.core.data.model.UploadTarget
import com.waivera.waivera.core.data.repository.PickPurpose
import com.waivera.waivera.core.data.repository.TaskUploadRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** Uploads held in memory; tests set what each call answers and read what was asked. */
class FakeTaskUploads(private val categories: List<DocumentCategory> = emptyList()) : TaskUploadRepository {
    val uploads = MutableStateFlow<List<TaskUpload>>(emptyList())
    var required: Result<List<RequiredFile>> = Result.success(emptyList())
    var requiredLoads = 0
    val added = mutableListOf<Pair<List<String>, UploadTarget>>()
    val retried = mutableListOf<String>()
    val dismissed = mutableListOf<String>()

    override fun uploads(taskId: Int): Flow<List<TaskUpload>> =
        uploads.map { list -> list.filter { it.taskId == taskId } }

    override suspend fun requiredFiles(taskId: Int): Result<List<RequiredFile>> {
        requiredLoads++
        return required
    }

    override suspend fun categories(): Result<List<DocumentCategory>> = Result.success(categories)

    override suspend fun add(taskId: Int, uris: List<String>, target: UploadTarget) {
        added += uris to target
    }

    override fun retry(id: String) {
        retried += id
    }

    override fun dismiss(id: String) {
        dismissed += id
        uploads.update { list -> list.filterNot { it.id == id } }
    }

    /** What [prepare] answers; a picked uri's last segment becomes the file name. */
    var prepareResult: ((String) -> Result<PreparedFile>) = { uri ->
        Result.success(PreparedFile(uri, uri.substringAfterLast('/'), "application/pdf", 1_000))
    }
    var sendResult: Result<String?> = Result.success(null)
    val sent = mutableListOf<Pair<PreparedFile, FileTemplate?>>()
    val discarded = mutableListOf<PreparedFile>()

    val evidencePicks = mutableListOf<String>()

    override suspend fun prepare(uri: String, purpose: PickPurpose): Result<PreparedFile> {
        if (purpose == PickPurpose.Evidence) evidencePicks += uri
        return prepareResult(uri)
    }

    override suspend fun sendNow(taskId: Int, file: PreparedFile, template: FileTemplate?): Result<String?> {
        sent += file to template
        return sendResult
    }

    var evidenceResult: Result<Int> = Result.success(EVIDENCE_ID)
    val evidence = mutableListOf<PreparedFile>()

    override suspend fun sendEvidence(taskId: Int, file: PreparedFile): Result<Int> {
        evidence += file
        return evidenceResult
    }

    override suspend fun discard(file: PreparedFile) {
        discarded += file
    }

    companion object {
        const val EVIDENCE_ID = 777
    }
}
