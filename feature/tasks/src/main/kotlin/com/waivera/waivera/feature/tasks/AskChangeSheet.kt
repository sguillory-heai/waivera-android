package com.waivera.waivera.feature.tasks

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import com.waivera.waivera.core.data.model.ChangeReason
import com.waivera.waivera.core.data.model.TaskDetail
import com.waivera.waivera.core.data.model.TaskFile
import com.waivera.waivera.core.designsystem.component.BannerTone
import com.waivera.waivera.core.designsystem.component.WaiveraBanner
import com.waivera.waivera.core.designsystem.component.WaiveraBottomSheet
import com.waivera.waivera.core.designsystem.component.WaiveraButton
import com.waivera.waivera.core.designsystem.component.WaiveraButtonSize
import com.waivera.waivera.core.designsystem.component.WaiveraDatePickerDialog
import com.waivera.waivera.core.designsystem.component.WaiveraErrorState
import com.waivera.waivera.core.designsystem.component.WaiveraIconButton
import com.waivera.waivera.core.designsystem.component.WaiveraIconTile
import com.waivera.waivera.core.designsystem.component.WaiveraListRow
import com.waivera.waivera.core.designsystem.component.WaiveraPickerField
import com.waivera.waivera.core.designsystem.component.WaiveraPressable
import com.waivera.waivera.core.designsystem.component.WaiveraTextField
import com.waivera.waivera.core.designsystem.component.WaiveraTileTone
import com.waivera.waivera.core.designsystem.icon.WaiveraIcons
import com.waivera.waivera.core.designsystem.theme.WaiveraShapes
import com.waivera.waivera.core.designsystem.theme.WaiveraSizes
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTextStyles
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.core.designsystem.theme.swapTransition
import java.time.LocalDate

internal data class AskActions(
    val onChoose: (AskKind) -> Unit = {},
    val onBack: () -> Boolean = { false },
    val onDismiss: () -> Unit = {},
    val onRetryReasons: () -> Unit = {},
    val onRetryFiles: () -> Unit = {},
    val onReason: (Long) -> Unit = {},
    val onDue: (LocalDate) -> Unit = {},
    val onExplanation: (String) -> Unit = {},
    val onEvidence: (Int?) -> Unit = {},
    val onSubmit: () -> Unit = {},
)

/** What the sheet shows on top of the form: a list to pick a reason or a file from. */
private enum class AskStep { Form, Reason, File }

@Composable
internal fun AskChangeSheet(form: AskForm, task: TaskDetail?, today: LocalDate, actions: AskActions) {
    var step by rememberSaveable { mutableStateOf(AskStep.Form) }
    val kind = form.kind
    val title = when {
        step == AskStep.Reason -> stringResource(kind.reasonLabel())
        step == AskStep.File -> stringResource(R.string.feature_tasks_ask_files_title)
        kind == null -> stringResource(R.string.feature_tasks_ask_change)
        else -> stringResource(kind.title())
    }
    WaiveraBottomSheet(
        onDismissRequest = {
            when {
                step != AskStep.Form -> step = AskStep.Form
                !actions.onBack() -> actions.onDismiss()
            }
        },
        title = title,
    ) {
        val motion = WaiveraTheme.motion
        // Deeper steps slide in from the right and back out to it, as pages do.
        AnimatedContent(
            targetState = if (kind == null) -1 else step.ordinal,
            transitionSpec = { motion.swapTransition(direction = targetState - initialState) },
            label = "askStep",
        ) { target ->
            Column(verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.MediumSmall)) {
                when {
                    target == -1 -> AskChoices(form, task, today, actions.onChoose)
                    target == AskStep.Reason.ordinal -> ReasonChoices(form, actions.onRetryReasons) { id ->
                        actions.onReason(id)
                        step = AskStep.Form
                    }
                    target == AskStep.File.ordinal -> FileChoices(form, actions.onRetryFiles) { id ->
                        actions.onEvidence(id)
                        step = AskStep.Form
                    }
                    else -> AskFields(
                        form = form,
                        task = task,
                        today = today,
                        actions = actions,
                        onPickReason = { step = AskStep.Reason },
                        onPickFile = { step = AskStep.File },
                    )
                }
            }
        }
    }
}

