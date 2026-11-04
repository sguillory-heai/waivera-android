package com.waivera.waivera.core.data.uploads

import com.waivera.waivera.core.data.model.UploadTarget
import okhttp3.MultipartBody
import okhttp3.RequestBody

/**
 * sprout_web `uploadFile`'s form for `POST api/file/upload`. OrganizationId, RegionId and
 * participantId are the web's fixed placeholders: the server reads the task's own organization,
 * but model binding still needs RegionId present.
 */
internal object UploadForm {
    private const val PLACEHOLDER_ORGANIZATION = "1"
    private const val PLACEHOLDER_REGION = "2"
    private const val PLACEHOLDER_PARTICIPANT = "2"

    @Suppress("LongParameterList") // One argument per form field the web sends.
    fun parts(
        body: RequestBody,
        fileName: String,
        target: UploadTarget,
        taskId: Int,
        mimeType: String,
        sizeBytes: Long,
    ): List<MultipartBody.Part> = buildList {
        add(MultipartBody.Part.createFormData("file.File", fileName, body))
        add(field("file.FileName", target.requirementName ?: fileName))
        add(field("file.DurationInMinutes", "0"))
        target.requirementId?.let { add(field("file.RequiredAttachmentId", it.toString())) }
        add(field("file.OrganizationId", PLACEHOLDER_ORGANIZATION))
        add(field("file.RegionId", PLACEHOLDER_REGION))
        add(field("file.participantId", PLACEHOLDER_PARTICIPANT))
        add(field("file.TypeId", target.typeId.toString()))
        target.categoryId?.let { add(field("file.DocumentCategoryId", it.toString())) }
        add(field("file.TaskId", taskId.toString()))
        add(field("file.MimeType", mimeType))
        add(field("file.Size", sizeBytes.toString()))
    }

    private fun field(name: String, value: String) = MultipartBody.Part.createFormData(name, value)

    /** sprout_web `uploadComplianceEvidence`: the server takes everything else from the task. */
    fun evidenceParts(body: RequestBody, fileName: String, taskId: Int, mimeType: String, sizeBytes: Long) = listOf(
        MultipartBody.Part.createFormData("file.File", fileName, body),
        field("file.TaskId", taskId.toString()),
        field("file.MimeType", mimeType),
        field("file.Size", sizeBytes.toString()),
    )
}
