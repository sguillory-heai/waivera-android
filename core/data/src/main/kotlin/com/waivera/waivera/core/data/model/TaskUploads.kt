package com.waivera.waivera.core.data.model

import kotlinx.serialization.Serializable

/**
 * A file the task still needs (sprout_web `TaskAttachmentSection` "Required files"). One whose
 * template is a form is filled on the web; the rest take one uploaded file named [name].
 */
data class RequiredFile(
    val id: Int,
    val name: String,
    val typeId: Int,
    /** The form that fills it, when it is filled as a form instead of uploaded. */
    val formName: String?,
    /** Someone asked to drop this requirement and the request is waiting. */
    val removalPending: Boolean,
) {
    val isForm: Boolean get() = formName != null
}

/** A file template (sprout_api `TemplateFile`): an activity's file is saved under its name and type. */
data class FileTemplate(val id: Int, val name: String, val typeId: Int, val categoryId: Int?)

/** A file picked for Log activity, checked and copied, sent when the activity is logged. */
data class PreparedFile(val path: String, val name: String, val mimeType: String, val sizeBytes: Long) {
    val kind: FileKind get() = FileKind.of(name, mimeType)
}

/** An employee file category; an extra file on an employee task is filed under one. */
data class DocumentCategory(val id: Int, val name: String)

/** Where an upload goes: a requirement it fills, or an extra file (with a category on employee tasks). */
@Serializable
data class UploadTarget(
    val requirementId: Int? = null,
    /** The requirement's name, which the server saves the file under. */
    val requirementName: String? = null,
    val typeId: Int = PLAIN_TYPE_ID,
    val categoryId: Int? = null,
) {
    companion object {
        /** sprout_web sends type 1 for a file that fills no requirement. */
        const val PLAIN_TYPE_ID = 1
    }
}

/** Why a file was not uploaded, worded by the app; never the server's own text. */
@Serializable
enum class UploadProblem {
    Empty,
    TooLarge,
    Unsupported,
    Mismatch,
    PdfLocked,
    PdfUnreadable,

    /** The requirement already has a file, or a file of that name is on the task. */
    AlreadyFilled,

    /** The task was finished or is waiting on a signature; the server takes no files. */
    TaskLocked,

    /** The person can no longer edit this task. */
    NoAccess,

    /** The picked file could not be read, or its copy was cleared. */
    Unreadable,
    Failed,
    ;

    /** Trying again can work: the file itself was fine. */
    val canRetry: Boolean get() = this == Failed
}

@Serializable
enum class UploadState { Queued, Uploading, Failed, Done }

/** One file on its way to a task, as the task's Files list shows it until the server lists it. */
data class TaskUpload(
    val id: String,
    val taskId: Int,
    val fileName: String,
    val kind: FileKind,
    val sizeBytes: Long,
    val requirementId: Int?,
    val state: UploadState,
    /** 0..1 while sending. */
    val progress: Float? = null,
    val problem: UploadProblem? = null,
    /** The name the server saved it under, once done. */
    val savedName: String? = null,
)
