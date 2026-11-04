package com.waivera.waivera.feature.tasks

import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.waivera.waivera.core.data.model.FileKind
import com.waivera.waivera.core.designsystem.component.SkeletonText
import com.waivera.waivera.core.designsystem.component.WaiveraCardGroup
import com.waivera.waivera.core.designsystem.component.WaiveraCountTone
import com.waivera.waivera.core.designsystem.component.WaiveraErrorState
import com.waivera.waivera.core.designsystem.component.WaiveraInsetDivider
import com.waivera.waivera.core.designsystem.component.WaiveraSectionHeader
import com.waivera.waivera.core.designsystem.component.WaiveraTileTone
import com.waivera.waivera.core.designsystem.component.rememberSkeletonPulse
import com.waivera.waivera.core.designsystem.component.tint
import com.waivera.waivera.core.designsystem.icon.WaiveraIcons
import com.waivera.waivera.core.designsystem.preview.WaiveraPreviews
import com.waivera.waivera.core.designsystem.theme.WaiveraShapes
import com.waivera.waivera.core.designsystem.theme.WaiveraSizes
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.feature.tasks.files.AddFileFooter
import com.waivera.waivera.feature.tasks.files.UploadActions
import com.waivera.waivera.feature.tasks.files.UploadRow
import com.waivera.waivera.feature.tasks.files.UploadRows

/**
 * The task's files, newest first. PDFs, images, text and Word files open in the phone's
 * read-only viewer; anything else, or anything too large, opens the task on the web.
 * Hidden when the task has no files.
 */
@Composable
internal fun FilesSection(
    files: Loadable<List<FileRow>>,
    onOpen: (Int) -> Unit,
    onRetry: () -> Unit,
    uploads: List<UploadRow> = emptyList(),
    uploadActions: UploadActions = UploadActions(),
    canUpload: Boolean = false,
) {
    val rows = (files as? Loadable.Ready)?.value.orEmpty()
    // Shown when there are files, files going up, or room to add one.
    val nothingListed = files is Loadable.Ready && rows.isEmpty()
    if (nothingListed && uploads.isEmpty() && !canUpload) return
    WaiveraSectionHeader(
        title = stringResource(R.string.feature_tasks_files_section),
        count = rows.size.takeIf { it > 0 },
        countDescription = pluralStringResource(R.plurals.feature_tasks_files_count, rows.size, rows.size),
        countTone = WaiveraCountTone.Info,
        modifier = Modifier.padding(
            start = WaiveraSpacing.Gutter + WaiveraSpacing.ExtraSmall,
            end = WaiveraSpacing.Gutter,
            top = WaiveraSpacing.MediumLarge,
            bottom = WaiveraSpacing.Small,
        ),
    )
    if (uploads.isNotEmpty() || files !is Loadable.Ready || rows.isNotEmpty()) {
        WaiveraCardGroup {
            UploadRows(uploads, uploadActions)
            if (uploads.isNotEmpty() && (files !is Loadable.Ready || rows.isNotEmpty())) {
                WaiveraInsetDivider(inset = WaiveraSpacing.Gutter + WaiveraSizes.FieldDividerInset)
            }
            FileList(files, rows, onOpen, onRetry)
        }
    }
    if (canUpload) AddFileFooter(uploadActions.onAddFile)
}

@Composable
private fun FileList(files: Loadable<List<FileRow>>, rows: List<FileRow>, onOpen: (Int) -> Unit, onRetry: () -> Unit) {
    when (files) {
        Loadable.Loading -> Column(
            modifier = Modifier.padding(WaiveraSpacing.Medium),
            verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.Small),
        ) {
            val pulse = rememberSkeletonPulse()
            SkeletonText(style = MaterialTheme.typography.titleSmall, widthFraction = SKELETON_NAME, pulse = pulse)
            SkeletonText(style = MaterialTheme.typography.bodySmall, widthFraction = SKELETON_META, pulse = pulse)
        }
        Loadable.Failed -> WaiveraErrorState(
            message = stringResource(R.string.feature_tasks_files_error),
            onRetry = onRetry,
            dense = true,
        )
        is Loadable.Ready -> rows.forEachIndexed { index, row ->
            if (index > 0) WaiveraInsetDivider(inset = WaiveraSpacing.Gutter + WaiveraSizes.FieldDividerInset)
            FileRowItem(row, onOpen = { onOpen(row.attachmentId) })
        }
    }
}

