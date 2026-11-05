package com.waivera.waivera.feature.tasks.review

import android.icu.text.ListFormatter
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.waivera.waivera.core.data.model.RelatedTask
import com.waivera.waivera.core.data.model.TaskState
import com.waivera.waivera.core.designsystem.component.ConfirmationContent
import com.waivera.waivera.core.designsystem.component.WaiveraBottomSheet
import com.waivera.waivera.core.designsystem.component.WaiveraButton
import com.waivera.waivera.core.designsystem.component.WaiveraButtonSize
import com.waivera.waivera.core.designsystem.component.WaiveraButtonVariant
import com.waivera.waivera.core.designsystem.component.WaiveraIconTile
import com.waivera.waivera.core.designsystem.component.WaiveraTileTone
import com.waivera.waivera.core.designsystem.icon.WaiveraIcons
import com.waivera.waivera.core.designsystem.theme.WaiveraShapes
import com.waivera.waivera.core.designsystem.theme.WaiveraSizes
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.feature.tasks.InfoDivider
import com.waivera.waivera.feature.tasks.InfoNote
import com.waivera.waivera.feature.tasks.InfoRow
import com.waivera.waivera.feature.tasks.InfoValue
import com.waivera.waivera.feature.tasks.R
import com.waivera.waivera.feature.tasks.SheetGroup
import com.waivera.waivera.feature.tasks.TaskFormat
import com.waivera.waivera.feature.tasks.label
import java.time.LocalDate

/** Every answer a review sheet can give; the screen wires them to [TaskReviewViewModel]. */
internal data class ReviewSheetActions(
    val onDismiss: () -> Unit = {},
    val onConfirmAll: () -> Unit = {},
    val onChooseScope: (all: Boolean) -> Unit = {},
    val onDiscardDrafts: () -> Unit = {},
    val onChooseDrive: (Boolean) -> Unit = {},
    val onConfirmDrive: () -> Unit = {},
    val onCompleteAnyway: () -> Unit = {},
    val onOpenRelated: (Int) -> Unit = {},
    val closure: ClosureActions = ClosureActions(),
    val requirements: RequirementActions = RequirementActions(),
    val late: LateActions = LateActions(),
    val sendBack: SendBackActions = SendBackActions(),
)

/** The open step's sheet, one at a time. */
@Composable
internal fun TaskReviewSheets(state: ReviewUiState, today: LocalDate, actions: ReviewSheetActions) {
    val step = state.step ?: return
    val action = state.running ?: ReviewAction.Approve
    when (step) {
        is ReviewStep.ConfirmAll -> WaiveraBottomSheet(onDismissRequest = actions.onDismiss) {
            ConfirmAllContent(step, actions)
        }
        is ReviewStep.Drafts -> WaiveraBottomSheet(onDismissRequest = actions.onDismiss) {
            DraftsContent(step, actions)
        }
        is ReviewStep.ChooseScope -> WaiveraBottomSheet(
            onDismissRequest = actions.onDismiss,
            title = stringResource(
                if (step.submit) {
                    R.string.feature_tasks_review_scope_submit_title
                } else {
                    R.string.feature_tasks_review_scope_title
                },
            ),
        ) { ChooseScopeContent(step, actions.onChooseScope) }
        is ReviewStep.DriveSection -> WaiveraBottomSheet(
            onDismissRequest = actions.onDismiss,
            title = stringResource(R.string.feature_tasks_review_drive_title),
        ) { DriveContent(step, today, actions) }
        is ReviewStep.Related -> WaiveraBottomSheet(
            onDismissRequest = actions.onDismiss,
            title = stringResource(
                if (step.blocked) {
                    R.string.feature_tasks_review_blocked_title
                } else {
                    R.string.feature_tasks_review_warning_title
                },
            ),
        ) { RelatedContent(step, actions) }
        is ReviewStep.Closure -> ClosureSheet(step.form, today, actions.onDismiss, actions.closure)
        is ReviewStep.Requirements -> RequirementsSheet(
            step.form,
            action,
            today,
            actions.onDismiss,
            actions.requirements,
        )
        is ReviewStep.LateReason -> LateReasonSheet(step.form, today, actions.onDismiss, actions.late)
        is ReviewStep.LateReview -> LateReviewSheet(step.check, actions.onDismiss, actions.late)
        is ReviewStep.SendBack -> SendBackSheet(step.form, actions.onDismiss, actions.sendBack)
    }
}

@Composable
internal fun ConfirmAllContent(step: ReviewStep.ConfirmAll, actions: ReviewSheetActions) {
    ConfirmationContent(
        title = pluralStringResource(
            if (step.submit) {
                R.plurals.feature_tasks_review_submit_all_title
            } else {
                R.plurals.feature_tasks_review_all_title
            },
            step.stages,
            step.stages,
        ),
        message = stringResource(R.string.feature_tasks_review_all_message),
        confirmLabel = stringResource(
            if (step.submit) R.string.feature_tasks_review_submit_all else R.string.feature_tasks_review_all_confirm,
        ),
        dismissLabel = stringResource(R.string.feature_tasks_review_go_back),
        destructive = false,
        confirmInProgress = false,
        onConfirm = actions.onConfirmAll,
        onDismiss = actions.onDismiss,
        icon = WaiveraIcons.Check,
    )
}

