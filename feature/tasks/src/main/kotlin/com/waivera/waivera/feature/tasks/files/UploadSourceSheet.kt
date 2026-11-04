package com.waivera.waivera.feature.tasks.files

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.core.content.FileProvider
import com.waivera.waivera.core.data.model.DocumentCategory
import com.waivera.waivera.core.data.model.UploadTarget
import com.waivera.waivera.core.designsystem.component.WaiveraBottomSheet
import com.waivera.waivera.core.designsystem.component.WaiveraErrorState
import com.waivera.waivera.core.designsystem.component.WaiveraIconTile
import com.waivera.waivera.core.designsystem.component.WaiveraLoadingState
import com.waivera.waivera.core.designsystem.component.WaiveraPressable
import com.waivera.waivera.core.designsystem.component.WaiveraSheetFrame
import com.waivera.waivera.core.designsystem.component.WaiveraTileTone
import com.waivera.waivera.core.designsystem.icon.WaiveraIcons
import com.waivera.waivera.core.designsystem.preview.WaiveraPreviews
import com.waivera.waivera.core.designsystem.theme.WaiveraShapes
import com.waivera.waivera.core.designsystem.theme.WaiveraSizes
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTextStyles
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.feature.tasks.InfoDivider
import com.waivera.waivera.feature.tasks.Loadable
import com.waivera.waivera.feature.tasks.R
import com.waivera.waivera.feature.tasks.SheetGroup
import java.io.File
import java.util.UUID

internal data class UploadSheetActions(
    val onDismiss: () -> Unit = {},
    val onCategory: (Int) -> Unit = {},
    val onRetryCategories: () -> Unit = {},
    val onSource: (UploadSource) -> Unit = {},
)

/**
 * Add a file: take a photo, choose photos, or choose a file (PDFs and images, from Files, Drive
 * and the like). The system pickers need no storage permission, and the camera app is asked for
 * one photo, so the app holds no camera permission either. [onPicked] gets the chosen uris.
 */
@Composable
internal fun UploadSourceHost(
    sheet: UploadSheet?,
    actions: UploadSheetActions,
    onPicked: (List<String>) -> Unit,
    onLaunch: () -> Unit = {},
) {
    // Registered even with no sheet, so a result that arrives after the app was stopped still lands.
    val pick = rememberUploadPicker(single = sheet?.single == true, onPicked = onPicked, onLaunch = onLaunch)
    sheet ?: return
    WaiveraBottomSheet(
        onDismissRequest = actions.onDismiss,
        title = sheet.title?.let { stringResource(R.string.feature_tasks_upload_title_for, it) }
            ?: stringResource(R.string.feature_tasks_add_file),
    ) {
        UploadSheetContent(sheet = sheet, actions = actions.copy(onSource = pick))
    }
}

/**
 * Opens the camera, photo picker or file picker for [UploadSource]s; [onPicked] gets the chosen
 * uris, or an empty list when the person backs out. [single] asks each picker for one file.
 */
@Composable
internal fun rememberUploadPicker(
    single: Boolean,
    onPicked: (List<String>) -> Unit,
    onLaunch: () -> Unit = {},
): (UploadSource) -> Unit {
    val context = LocalContext.current
    val latest by rememberUpdatedState(onPicked)
    var cameraFile by rememberSaveable { mutableStateOf<String?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val path = cameraFile
        cameraFile = null
        if (saved && path != null) latest(listOf(Uri.fromFile(File(path)).toString())) else latest(emptyList())
    }
    val onePhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        latest(listOfNotNull(uri?.toString()))
    }
    val photos = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(MAX_PICK),
    ) { uris -> latest(uris.map(Uri::toString)) }
    val oneFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        latest(listOfNotNull(uri?.toString()))
    }
    val files = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        latest(uris.map(Uri::toString))
    }
    return { source ->
        onLaunch()
        when (source) {
            UploadSource.Camera -> {
                val (file, uri) = cameraTarget(context)
                cameraFile = file.path
                camera.launch(uri)
            }
            UploadSource.Photos -> {
                val request = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                if (single) onePhoto.launch(request) else photos.launch(request)
            }
            UploadSource.Files -> if (single) oneFile.launch(MIME_TYPES) else files.launch(MIME_TYPES)
        }
    }
}

@Composable
internal fun UploadSheetContent(sheet: UploadSheet, actions: UploadSheetActions) {
    Column(verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.MediumSmall)) {
        if (sheet.needsCategory) {
            Text(
                text = stringResource(R.string.feature_tasks_upload_category),
                style = MaterialTheme.typography.titleSmall,
                color = WaiveraTheme.colors.text,
            )
            Categories(sheet, actions)
        }
        SheetGroup(Modifier.padding(bottom = WaiveraSpacing.XxSmall)) {
            UploadSource.entries.forEachIndexed { index, source ->
                if (index > 0) InfoDivider()
                SourceRow(source, enabled = sheet.ready, single = sheet.single, onClick = { actions.onSource(source) })
            }
        }
        Text(
            text = stringResource(
                if (sheet.single) R.string.feature_tasks_upload_limits_one else R.string.feature_tasks_upload_limits,
            ),
            style = MaterialTheme.typography.bodySmall,
            color = WaiveraTheme.colors.textSecondary,
            modifier = Modifier.padding(bottom = WaiveraSpacing.Small),
        )
    }
}