@Composable
internal fun ColumnScope.AskChoices(form: AskForm, task: TaskDetail?, today: LocalDate, onChoose: (AskKind) -> Unit) {
    task?.let { TaskContext(it, today) }
    SheetGroup(Modifier.padding(bottom = WaiveraSpacing.Small)) {
        form.choices.forEachIndexed { index, kind ->
            if (index > 0) InfoDivider()
            ChoiceRow(
                icon = kind.icon(),
                tone = if (kind == AskKind.Delay) WaiveraTileTone.Amber else WaiveraTileTone.Brand,
                title = stringResource(kind.choiceTitle()),
                detail = stringResource(kind.choiceDetail()),
                onClick = { onChoose(kind) },
            )
        }
    }
}

@Composable
private fun ChoiceRow(
    @DrawableRes icon: Int,
    tone: WaiveraTileTone,
    title: String,
    detail: String,
    onClick: () -> Unit,
) {
    WaiveraPressable(onClick = onClick, shape = WaiveraShapes.SheetCard, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = WaiveraSpacing.MinTouchTarget)
                .padding(horizontal = WaiveraSpacing.Medium, vertical = WaiveraSpacing.MediumSmall),
            horizontalArrangement = Arrangement.spacedBy(WaiveraSizes.FieldTileGap),
            verticalAlignment = Alignment.Top,
        ) {
            WaiveraIconTile(
                icon = icon,
                tone = tone,
                size = WaiveraSizes.FieldTile,
                iconSize = WaiveraSizes.FieldTileGlyph,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.XxSmall)) {
                Text(title, style = WaiveraTextStyles.FieldValue, color = WaiveraTheme.colors.text)
                InfoNote(detail)
            }
            Icon(
                painter = painterResource(WaiveraIcons.ChevronRight),
                contentDescription = null,
                tint = WaiveraTheme.colors.textSecondary,
                modifier = Modifier.align(Alignment.CenterVertically),
            )
        }
    }
}

@Composable
private fun TaskContext(task: TaskDetail, today: LocalDate) {
    val due = TaskFormat.due(task.due, today)
    SheetSubtitle(
        first = stringResource(R.string.feature_tasks_activity_row, task.key, task.title),
        second = if (due == DueText.None) task.about else due.text(),
    )
}

