package com.waivera.waivera.feature.tasks.files

import android.text.format.Formatter
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.waivera.waivera.core.data.model.UploadProblem
import com.waivera.waivera.core.data.model.UploadState
import com.waivera.waivera.core.designsystem.component.WaiveraButton
import com.waivera.waivera.core.designsystem.component.WaiveraButtonSize
import com.waivera.waivera.core.designsystem.component.WaiveraButtonVariant
import com.waivera.waivera.core.designsystem.component.WaiveraCardGroup
import com.waivera.waivera.core.designsystem.component.WaiveraCountTone
import com.waivera.waivera.core.designsystem.component.WaiveraErrorState
import com.waivera.waivera.core.designsystem.component.WaiveraIconButton
import com.waivera.waivera.core.designsystem.component.WaiveraIconTile
import com.waivera.waivera.core.designsystem.component.WaiveraInsetDivider
import com.waivera.waivera.core.designsystem.component.WaiveraPressable
import com.waivera.waivera.core.designsystem.component.WaiveraSectionHeader
import com.waivera.waivera.core.designsystem.component.WaiveraTileTone
import com.waivera.waivera.core.designsystem.icon.WaiveraIcons
import com.waivera.waivera.core.designsystem.theme.WaiveraSizes
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.core.designsystem.theme.isLargeFontScale
import com.waivera.waivera.feature.tasks.KindTile
import com.waivera.waivera.feature.tasks.Loadable
import com.waivera.waivera.feature.tasks.R

internal data class UploadActions(
    val onUploadRequired: (Int) -> Unit = {},
    val onOpenForm: () -> Unit = {},
    val onRetryRequired: () -> Unit = {},
    val onAddFile: () -> Unit = {},
    val onRetryUpload: (String) -> Unit = {},
    val onDismissUpload: (String) -> Unit = {},
)

/**
 * The files the task still needs (sprout_web "Required files"). Each takes one upload; a form is
 * filled on the web. Hidden when nothing is missing, or while the list hasn't loaded.
 */
@Composable
internal fun RequiredFilesSection(required: Loadable<List<RequiredRow>>, canUpload: Boolean, actions: UploadActions) {
    if (required is Loadable.Loading) return
    val rows = (required as? Loadable.Ready)?.value.orEmpty()
    if (required is Loadable.Ready && rows.isEmpty()) return
    val missing = rows.count { it.upload?.state != UploadState.Done }
    WaiveraSectionHeader(
        title = stringResource(R.string.feature_tasks_required_files),
        count = missing.takeIf { it > 0 },
        countDescription = pluralStringResource(R.plurals.feature_tasks_required_count, missing, missing),
        countTone = WaiveraCountTone.Attention,
        modifier = Modifier.padding(
            start = WaiveraSpacing.Gutter + WaiveraSpacing.ExtraSmall,
            end = WaiveraSpacing.Gutter,
            top = WaiveraSpacing.MediumLarge,
            bottom = WaiveraSpacing.Small,
        ),
    )
    WaiveraCardGroup {
        if (required is Loadable.Failed) {
            WaiveraErrorState(
                message = stringResource(R.string.feature_tasks_required_error),
                onRetry = actions.onRetryRequired,
                dense = true,
            )
        } else {
            rows.forEachIndexed { index, row ->
                if (index > 0) WaiveraInsetDivider(inset = WaiveraSpacing.Gutter + WaiveraSizes.FieldDividerInset)
                RequiredRowItem(row, canUpload, actions)
            }
        }
    }
}

