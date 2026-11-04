package com.waivera.waivera.feature.tasks.files

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.waivera.waivera.core.data.model.FileKind
import com.waivera.waivera.core.designsystem.component.WaiveraIconButton
import com.waivera.waivera.core.designsystem.icon.WaiveraIcons
import com.waivera.waivera.core.designsystem.theme.WaiveraSizes
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTextStyles
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.feature.tasks.InfoDivider
import com.waivera.waivera.feature.tasks.KindTile
import com.waivera.waivera.feature.tasks.LogForm
import com.waivera.waivera.feature.tasks.R
import com.waivera.waivera.feature.tasks.SheetGroup

/**
 * The file a Log activity type needs (sprout_web `LogActivityModal`): take a photo, choose one, or
 * choose a file. It is checked as soon as it's picked, shown under the name it is saved as, and
 * uploaded when the activity is logged.
 */
@Composable
internal fun LogActivityFile(form: LogForm, onSource: (UploadSource) -> Unit, onRemove: () -> Unit) {
    val colors = WaiveraTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.ExtraSmall)) {
        Text(
            text = stringResource(R.string.feature_tasks_log_file),
            style = WaiveraTextStyles.FieldLabel,
            color = colors.textSecondary,
        )
        val file = form.file
        val sentAs = form.sentAs
        when {
            file != null || sentAs != null -> SheetGroup {
                PickedFileRow(
                    name = sentAs ?: form.fileSavedName.orEmpty(),
                    kind = file?.kind ?: FileKind.of(sentAs.orEmpty(), null),
                    sizeBytes = file?.sizeBytes,
                    uploaded = sentAs != null,
                    canRemove = !form.isSaving && sentAs == null,
                    onRemove = onRemove,
                )
            }
            form.fileChecking -> SheetGroup {
                Text(
                    text = stringResource(R.string.feature_tasks_log_file_checking),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                    modifier = Modifier
                        .heightIn(min = WaiveraSpacing.MinTouchTarget)
                        .padding(horizontal = WaiveraSpacing.Medium, vertical = WaiveraSpacing.MediumSmall),
                )
            }
            else -> SheetGroup {
                UploadSource.entries.forEachIndexed { index, source ->
                    if (index > 0) InfoDivider()
                    SourceRow(source, enabled = !form.isSaving, single = true, onClick = { onSource(source) })
                }
            }
        }
        val problem = form.fileProblem
        val error = when {
            problem != null && file != null ->
                stringResource(R.string.feature_tasks_log_upload_failed, stringResource(problem.text()))
            problem != null -> stringResource(R.string.feature_tasks_log_file_problem, stringResource(problem.text()))
            form.showErrors && form.fileMissing && !form.fileChecking ->
                stringResource(R.string.feature_tasks_log_file_required)
            else -> null
        }
        Text(
            text = error ?: stringResource(R.string.feature_tasks_upload_limits_one),
            style = MaterialTheme.typography.bodySmall,
            color = if (error != null) colors.dangerText else colors.textSecondary,
        )
    }
}

@Composable
private fun PickedFileRow(
    name: String,
    kind: FileKind,
    sizeBytes: Long?,
    uploaded: Boolean,
    canRemove: Boolean,
    onRemove: () -> Unit,
) {
    val colors = WaiveraTheme.colors
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = WaiveraSizes.ListRowMinHeight)
            .padding(start = WaiveraSpacing.Medium, end = WaiveraSpacing.ExtraSmall)
            .padding(vertical = WaiveraSpacing.MediumSmall),
        horizontalArrangement = Arrangement.spacedBy(WaiveraSizes.FieldTileGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KindTile(kind)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.XxSmall)) {
            Text(
                text = name,
                style = WaiveraTextStyles.FieldValue,
                color = colors.text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = listOfNotNull(
                    stringResource(
                        if (!uploaded) {
                            R.string.feature_tasks_log_file_on_log
                        } else {
                            R.string.feature_tasks_upload_done
                        },
                    ),
                    sizeBytes?.let { Formatter.formatShortFileSize(context, it) },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )
        }
        if (canRemove) {
            WaiveraIconButton(
                icon = WaiveraIcons.Close,
                contentDescription = stringResource(R.string.feature_tasks_log_file_remove, name),
                onClick = onRemove,
                tint = colors.textSecondary,
            )
        }
    }
}