@Composable
private fun FileRowItem(row: FileRow, onOpen: () -> Unit) {
    val colors = WaiveraTheme.colors
    val context = LocalContext.current
    val size = Formatter.formatShortFileSize(context, row.sizeBytes)
    // Type, size and date are short and always fit; the name that added it goes on its own line
    // so a long one ellipsizes alone instead of pushing the date off.
    val meta = listOfNotNull(row.kind.label(), size, row.added).joinToString(META_SEPARATOR)
    val where = if (row.viewable) {
        null
    } else if (row.kind == FileKind.Other) {
        stringResource(R.string.feature_tasks_file_opens_on_web)
    } else {
        stringResource(R.string.feature_tasks_file_too_large)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = WaiveraSizes.ListRowMinHeight)
            .clickable(
                role = Role.Button,
                onClickLabel = stringResource(
                    if (row.viewable) R.string.feature_tasks_file_open else R.string.feature_tasks_file_open_web,
                ),
                onClick = onOpen,
            )
            .padding(horizontal = WaiveraSpacing.Gutter, vertical = WaiveraSpacing.MediumSmall),
        horizontalArrangement = Arrangement.spacedBy(WaiveraSizes.FieldTileGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KindTile(row.kind, Modifier.align(Alignment.Top))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.XxSmall)) {
            Text(
                text = row.name,
                style = MaterialTheme.typography.titleSmall,
                color = colors.text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = meta,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            row.addedBy?.let {
                Text(
                    text = stringResource(R.string.feature_tasks_file_added_by, it),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            where?.let { Text(text = it, style = MaterialTheme.typography.bodySmall, color = colors.link) }
        }
        Icon(
            painter = painterResource(if (row.viewable) WaiveraIcons.ChevronRight else WaiveraIcons.OpenExternal),
            contentDescription = null,
            tint = if (row.viewable) colors.textSecondary else colors.link,
            modifier = Modifier.size(WaiveraSizes.IconSmall),
        )
    }
}

/**
 * The file type as a short word on a coloured tile, so a PDF and a photo read apart at a glance.
 * The word is sized in dp, not sp, so it never outgrows the tile at large font sizes; the type is
 * also spelled out in the line under the name, which does scale.
 */
@Composable
internal fun KindTile(kind: FileKind, modifier: Modifier = Modifier) {
    val tint = kind.tone().tint(WaiveraTheme.colors)
    val size = with(LocalDensity.current) { TileWordSize.toSp() }
    Box(
        modifier = modifier
            .size(WaiveraSizes.FieldTile)
            .background(tint.container, WaiveraShapes.Tile),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = kind.short(),
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = size,
                lineHeight = size,
            ),
            color = tint.content,
            maxLines = 1,
        )
    }
}

private fun FileKind.tone(): WaiveraTileTone = when (this) {
    FileKind.Pdf -> WaiveraTileTone.Rose
    FileKind.Image -> WaiveraTileTone.Teal
    FileKind.Word -> WaiveraTileTone.Brand
    FileKind.Text -> WaiveraTileTone.Amber
    FileKind.Other -> WaiveraTileTone.Neutral
}

@Composable
private fun FileKind.short(): String = stringResource(
    when (this) {
        FileKind.Pdf -> R.string.feature_tasks_file_short_pdf
        FileKind.Image -> R.string.feature_tasks_file_short_image
        FileKind.Word -> R.string.feature_tasks_file_short_word
        FileKind.Text -> R.string.feature_tasks_file_short_text
        FileKind.Other -> R.string.feature_tasks_file_short_other
    },
)

@Composable
internal fun FileKind.label(): String = stringResource(
    when (this) {
        FileKind.Pdf -> R.string.feature_tasks_file_kind_pdf
        FileKind.Image -> R.string.feature_tasks_file_kind_image
        FileKind.Word -> R.string.feature_tasks_file_kind_word
        FileKind.Text -> R.string.feature_tasks_file_kind_text
        FileKind.Other -> R.string.feature_tasks_file_kind_other
    },
)

private const val META_SEPARATOR = " · "
private val TileWordSize = WaiveraSizes.TileWord
private const val SKELETON_NAME = 0.6f
private const val SKELETON_META = 0.4f

@WaiveraPreviews
@Composable
private fun FilesSectionPreview() {
    WaiveraTheme {
        Column { FilesSection(Loadable.Ready(TaskPreviewData.files), onOpen = {}, onRetry = {}) }
    }
}