@Composable
internal fun DraftsContent(step: ReviewStep.Drafts, actions: ReviewSheetActions) {
    val count = step.names.size.coerceAtLeast(1)
    ConfirmationContent(
        title = pluralStringResource(R.plurals.feature_tasks_review_drafts_title, count, count),
        message = if (step.names.isEmpty()) {
            stringResource(R.string.feature_tasks_review_drafts_message)
        } else {
            val resources = LocalResources.current
            // The platform's list wording ("A, B and C") for the device language, not a hard-coded comma.
            val names = remember(step.names, resources) {
                ListFormatter.getInstance().format(
                    step.names.map {
                        resources.getString(R.string.feature_tasks_review_quoted, it)
                    },
                )
            }
            pluralStringResource(R.plurals.feature_tasks_review_drafts_named, step.names.size, names)
        },
        confirmLabel = stringResource(R.string.feature_tasks_review_drafts_confirm),
        dismissLabel = stringResource(R.string.feature_tasks_review_go_back),
        destructive = true,
        confirmInProgress = false,
        onConfirm = actions.onDiscardDrafts,
        onDismiss = actions.onDismiss,
    )
}

@Composable
internal fun ColumnScope.DriveContent(step: ReviewStep.DriveSection, today: LocalDate, actions: ReviewSheetActions) {
    Column(verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.Small)) {
        ChoiceRow(
            title = stringResource(R.string.feature_tasks_review_drive_original),
            detail = stringResource(
                R.string.feature_tasks_review_drive_original_detail,
                TaskFormat.monthYear(step.previous),
            ),
            selected = step.useOriginal,
            onClick = { actions.onChooseDrive(true) },
        )
        ChoiceRow(
            title = stringResource(R.string.feature_tasks_review_drive_latest),
            detail = TaskFormat.monthYear(today),
            selected = !step.useOriginal,
            onClick = { actions.onChooseDrive(false) },
        )
    }
    WaiveraButton(
        text = stringResource(R.string.feature_tasks_review_continue),
        onClick = actions.onConfirmDrive,
        fullWidth = true,
        size = WaiveraButtonSize.Large,
    )
}

@Composable
internal fun ColumnScope.RelatedContent(step: ReviewStep.Related, actions: ReviewSheetActions) {
    Column(
        modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.MediumSmall),
    ) {
        Text(
            text = stringResource(
                if (step.blocked) {
                    R.string.feature_tasks_review_blocked_message
                } else {
                    R.string.feature_tasks_review_warning_message
                },
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = WaiveraTheme.colors.textSecondary,
        )
        SheetGroup {
            step.related.forEachIndexed { index, task ->
                if (index > 0) InfoDivider()
                RelatedRow(task, step.blocked, onOpen = { actions.onOpenRelated(task.id) })
            }
        }
    }
    if (step.blocked) {
        WaiveraButton(
            text = stringResource(R.string.feature_tasks_review_open_related),
            onClick = { step.related.firstOrNull()?.let { actions.onOpenRelated(it.id) } },
            fullWidth = true,
            size = WaiveraButtonSize.Large,
            enabled = step.related.isNotEmpty(),
        )
        WaiveraButton(
            text = stringResource(R.string.feature_tasks_review_close),
            onClick = actions.onDismiss,
            variant = WaiveraButtonVariant.Secondary,
            fullWidth = true,
            size = WaiveraButtonSize.Large,
        )
    } else {
        WaiveraButton(
            text = stringResource(R.string.feature_tasks_review_complete_anyway),
            onClick = actions.onCompleteAnyway,
            fullWidth = true,
            size = WaiveraButtonSize.Large,
            loadingText = stringResource(R.string.feature_tasks_review_sending).takeIf { step.isSaving },
        )
        WaiveraButton(
            text = stringResource(R.string.feature_tasks_review_go_back),
            onClick = actions.onDismiss,
            variant = WaiveraButtonVariant.Secondary,
            fullWidth = true,
            size = WaiveraButtonSize.Large,
            enabled = !step.isSaving,
        )
    }
}

@Composable
private fun RelatedRow(task: RelatedTask, blocked: Boolean, onOpen: () -> Unit) {
    InfoRow(
        label = task.relationship ?: stringResource(
            if (blocked) R.string.feature_tasks_review_blocked_row else R.string.feature_tasks_review_warning_row,
        ),
        icon = R.drawable.feature_tasks_ic_task,
        tone = if (blocked) WaiveraTileTone.Danger else WaiveraTileTone.Amber,
        onClick = onOpen,
    ) {
        InfoValue(task.title.ifEmpty { task.key })
        val status = task.statusName?.let { TaskState.of(it).label() }
        InfoNote(listOfNotNull(task.key.takeIf(String::isNotEmpty), status).joinToString(" · "))
    }
}

/** A choice card (mockup C6/C7): the chosen one has the accent wash, a stronger edge and a check. */
@Composable
@Suppress("LongParameterList") // A card's parts, each shown as given.
internal fun ChoiceRow(
    title: String,
    detail: String?,
    selected: Boolean,
    onClick: () -> Unit,
    @DrawableRes icon: Int = R.drawable.feature_tasks_ic_folder,
    enabled: Boolean = true,
) {
    val colors = WaiveraTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = WaiveraSizes.ListRowMinHeight)
            .clip(WaiveraShapes.SheetCard)
            .background(if (selected) colors.accentWash else colors.surface)
            .border(
                if (selected) WaiveraSizes.FieldBorder else WaiveraSpacing.Hairline,
                if (selected) colors.accentBorderStrong else colors.border,
                WaiveraShapes.SheetCard,
            )
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(WaiveraSpacing.MediumSmall),
        horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.MediumSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WaiveraIconTile(
            icon = icon,
            tone = if (enabled) WaiveraTileTone.Brand else WaiveraTileTone.Neutral,
            size = WaiveraSizes.FieldTile,
            iconSize = WaiveraSizes.FieldTileGlyph,
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.XxSmall)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = if (enabled) colors.text else colors.textSecondary,
            )
            detail?.let { InfoNote(it) }
        }
        if (selected) {
            Icon(painter = painterResource(WaiveraIcons.Check), contentDescription = null, tint = colors.accent)
        }
    }
}
