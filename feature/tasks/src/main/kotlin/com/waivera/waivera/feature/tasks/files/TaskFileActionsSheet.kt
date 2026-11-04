package com.waivera.waivera.feature.tasks.files

import android.text.format.Formatter
import androidx.annotation.DrawableRes
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import com.waivera.waivera.core.data.model.FileKind
import com.waivera.waivera.core.designsystem.component.WaiveraBottomSheet
import com.waivera.waivera.core.designsystem.component.WaiveraBusyIndicator
import com.waivera.waivera.core.designsystem.component.WaiveraIconTile
import com.waivera.waivera.core.designsystem.component.WaiveraPressable
import com.waivera.waivera.core.designsystem.component.WaiveraTileTone
import com.waivera.waivera.core.designsystem.icon.WaiveraIcons
import com.waivera.waivera.core.designsystem.preview.WaiveraPreviews
import com.waivera.waivera.core.designsystem.theme.WaiveraShapes
import com.waivera.waivera.core.designsystem.theme.WaiveraSizes
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTextStyles
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.feature.tasks.InfoDivider
import com.waivera.waivera.feature.tasks.KindTile
import com.waivera.waivera.feature.tasks.R
import com.waivera.waivera.feature.tasks.SheetGroup
import com.waivera.waivera.feature.tasks.label

internal data class FileSheetActions(
    val onDismiss: () -> Unit,
    val onOpen: () -> Unit,
    val onSave: () -> Unit,
    val onShare: () -> Unit,
)

/** What to do with a tapped task file: look at it, keep a copy on the phone, or send it on. */
@Composable
internal fun TaskFileActionsSheet(sheet: FileSheet, actions: FileSheetActions) {
    WaiveraBottomSheet(onDismissRequest = actions.onDismiss) {
        FileSheetContent(sheet, actions)
    }
}

@Composable
internal fun FileSheetContent(sheet: FileSheet, actions: FileSheetActions) {
    Column(verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.MediumSmall)) {
        FileHeader(sheet)
        SheetGroup(Modifier.padding(bottom = WaiveraSpacing.Small)) {
            val idle = sheet.busy == null
            ActionRow(
                icon = if (sheet.viewable) WaiveraIcons.View else WaiveraIcons.OpenExternal,
                tone = WaiveraTileTone.Brand,
                title = stringResource(
                    if (sheet.viewable) R.string.feature_tasks_file_open else R.string.feature_tasks_file_open_web,
                ),
                busy = false,
                enabled = idle,
                onClick = actions.onOpen,
            )
            InfoDivider()
            ActionRow(
                icon = WaiveraIcons.Download,
                tone = WaiveraTileTone.Teal,
                title = stringResource(R.string.feature_tasks_file_save),
                busy = sheet.busy == FileAction.Save,
                enabled = idle,
                onClick = actions.onSave,
            )
            InfoDivider()
            ActionRow(
                icon = WaiveraIcons.Share,
                tone = WaiveraTileTone.Amber,
                title = stringResource(R.string.feature_tasks_file_share),
                busy = sheet.busy == FileAction.Share,
                enabled = idle,
                onClick = actions.onShare,
            )
        }
    }
}

@Composable
private fun FileHeader(sheet: FileSheet) {
    val colors = WaiveraTheme.colors
    val size = Formatter.formatShortFileSize(LocalContext.current, sheet.sizeBytes)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(WaiveraSizes.FieldTileGap),
        verticalAlignment = Alignment.Top,
    ) {
        KindTile(sheet.kind)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.XxSmall)) {
            // A long name wraps instead of being cut, so the person knows which file they picked.
            Text(text = sheet.name, style = MaterialTheme.typography.titleMedium, color = colors.text)
            Text(
                text = listOf(sheet.kind.label(), size).joinToString(META_SEPARATOR),
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ActionRow(
    @DrawableRes icon: Int,
    tone: WaiveraTileTone,
    title: String,
    busy: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val working = stringResource(R.string.feature_tasks_file_preparing)
    WaiveraPressable(
        onClick = onClick,
        enabled = enabled,
        shape = WaiveraShapes.SheetCard,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { if (busy) stateDescription = working },
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
                icon = icon,
                tone = tone,
                size = WaiveraSizes.FieldTile,
                iconSize = WaiveraSizes.FieldTileGlyph,
            )
            Text(
                text = if (busy) working else title,
                style = WaiveraTextStyles.FieldValue,
                color = WaiveraTheme.colors.text,
                modifier = Modifier.weight(1f),
            )
            if (busy) {
                WaiveraBusyIndicator()
            } else {
                Icon(
                    painter = painterResource(WaiveraIcons.ChevronRight),
                    contentDescription = null,
                    tint = WaiveraTheme.colors.textSecondary,
                    modifier = Modifier.size(WaiveraSizes.IconSmall),
                )
            }
        }
    }
}

private const val META_SEPARATOR = " · "

@WaiveraPreviews
@Composable
private fun FileSheetContentPreview() {
    WaiveraTheme {
        Column(Modifier.padding(WaiveraSpacing.Gutter)) {
            FileSheetContent(
                sheet = FileSheet(
                    attachmentId = 1,
                    name = "Signed service plan - Jordan Ellis - 2026.pdf",
                    kind = FileKind.Pdf,
                    sizeBytes = 284_000,
                    viewable = true,
                    busy = FileAction.Save,
                ),
                actions = FileSheetActions({}, {}, {}, {}),
            )
        }
    }
}
