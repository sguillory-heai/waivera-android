package com.waivera.waivera.feature.tasks.files

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.waivera.waivera.core.data.model.ActivityType
import com.waivera.waivera.core.data.model.DocumentCategory
import com.waivera.waivera.core.data.model.FileKind
import com.waivera.waivera.core.data.model.FileTemplate
import com.waivera.waivera.core.data.model.PreparedFile
import com.waivera.waivera.core.data.model.UploadProblem
import com.waivera.waivera.core.data.model.UploadState
import com.waivera.waivera.core.data.model.UploadTarget
import com.waivera.waivera.core.designsystem.component.WaiveraSheetFrame
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.core.screenshottesting.ScreenshotSdk
import com.waivera.waivera.core.screenshottesting.captureScreenshots
import com.waivera.waivera.feature.tasks.FilesSection
import com.waivera.waivera.feature.tasks.Loadable
import com.waivera.waivera.feature.tasks.LogActivityActions
import com.waivera.waivera.feature.tasks.LogActivityFields
import com.waivera.waivera.feature.tasks.LogForm
import com.waivera.waivera.feature.tasks.TaskDetailActions
import com.waivera.waivera.feature.tasks.TaskDetailContent
import com.waivera.waivera.feature.tasks.TaskPreviewData
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Required files, files going up (sending, done, failed) and the Add a file sheet. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotSdk.VALUE])
class TaskUploadScreenshotTest {
    @Test
    fun requiredFiles() = captureScreenshots("task_required_files") {
        Column(Modifier.background(WaiveraTheme.colors.background)) {
            RequiredFilesSection(Loadable.Ready(REQUIRED), canUpload = true, actions = UploadActions())
        }
    }

    @Test
    fun filesWithUploads() = captureScreenshots("task_files_uploading", heightDp = 1100) {
        Column(Modifier.background(WaiveraTheme.colors.background)) {
            FilesSection(
                Loadable.Ready(TaskPreviewData.files),
                onOpen = {},
                onRetry = {},
                uploads = UPLOADS,
                canUpload = true,
            )
        }
    }

    @Test
    fun detailWithRequiredFiles() = captureScreenshots("task_detail_required_files", heightDp = 1600) {
        TaskDetailContent(
            state = TaskPreviewData.detailState(),
            snackbar = remember { SnackbarHostState() },
            actions = TaskDetailActions.None,
            uploads = TaskUploadsUiState(required = Loadable.Ready(REQUIRED), uploads = UPLOADS.take(1)),
        )
    }

    @Test
    fun sourceSheet() = captureScreenshots("task_upload_sheet") {
        WaiveraSheetFrame(title = "Upload “Signed service plan”") {
            UploadSheetContent(
                sheet = UploadSheet(
                    target = UploadTarget(requirementId = 7, requirementName = "Signed service plan"),
                    title = "Signed service plan",
                    needsCategory = false,
                    categories = Loadable.Ready(emptyList()),
                    single = true,
                ),
                actions = UploadSheetActions(),
            )
        }
    }

    @Test
    fun sourceSheetWithCategories() = captureScreenshots("task_upload_sheet_categories", heightDp = 1000) {
        WaiveraSheetFrame(title = "Add a file") {
            UploadSheetContent(
                sheet = UploadSheet(
                    target = UploadTarget(categoryId = 2),
                    title = null,
                    needsCategory = true,
                    categories = Loadable.Ready(
                        listOf(
                            DocumentCategory(1, "Certifications"),
                            DocumentCategory(2, "Training records"),
                            DocumentCategory(3, "Background checks and clearances"),
                        ),
                    ),
                    single = false,
                ),
                actions = UploadSheetActions(),
            )
        }
    }

    @Test
    fun logWithFileMissing() =
        logShot("task_log_file_missing", TaskPreviewData.logForm().withFileType(showErrors = true))

    @Test
    fun logWithFile() = logShot(
        "task_log_file_picked",
        TaskPreviewData.logForm().withFileType().copy(
            file = PreparedFile("/tmp/scan.pdf", "IMG_2041.pdf", "application/pdf", 420_000),
        ),
    )

    @Test
    fun logUploadRefused() = logShot(
        "task_log_file_refused",
        TaskPreviewData.logForm().withFileType().copy(fileProblem = UploadProblem.PdfLocked),
    )

    private fun logShot(name: String, form: LogForm) = captureScreenshots(name, heightDp = 1200) {
        WaiveraSheetFrame(title = "Log activity") {
            LogActivityFields(
                form = form,
                task = TaskPreviewData.task(),
                actions = LogActivityActions.None,
                onChooseType = {},
            )
        }
    }

    private fun LogForm.withFileType(showErrors: Boolean = false): LogForm {
        val type = ActivityType(
            id = 21,
            name = "Signed visit form",
            recommendedMinutes = 45,
            fixedDescription = null,
            needsFile = true,
            fileTemplate = FileTemplate(903, "Signed visit form", 4, null),
        )
        return copy(
            types = Loadable.Ready(listOf(type)),
            typeId = type.id,
            minutes = 45,
            description = "Signed at the home visit",
            showErrors = showErrors,
        )
    }

    private companion object {
        val UPLOADS = listOf(
            UploadRow("u1", "Home visit photo.jpg", FileKind.Image, 2_400_000, UploadState.Uploading, 0.42f, null),
            UploadRow("u2", "Mileage log.pdf", FileKind.Pdf, 180_000, UploadState.Done, null, null),
            UploadRow(
                "u3",
                "Bank statement.pdf",
                FileKind.Pdf,
                900_000,
                UploadState.Failed,
                null,
                UploadProblem.PdfLocked,
            ),
            UploadRow("u4", "Visit notes.pdf", FileKind.Pdf, 300_000, UploadState.Failed, null, UploadProblem.Failed),
        )
        val REQUIRED = listOf(
            RequiredRow(7, "Signed service plan", isForm = false, removalPending = false, upload = null),
            RequiredRow(8, "Risk screen", isForm = true, removalPending = false, upload = null),
            RequiredRow(
                9,
                "Proof of residence for the annual eligibility review",
                isForm = false,
                removalPending = false,
                upload = UploadRow("u5", "lease.pdf", FileKind.Pdf, 500_000, UploadState.Uploading, 0.7f, null),
            ),
            RequiredRow(10, "Medication list", isForm = false, removalPending = true, upload = null),
        )
    }
}