@Composable
internal fun ColumnScope.AskFields(
    form: AskForm,
    task: TaskDetail?,
    today: LocalDate,
    actions: AskActions,
    onPickReason: () -> Unit,
    onPickFile: () -> Unit,
) {
    val kind = form.kind ?: return
    var pickingDue by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.MediumSmall),
    ) {
        task?.let { TaskContext(it, today) }
        if (form.failed) {
            WaiveraBanner(
                message = stringResource(R.string.feature_tasks_ask_failed),
                tone = BannerTone.Error,
                gutter = false,
            )
        }
        if (form.delayChanged) {
            WaiveraBanner(
                message = stringResource(
                    if (form.delay ==
                        null
                    ) {
                        R.string.feature_tasks_ask_delay_closed
                    } else {
                        R.string.feature_tasks_ask_delay_changed
                    },
                ),
                tone = BannerTone.Warning,
                gutter = false,
            )
        }
        if (kind == AskKind.MoreTime) {
            WaiveraPickerField(
                label = stringResource(R.string.feature_tasks_ask_new_due),
                value = form.newDue?.let { TaskFormat.date(it, today) },
                placeholder = stringResource(R.string.feature_tasks_ask_pick_day),
                icon = WaiveraIcons.Calendar,
                errorText = dueError(form, today),
                enabled = !form.isSaving,
                onClick = { pickingDue = true },
            )
        }
        WaiveraPickerField(
            label = stringResource(kind.reasonLabel()),
            value = form.reason?.name,
            placeholder = stringResource(R.string.feature_tasks_ask_choose_reason),
            icon = WaiveraIcons.ExpandMore,
            errorText = stringResource(R.string.feature_tasks_ask_reason_required).takeIf {
                form.showErrors && form.reasonMissing
            },
            enabled = !form.isSaving,
            onClick = onPickReason,
        )
        if (kind == AskKind.Delay) DelayWindow(form, today)
        ExplanationField(form, kind, actions.onExplanation)
        if (form.needsEvidence) EvidenceField(form, onPickFile, actions)
        kind.note()?.let { InfoNote(stringResource(it)) }
    }
    WaiveraButton(
        text = stringResource(kind.sendLabel(form)),
        onClick = actions.onSubmit,
        fullWidth = true,
        size = WaiveraButtonSize.Large,
        enabled = !form.explanationTooLong && !(form.delayChanged && form.delay == null),
    )
    if (pickingDue) {
        val current = form.currentDue
        WaiveraDatePickerDialog(
            initial = form.newDue ?: (current ?: today).plusDays(1),
            earliest = current?.plusDays(1),
            latest = current?.plusYears(AskForm.MAX_YEARS),
            onPicked = {
                actions.onDue(it)
                pickingDue = false
            },
            onDismiss = { pickingDue = false },
        )
    }
}

@Composable
private fun dueError(form: AskForm, today: LocalDate): String? = when {
    form.showErrors && form.dueMissing -> stringResource(R.string.feature_tasks_ask_due_required)
    form.dueTooEarly -> form.currentDue?.let {
        stringResource(R.string.feature_tasks_ask_due_after, TaskFormat.date(it, today))
    }
    form.dueTooLate -> stringResource(R.string.feature_tasks_ask_due_within)
    else -> null
}

@Composable
private fun DelayWindow(form: AskForm, today: LocalDate) {
    val delay = form.delay ?: return
    Column(verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.ExtraSmall + WaiveraSpacing.XxSmall)) {
        Text(
            text = stringResource(R.string.feature_tasks_ask_delay_window),
            style = MaterialTheme.typography.labelMedium,
            color = WaiveraTheme.colors.textSecondary,
        )
        SheetGroup {
            InfoRow(stringResource(R.string.feature_tasks_ask_delay_starts), WaiveraIcons.Calendar) {
                InfoValue(TaskFormat.date(delay.start, today))
            }
            InfoDivider()
            InfoRow(stringResource(R.string.feature_tasks_ask_delay_through), WaiveraIcons.Schedule) {
                InfoValue(TaskFormat.date(delay.end, today))
            }
        }
        InfoNote(stringResource(R.string.feature_tasks_ask_delay_fixed))
    }
}

@Composable
private fun ExplanationField(form: AskForm, kind: AskKind, onChange: (String) -> Unit) {
    val optional = kind == AskKind.Closure && form.reason?.needsComment != true
    val over = form.explanation.length - AskForm.MAX_EXPLANATION
    val error = when {
        over > 0 -> pluralStringResource(R.plurals.feature_tasks_ask_explanation_over, over, over)
        form.showErrors && form.explanationMissing -> stringResource(
            if (kind ==
                AskKind.Closure
            ) {
                R.string.feature_tasks_ask_comment_required
            } else {
                R.string.feature_tasks_ask_explanation_required
            },
        )
        else -> null
    }
    WaiveraTextField(
        value = form.explanation,
        onValueChange = onChange,
        label = stringResource(
            when {
                kind == AskKind.MoreTime -> R.string.feature_tasks_ask_justification
                kind == AskKind.Delay -> R.string.feature_tasks_ask_explanation
                optional -> R.string.feature_tasks_ask_comments_optional
                else -> R.string.feature_tasks_ask_comments
            },
        ),
        placeholder = stringResource(
            when (kind) {
                AskKind.MoreTime -> R.string.feature_tasks_ask_justification_placeholder
                AskKind.Delay -> R.string.feature_tasks_ask_explanation_placeholder
                AskKind.Closure -> R.string.feature_tasks_ask_comments_placeholder
            },
        ),
        singleLine = false,
        minLines = EXPLANATION_LINES,
        enabled = !form.isSaving,
        errorText = error,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
    )
    // The count shows only near the limit, where it helps.
    if (form.explanation.length > AskForm.MAX_EXPLANATION - COUNT_SHOWS_WITHIN && over <= 0) {
        InfoNote(stringResource(R.string.feature_tasks_ask_explanation_count, "%,d".format(form.explanation.length)))
    }
}