@Composable
private fun Categories(sheet: UploadSheet, actions: UploadSheetActions) {
    when (val categories = sheet.categories) {
        Loadable.Loading -> WaiveraLoadingState(
            caption = stringResource(R.string.feature_tasks_upload_categories_loading),
        )
        Loadable.Failed -> WaiveraErrorState(
            message = stringResource(R.string.feature_tasks_upload_categories_error),
            onRetry = actions.onRetryCategories,
            dense = true,
        )
        is Loadable.Ready -> SheetGroup {
            categories.value.forEachIndexed { index, category ->
                if (index > 0) InfoDivider()
                CategoryRow(category, selected = category.id == sheet.target.categoryId, onClick = {
                    actions.onCategory(category.id)
                })
            }
        }
    }
}

@Composable
private fun CategoryRow(category: DocumentCategory, selected: Boolean, onClick: () -> Unit) {
    WaiveraPressable(
        onClick = onClick,
        role = Role.RadioButton,
        shape = WaiveraShapes.SheetCard,
        modifier = Modifier.fillMaxWidth().semantics { this.selected = selected },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = WaiveraSpacing.MinTouchTarget)
                .padding(horizontal = WaiveraSpacing.Medium, vertical = WaiveraSpacing.MediumSmall),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = category.name,
                style = WaiveraTextStyles.FieldValue,
                color = WaiveraTheme.colors.text,
                modifier = Modifier.weight(1f),
            )
            if (selected) {
                Icon(
                    painter = painterResource(WaiveraIcons.Check),
                    contentDescription = null,
                    tint = WaiveraTheme.colors.accent,
                    modifier = Modifier.size(WaiveraSizes.IconSmall),
                )
            }
        }
    }
}

@Composable
internal fun SourceRow(source: UploadSource, enabled: Boolean, single: Boolean, onClick: () -> Unit) {
    val colors = WaiveraTheme.colors
    WaiveraPressable(
        onClick = onClick,
        enabled = enabled,
        shape = WaiveraShapes.SheetCard,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = WaiveraSpacing.MinTouchTarget)
                .padding(horizontal = WaiveraSpacing.Medium, vertical = WaiveraSpacing.MediumSmall),
            horizontalArrangement = Arrangement.spacedBy(WaiveraSizes.FieldTileGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WaiveraIconTile(
                icon = sourceIcon(source),
                tone = when (source) {
                    UploadSource.Camera -> WaiveraTileTone.Brand
                    UploadSource.Photos -> WaiveraTileTone.Teal
                    UploadSource.Files -> WaiveraTileTone.Amber
                },
                size = WaiveraSizes.FieldTile,
                iconSize = WaiveraSizes.FieldTileGlyph,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.XxSmall)) {
                Text(
                    text = stringResource(source.title(single)),
                    style = WaiveraTextStyles.FieldValue,
                    color = if (enabled) colors.text else colors.textSecondary,
                )
                Text(
                    text = stringResource(source.detail()),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
            }
            Icon(
                painter = painterResource(WaiveraIcons.ChevronRight),
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier.size(WaiveraSizes.IconSmall),
            )
        }
    }
}

private fun UploadSource.title(single: Boolean): Int = when (this) {
    UploadSource.Camera -> R.string.feature_tasks_upload_camera
    UploadSource.Photos -> if (single) R.string.feature_tasks_upload_photo else R.string.feature_tasks_upload_photos
    UploadSource.Files -> R.string.feature_tasks_upload_files
}

private fun UploadSource.detail(): Int = when (this) {
    UploadSource.Camera -> R.string.feature_tasks_upload_camera_detail
    UploadSource.Photos -> R.string.feature_tasks_upload_photos_detail
    UploadSource.Files -> R.string.feature_tasks_upload_files_detail
}

/**
 * A fresh file in the camera folder for one photo, shared with the camera app through the
 * task file provider. The upload queue copies it, and the folder is emptied on each new photo.
 */
private fun cameraTarget(context: Context): Pair<File, Uri> {
    val directory = File(context.cacheDir, CAMERA_DIRECTORY).apply {
        mkdirs()
        listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > STALE_MILLIS }?.forEach(File::delete)
    }
    val file = File(directory, "photo-${UUID.randomUUID()}.jpg")
    val uri = FileProvider.getUriForFile(context, "${context.packageName}$AUTHORITY_SUFFIX", file)
    return file to uri
}

private const val CAMERA_DIRECTORY = "feature_tasks_camera"
private const val AUTHORITY_SUFFIX = ".feature.tasks.files"
private const val STALE_MILLIS = 60L * 60 * 1000
private const val MAX_PICK = 10
private val MIME_TYPES = arrayOf("application/pdf", "image/*")

@WaiveraPreviews
@androidx.compose.runtime.Composable
private fun UploadSheetPreview() {
    WaiveraTheme {
        WaiveraSheetFrame(title = "Add a file") {
            UploadSheetContent(
                sheet = UploadSheet(
                    target = UploadTarget(categoryId = 2),
                    title = null,
                    needsCategory = true,
                    categories = Loadable.Ready(
                        listOf(DocumentCategory(1, "Certifications"), DocumentCategory(2, "Training")),
                    ),
                    single = false,
                ),
                actions = UploadSheetActions(),
            )
        }
    }
}