@Composable
private fun RequiredRowItem(row: RequiredRow, canUpload: Boolean, actions: UploadActions) {
    val colors = WaiveraTheme.colors
    val upload = row.upload
    val note = when {
        row.isForm -> NoteText(stringResource(R.string.feature_tasks_required_form), colors.textSecondary)
        upload != null -> uploadNote(upload)
        row.removalPending -> NoteText(stringResource(R.string.feature_tasks_required_removal), colors.textSecondary)
        else -> NoteText(stringResource(R.string.feature_tasks_required_needed), colors.warningText)
    }
    val showsButton = canUpload && !row.isForm && (upload == null || upload.problem?.canRetry == false)
    // At large text the button sits under the name, so the name keeps the row's width.
    val stacked = isLargeFontScale()
    val body: @Composable () -> Unit = {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = WaiveraSizes.ListRowMinHeight)
                .padding(horizontal = WaiveraSpacing.Gutter, vertical = WaiveraSpacing.MediumSmall),
            horizontalArrangement = Arrangement.spacedBy(WaiveraSizes.FieldTileGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WaiveraIconTile(
                icon = if (row.isForm) R.drawable.feature_tasks_ic_task else WaiveraIcons.Attachment,
                tone = if (row.isForm) WaiveraTileTone.Neutral else WaiveraTileTone.Amber,
                size = WaiveraSizes.FieldTile,
                iconSize = WaiveraSizes.FieldTileGlyph,
                modifier = Modifier.align(Alignment.Top),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.XxSmall)) {
                Text(text = row.name, style = MaterialTheme.typography.titleSmall, color = colors.text)
                Text(text = note.text, style = MaterialTheme.typography.bodySmall, color = note.color)
                upload?.let { UploadProgress(it) }
                upload?.takeIf { it.problem?.canRetry == true }?.let {
                    RetryDismiss(it.id, actions)
                }
                if (showsButton && stacked) UploadButton(row, actions)
            }
            when {
                row.isForm -> Icon(
                    painter = painterResource(WaiveraIcons.OpenExternal),
                    contentDescription = null,
                    tint = colors.link,
                    modifier = Modifier.size(WaiveraSizes.IconSmall),
                )
                showsButton && !stacked -> UploadButton(row, actions)
            }
        }
    }
    if (row.isForm) {
        WaiveraPressable(
            onClick = actions.onOpenForm,
            onClickLabel = stringResource(R.string.feature_tasks_file_open_web),
            role = Role.Button,
        ) { body() }
    } else {
        body()
    }
}

@Composable
private fun UploadButton(row: RequiredRow, actions: UploadActions) {
    val label = stringResource(R.string.feature_tasks_upload_for, row.name)
    WaiveraButton(
        text = stringResource(R.string.feature_tasks_upload),
        onClick = { actions.onUploadRequired(row.id) },
        leadingIcon = WaiveraIcons.Upload,
        size = WaiveraButtonSize.Compact,
        variant = WaiveraButtonVariant.Tonal,
        modifier = Modifier.semantics { contentDescription = label },
    )
}

/** Files going up, above the task's files: progress, then a short "Uploaded", or why it failed. */
@Composable
internal fun UploadRows(uploads: List<UploadRow>, actions: UploadActions) {
    uploads.forEachIndexed { index, row ->
        if (index > 0) WaiveraInsetDivider(inset = WaiveraSpacing.Gutter + WaiveraSizes.FieldDividerInset)
        UploadRowItem(row, actions)
    }
}

@Composable
private fun UploadRowItem(row: UploadRow, actions: UploadActions) {
    val colors = WaiveraTheme.colors
    val note = uploadNote(row)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = WaiveraSizes.ListRowMinHeight)
            .padding(horizontal = WaiveraSpacing.Gutter, vertical = WaiveraSpacing.MediumSmall),
        horizontalArrangement = Arrangement.spacedBy(WaiveraSizes.FieldTileGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KindTile(row.kind, Modifier.align(Alignment.Top))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.XxSmall)) {
            Text(
                text = row.name,
                style = MaterialTheme.typography.titleSmall,
                color = colors.text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(text = note.text, style = MaterialTheme.typography.bodySmall, color = note.color)
            UploadProgress(row)
            if (row.problem?.canRetry == true) RetryDismiss(row.id, actions)
        }
        if (row.state == UploadState.Failed && row.problem?.canRetry != true) {
            WaiveraIconButton(
                icon = WaiveraIcons.Close,
                contentDescription = stringResource(R.string.feature_tasks_upload_dismiss, row.name),
                onClick = { actions.onDismissUpload(row.id) },
            )
        }
    }
}