@Composable
private fun EvidenceField(form: AskForm, onPickFile: () -> Unit, actions: AskActions) {
    val files = form.files
    val evidence = form.evidence
    when {
        evidence != null -> AttachmentChip(evidence, onRemove = { actions.onEvidence(null) })
        files is Loadable.Ready && files.value.isEmpty() -> WaiveraBanner(
            message = stringResource(R.string.feature_tasks_ask_no_files),
            tone = BannerTone.Warning,
            gutter = false,
            icon = WaiveraIcons.Attachment,
        )
        files is Loadable.Failed -> WaiveraErrorState(
            message = stringResource(R.string.feature_tasks_ask_files_error),
            onRetry = actions.onRetryFiles,
            dense = true,
        )
        else -> WaiveraPickerField(
            label = stringResource(R.string.feature_tasks_ask_evidence),
            value = null,
            placeholder = stringResource(R.string.feature_tasks_ask_choose_file),
            icon = WaiveraIcons.Attachment,
            errorText = stringResource(R.string.feature_tasks_ask_evidence_required).takeIf {
                form.showErrors && form.evidenceMissing
            },
            enabled = !form.isSaving && files is Loadable.Ready,
            onClick = onPickFile,
        )
    }
}

/** The picked file, in the soft accent, with its own remove button. */
@Composable
private fun AttachmentChip(file: TaskFile, onRemove: () -> Unit) {
    val colors = WaiveraTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.ExtraSmall + WaiveraSpacing.XxSmall)) {
        Text(
            text = stringResource(R.string.feature_tasks_ask_evidence),
            style = MaterialTheme.typography.labelMedium,
            color = colors.textSecondary,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = WaiveraSizes.FieldHeight)
                .background(colors.accentSoft, WaiveraShapes.Field)
                .padding(start = WaiveraSpacing.Medium),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.Small),
        ) {
            Icon(
                painter = painterResource(WaiveraIcons.Attachment),
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(WaiveraSizes.Icon),
            )
            Text(
                text = file.name,
                style = MaterialTheme.typography.bodyLarge,
                color = colors.onAccentSoft,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(vertical = WaiveraSpacing.MediumSmall),
            )
            Box(contentAlignment = Alignment.Center) {
                WaiveraIconButton(
                    icon = WaiveraIcons.Close,
                    contentDescription = stringResource(R.string.feature_tasks_ask_remove_file, file.name),
                    onClick = onRemove,
                    tint = colors.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun ReasonChoices(form: AskForm, onRetry: () -> Unit, onPick: (Long) -> Unit) {
    when (val reasons = form.reasons) {
        Loadable.Loading -> InfoNote(stringResource(R.string.feature_tasks_loading))
        Loadable.Failed -> WaiveraErrorState(
            message = stringResource(R.string.feature_tasks_ask_reasons_error),
            onRetry = onRetry,
            dense = true,
        )
        is Loadable.Ready -> Column(Modifier.verticalScroll(rememberScrollState())) {
            reasons.value.forEach { reason ->
                ReasonRow(reason, selected = reason.id == form.reasonId, onPick = onPick)
            }
        }
    }
}

@Composable
private fun ReasonRow(reason: ChangeReason, selected: Boolean, onPick: (Long) -> Unit) {
    WaiveraListRow(
        headline = reason.name,
        supporting = reason.description?.takeIf { it.isNotBlank() },
        onClick = { onPick(reason.id) },
        trailing = { if (selected) SelectedCheck() },
        modifier = Modifier.semantics { if (selected) stateDescription = reason.name },
    )
}

@Composable
private fun FileChoices(form: AskForm, onRetry: () -> Unit, onPick: (Int) -> Unit) {
    when (val files = form.files) {
        Loadable.Loading -> InfoNote(stringResource(R.string.feature_tasks_loading))
        Loadable.Failed -> WaiveraErrorState(
            message = stringResource(R.string.feature_tasks_ask_files_error),
            onRetry = onRetry,
            dense = true,
        )
        is Loadable.Ready -> Column(Modifier.verticalScroll(rememberScrollState())) {
            files.value.forEach { file ->
                val selected = file.attachmentId == form.evidenceId
                WaiveraListRow(
                    headline = file.name,
                    onClick = { onPick(file.attachmentId) },
                    leading = {
                        WaiveraIconTile(
                            icon = WaiveraIcons.Attachment,
                            size = WaiveraSizes.FieldTile,
                            iconSize = WaiveraSizes.FieldTileGlyph,
                        )
                    },
                    trailing = { if (selected) SelectedCheck() },
                    modifier = Modifier.semantics { if (selected) stateDescription = file.name },
                )
            }
        }
    }
}

@Composable
private fun SelectedCheck() {
    Icon(painter = painterResource(WaiveraIcons.Check), contentDescription = null, tint = WaiveraTheme.colors.accent)
}

private fun AskKind?.reasonLabel(): Int = when (this) {
    AskKind.Delay -> R.string.feature_tasks_ask_delay_reason
    AskKind.Closure -> R.string.feature_tasks_ask_closure_reason
    AskKind.MoreTime, null -> R.string.feature_tasks_ask_reason_code
}

private fun AskKind.title(): Int = when (this) {
    AskKind.MoreTime -> R.string.feature_tasks_ask_more_time_title
    AskKind.Delay -> R.string.feature_tasks_ask_delay
    AskKind.Closure -> R.string.feature_tasks_ask_closure
}

private fun AskKind.choiceTitle(): Int = when (this) {
    AskKind.MoreTime -> R.string.feature_tasks_ask_more_time
    AskKind.Delay -> R.string.feature_tasks_ask_delay
    AskKind.Closure -> R.string.feature_tasks_ask_closure
}

private fun AskKind.choiceDetail(): Int = when (this) {
    AskKind.MoreTime -> R.string.feature_tasks_ask_more_time_detail
    AskKind.Delay -> R.string.feature_tasks_ask_delay_detail
    AskKind.Closure -> R.string.feature_tasks_ask_closure_detail
}

@DrawableRes
private fun AskKind.icon(): Int = when (this) {
    AskKind.MoreTime -> WaiveraIcons.Schedule
    AskKind.Delay -> WaiveraIcons.Warning
    AskKind.Closure -> WaiveraIcons.Archive
}

private fun AskKind.note(): Int? = when (this) {
    AskKind.MoreTime -> R.string.feature_tasks_ask_more_time_note
    AskKind.Closure -> R.string.feature_tasks_ask_closure_note
    AskKind.Delay -> null
}

private fun AskKind.sendLabel(form: AskForm): Int = when {
    this != AskKind.Delay -> R.string.feature_tasks_ask_send
    form.delay?.renewal == true -> R.string.feature_tasks_ask_report_again
    else -> R.string.feature_tasks_ask_report
}

private const val EXPLANATION_LINES = 3
private const val COUNT_SHOWS_WITHIN = 400