@Composable
private fun RetryDismiss(id: String, actions: UploadActions) {
    Row(horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.Small)) {
        WaiveraButton(
            text = stringResource(R.string.feature_tasks_upload_retry),
            onClick = { actions.onRetryUpload(id) },
            leadingIcon = WaiveraIcons.Refresh,
            size = WaiveraButtonSize.Compact,
            variant = WaiveraButtonVariant.Tonal,
        )
        WaiveraButton(
            text = stringResource(R.string.feature_tasks_upload_remove),
            onClick = { actions.onDismissUpload(id) },
            size = WaiveraButtonSize.Compact,
            variant = WaiveraButtonVariant.Text,
        )
    }
}

@Composable
private fun UploadProgress(row: UploadRow) {
    if (row.state != UploadState.Uploading && row.state != UploadState.Queued) return
    val modifier = Modifier.fillMaxWidth().padding(top = WaiveraSpacing.XxSmall)
    val progress = row.progress
    if (progress == null) {
        LinearProgressIndicator(modifier = modifier, color = WaiveraTheme.colors.accent)
    } else {
        LinearProgressIndicator(progress = { progress }, modifier = modifier, color = WaiveraTheme.colors.accent)
    }
}

/** Add a file, under the task's files, with the web's limits spelled out. */
@Composable
internal fun AddFileFooter(onAdd: () -> Unit) {
    Column(
        modifier = Modifier.padding(
            start = WaiveraSpacing.Gutter,
            end = WaiveraSpacing.Gutter,
            top = WaiveraSpacing.MediumSmall,
        ),
        verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.Small),
    ) {
        WaiveraButton(
            text = stringResource(R.string.feature_tasks_add_file),
            onClick = onAdd,
            leadingIcon = WaiveraIcons.Add,
            variant = WaiveraButtonVariant.Secondary,
            fullWidth = true,
        )
        Text(
            text = stringResource(R.string.feature_tasks_upload_limits),
            style = MaterialTheme.typography.bodySmall,
            color = WaiveraTheme.colors.textSecondary,
        )
    }
}

private data class NoteText(val text: String, val color: androidx.compose.ui.graphics.Color)

@Composable
private fun uploadNote(row: UploadRow): NoteText {
    val colors = WaiveraTheme.colors
    val size = Formatter.formatShortFileSize(LocalContext.current, row.sizeBytes)
    return when (row.state) {
        UploadState.Queued -> NoteText(stringResource(R.string.feature_tasks_upload_waiting), colors.textSecondary)
        UploadState.Uploading -> NoteText(
            row.progress?.let {
                stringResource(R.string.feature_tasks_upload_progress, (it * PERCENT).toInt(), size)
            } ?: stringResource(R.string.feature_tasks_upload_starting),
            colors.textSecondary,
        )
        UploadState.Done -> NoteText(stringResource(R.string.feature_tasks_upload_done), colors.successText)
        UploadState.Failed -> NoteText(
            stringResource(R.string.feature_tasks_upload_failed, stringResource(row.problem.text())),
            colors.dangerText,
        )
    }
}

@StringRes
internal fun UploadProblem?.text(): Int = when (this) {
    UploadProblem.Empty -> R.string.feature_tasks_upload_problem_empty
    UploadProblem.TooLarge -> R.string.feature_tasks_upload_problem_too_large
    UploadProblem.Unsupported, UploadProblem.Mismatch -> R.string.feature_tasks_upload_problem_type
    UploadProblem.PdfLocked -> R.string.feature_tasks_upload_problem_pdf_locked
    UploadProblem.PdfUnreadable -> R.string.feature_tasks_upload_problem_pdf_unreadable
    UploadProblem.AlreadyFilled -> R.string.feature_tasks_upload_problem_already
    UploadProblem.TaskLocked -> R.string.feature_tasks_upload_problem_locked
    UploadProblem.NoAccess -> R.string.feature_tasks_upload_problem_access
    UploadProblem.Unreadable -> R.string.feature_tasks_upload_problem_unreadable
    UploadProblem.Failed, null -> R.string.feature_tasks_upload_problem_failed
}

@DrawableRes
internal fun sourceIcon(source: UploadSource): Int = when (source) {
    UploadSource.Camera -> WaiveraIcons.Camera
    UploadSource.Photos -> WaiveraIcons.Photo
    UploadSource.Files -> WaiveraIcons.Folder
}

/** Where a picked file comes from. Scanning is left out: it needs Google Play's document scanner. */
enum class UploadSource { Camera, Photos, Files }

private const val PERCENT = 100
