package com.waivera.waivera.feature.tasks.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import com.waivera.waivera.core.data.model.ClosurePdf
import com.waivera.waivera.core.data.model.FieldType
import com.waivera.waivera.core.data.model.RequirementField
import com.waivera.waivera.core.data.model.StageRequirement
import com.waivera.waivera.core.designsystem.component.BannerTone
import com.waivera.waivera.core.designsystem.component.WaiveraBanner
import com.waivera.waivera.core.designsystem.component.WaiveraBottomSheet
import com.waivera.waivera.core.designsystem.component.WaiveraButton
import com.waivera.waivera.core.designsystem.component.WaiveraButtonSize
import com.waivera.waivera.core.designsystem.component.WaiveraButtonVariant
import com.waivera.waivera.core.designsystem.component.WaiveraDatePickerDialog
import com.waivera.waivera.core.designsystem.component.WaiveraIconButton
import com.waivera.waivera.core.designsystem.component.WaiveraPickerField
import com.waivera.waivera.core.designsystem.component.WaiveraTextField
import com.waivera.waivera.core.designsystem.icon.WaiveraIcons
import com.waivera.waivera.core.designsystem.theme.WaiveraSizes
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTextStyles
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.feature.tasks.InfoDivider
import com.waivera.waivera.feature.tasks.InfoNote
import com.waivera.waivera.feature.tasks.R
import com.waivera.waivera.feature.tasks.SheetGroup
import com.waivera.waivera.feature.tasks.TaskFormat
import java.time.LocalDate

internal data class ClosureActions(
    val onMerge: (Boolean) -> Unit = {},
    val onToggle: (String) -> Unit = {},
    val onMove: (String, Boolean) -> Unit = { _, _ -> },
    val onExpiration: (LocalDate) -> Unit = {},
    val onSave: () -> Unit = {},
)

internal data class RequirementActions(
    val onValue: (actionId: Int, key: String, value: String) -> Unit = { _, _, _ -> },
    val onSubmit: () -> Unit = {},
    val onOpenWeb: () -> Unit = {},
)

/** How an employee task's files are finalized: close each, or merge PDFs in a chosen order. */
@Composable
internal fun ClosureSheet(form: ClosureForm, today: LocalDate, onDismiss: () -> Unit, actions: ClosureActions) {
    WaiveraBottomSheet(
        onDismissRequest = onDismiss,
        title = stringResource(
            if (form.options.needsExpiration) {
                R.string.feature_tasks_review_closure_insurance
            } else {
                R.string.feature_tasks_review_closure_title
            },
        ),
    ) { ClosureFields(form, today, actions) }
}

@Composable
internal fun ClosureFields(form: ClosureForm, today: LocalDate, actions: ClosureActions) {
    var picking by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.MediumSmall)) {
        Column(
            modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.MediumSmall),
        ) {
            if (form.failed) {
                WaiveraBanner(
                    message = stringResource(R.string.feature_tasks_review_closure_failed),
                    tone = BannerTone.Error,
                    gutter = false,
                )
            }
            if (form.options.canMerge) {
                // Choice cards, not a segmented control: "Close & merge PDFs" must stay whole at 200% font.
                Column(verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.Small)) {
                    ChoiceRow(
                        title = stringResource(R.string.feature_tasks_review_closure_close),
                        detail = stringResource(R.string.feature_tasks_review_closure_close_detail),
                        selected = !form.merge,
                        onClick = { actions.onMerge(false) },
                        enabled = !form.isSaving,
                    )
                    ChoiceRow(
                        title = stringResource(R.string.feature_tasks_review_closure_merge),
                        detail = stringResource(R.string.feature_tasks_review_closure_merge_detail),
                        selected = form.merge,
                        onClick = { actions.onMerge(true) },
                        enabled = !form.isSaving,
                    )
                }
            }
            if (form.merge) MergeOrder(form, actions)
            if (form.options.needsExpiration) {
                WaiveraPickerField(
                    label = stringResource(R.string.feature_tasks_review_closure_expiration),
                    value = form.expiration?.let { TaskFormat.date(it, today) },
                    placeholder = stringResource(R.string.feature_tasks_review_pick_date),
                    icon = WaiveraIcons.Calendar,
                    errorText = stringResource(R.string.feature_tasks_review_closure_expiration_required).takeIf {
                        form.showErrors && form.expirationMissing
                    },
                    enabled = !form.isSaving,
                    onClick = { picking = true },
                )
                InfoNote(stringResource(R.string.feature_tasks_review_closure_expiration_note))
            }
        }
        WaiveraButton(
            text = stringResource(R.string.feature_tasks_review_continue),
            onClick = actions.onSave,
            fullWidth = true,
            size = WaiveraButtonSize.Large,
            loadingText = stringResource(R.string.feature_tasks_review_sending).takeIf { form.isSaving },
        )
    }
    if (picking) {
        WaiveraDatePickerDialog(
            initial = form.expiration ?: today,
            onPicked = {
                actions.onExpiration(it)
                picking = false
            },
            onDismiss = { picking = false },
        )
    }
}

@Composable
private fun MergeOrder(form: ClosureForm, actions: ClosureActions) {
    val byId = form.options.pdfs.associateBy { it.id }
    Text(
        text = stringResource(R.string.feature_tasks_review_closure_order),
        style = MaterialTheme.typography.labelMedium,
        color = WaiveraTheme.colors.textSecondary,
    )
    SheetGroup {
        form.order.forEachIndexed { index, id ->
            val pdf = byId[id] ?: return@forEachIndexed
            if (index > 0) InfoDivider()
            PdfRow(
                pdf = pdf,
                picked = id in form.picked,
                first = index == 0,
                last = index == form.order.lastIndex,
                enabled = !form.isSaving,
                actions = actions,
            )
        }
    }
    if (form.tooFewPicked) {
        Text(
            text = stringResource(R.string.feature_tasks_review_closure_too_few),
            style = MaterialTheme.typography.bodySmall,
            color = if (form.showErrors) WaiveraTheme.colors.dangerText else WaiveraTheme.colors.textSecondary,
        )
    }
}

@Composable
@Suppress("LongParameterList") // The row's place in the order decides which moves it offers.
private fun PdfRow(
    pdf: ClosurePdf,
    picked: Boolean,
    first: Boolean,
    last: Boolean,
    enabled: Boolean,
    actions: ClosureActions,
) {
    val colors = WaiveraTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = WaiveraSizes.ListRowMinHeight)
            .padding(start = WaiveraSpacing.ExtraSmall, end = WaiveraSpacing.XxSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = WaiveraSpacing.MinTouchTarget)
                .toggleable(value = picked, enabled = enabled, role = Role.Checkbox, onValueChange = {
                    actions.onToggle(pdf.id)
                }),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = picked,
                onCheckedChange = null,
                enabled = enabled,
                colors = CheckboxDefaults.colors(checkedColor = colors.accent, checkmarkColor = colors.onAccent),
                modifier = Modifier.padding(horizontal = WaiveraSpacing.Small),
            )
            Text(
                text = pdf.name,
                style = WaiveraTextStyles.FieldValue,
                color = colors.text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        WaiveraIconButton(
            icon = WaiveraIcons.ArrowUp,
            contentDescription = stringResource(R.string.feature_tasks_review_closure_up, pdf.name),
            onClick = { actions.onMove(pdf.id, true) },
            enabled = enabled && !first,
            tint = colors.textSecondary,
        )
        WaiveraIconButton(
            icon = WaiveraIcons.ArrowDown,
            contentDescription = stringResource(R.string.feature_tasks_review_closure_down, pdf.name),
            onClick = { actions.onMove(pdf.id, false) },
            enabled = enabled && !last,
            tint = colors.textSecondary,
        )
    }
}

/** The stage's own questions, such as new plan dates (web `WorkflowApprovalRequirementsModal`). */
@Composable
internal fun RequirementsSheet(
    form: RequirementsForm,
    action: ReviewAction,
    today: LocalDate,
    onDismiss: () -> Unit,
    actions: RequirementActions,
) {
    WaiveraBottomSheet(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.feature_tasks_review_requirements_title),
    ) { RequirementFields(form, action, today, actions) }
}

@Composable
internal fun RequirementFields(
    form: RequirementsForm,
    action: ReviewAction,
    today: LocalDate,
    actions: RequirementActions,
) {
    Column(verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.MediumSmall)) {
        Column(
            modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.MediumSmall),
        ) {
            val banner = when {
                form.failed -> R.string.feature_tasks_review_requirements_failed to BannerTone.Error
                form.changed -> R.string.feature_tasks_review_requirements_changed to BannerTone.Warning
                !form.supported -> R.string.feature_tasks_review_requirements_web to BannerTone.Info
                form.confirmWarnings && form.hasWarnings ->
                    R.string.feature_tasks_review_requirements_warnings to BannerTone.Warning
                else -> null
            }
            banner?.let { (text, tone) -> WaiveraBanner(message = stringResource(text), tone = tone, gutter = false) }
            form.requirements.forEach { requirement ->
                RequirementBlock(requirement, form, today, actions)
            }
        }
        if (form.supported) {
            WaiveraButton(
                text = when {
                    form.confirmWarnings && form.hasWarnings -> stringResource(
                        R.string.feature_tasks_review_requirements_anyway,
                    )
                    action == ReviewAction.Submit || action == ReviewAction.SubmitAll ->
                        stringResource(R.string.feature_tasks_review_submit)
                    else -> stringResource(R.string.feature_tasks_review_approve)
                },
                onClick = actions.onSubmit,
                leadingIcon = WaiveraIcons.Check,
                fullWidth = true,
                size = WaiveraButtonSize.Large,
                loadingText = stringResource(R.string.feature_tasks_review_sending).takeIf { form.isSaving },
            )
        } else {
            WaiveraButton(
                text = stringResource(R.string.feature_tasks_review_open_web),
                onClick = actions.onOpenWeb,
                leadingIcon = WaiveraIcons.OpenExternal,
                variant = WaiveraButtonVariant.Secondary,
                fullWidth = true,
                size = WaiveraButtonSize.Large,
            )
        }
    }
}

@Composable
private fun RequirementBlock(
    requirement: StageRequirement,
    form: RequirementsForm,
    today: LocalDate,
    actions: RequirementActions,
) {
    val colors = WaiveraTheme.colors
    if (requirement.title.isNotEmpty() || requirement.description != null) {
        Column(verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.XxSmall)) {
            if (requirement.title.isNotEmpty()) {
                Text(requirement.title, style = MaterialTheme.typography.titleSmall, color = colors.text)
            }
            requirement.description?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
            }
        }
    }
    val values = form.values[requirement.actionId].orEmpty()
    requirement.fields.filter { it.type != FieldType.Unsupported }.forEach { field ->
        RequirementInput(
            field = field,
            value = values[field.key].orEmpty(),
            error = form.errors[requirement.actionId]?.get(field.key),
            warning = form.warnings[requirement.actionId]?.get(field.key),
            enabled = !form.isSaving,
            today = today,
            onValue = { actions.onValue(requirement.actionId, field.key, it) },
        )
    }
}

@Composable
@Suppress("LongParameterList") // One field, its value and both kinds of message.
private fun RequirementInput(
    field: RequirementField,
    value: String,
    error: RequirementIssue?,
    warning: RequirementIssue?,
    enabled: Boolean,
    today: LocalDate,
    onValue: (String) -> Unit,
) {
    val message = (error ?: warning)?.let { issueText(it, field.label) }
    when (field.type) {
        FieldType.Date -> {
            var picking by remember { mutableStateOf(false) }
            val date = RequirementChecks.date(value)
            WaiveraPickerField(
                label = field.label,
                value = date?.let { TaskFormat.fullDate(it) } ?: value.takeIf(String::isNotBlank),
                placeholder = stringResource(R.string.feature_tasks_review_pick_date),
                icon = WaiveraIcons.Calendar,
                errorText = message.takeIf { error != null },
                enabled = enabled,
                onClick = { picking = true },
            )
            if (error == null && message != null) WarningText(message)
            if (picking) {
                WaiveraDatePickerDialog(
                    initial = date ?: today,
                    onPicked = {
                        onValue(it.toString())
                        picking = false
                    },
                    onDismiss = { picking = false },
                )
            }
        }
        else -> {
            WaiveraTextField(
                value = value,
                onValueChange = onValue,
                label = field.label,
                placeholder = field.label,
                singleLine = field.type != FieldType.LongText,
                minLines = if (field.type == FieldType.LongText) LONG_TEXT_LINES else 1,
                enabled = enabled,
                errorText = message.takeIf { error != null },
                keyboardOptions = if (field.type == FieldType.Number) {
                    KeyboardOptions(keyboardType = KeyboardType.Number)
                } else {
                    KeyboardOptions.Default
                },
            )
            if (error == null && message != null) WarningText(message)
        }
    }
}

@Composable
private fun WarningText(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodySmall, color = WaiveraTheme.colors.warningText)
}

@Composable
private fun issueText(issue: RequirementIssue, label: String): String = when (issue.kind) {
    IssueKind.Required -> stringResource(R.string.feature_tasks_review_issue_required, label)
    IssueKind.InvalidDate -> stringResource(R.string.feature_tasks_review_issue_invalid)
    IssueKind.EndBeforeStart -> stringResource(R.string.feature_tasks_review_issue_end_before_start)
    IssueKind.OverAYearAgo -> stringResource(R.string.feature_tasks_review_issue_year_ago)
    IssueKind.ExpectedStart -> stringResource(
        R.string.feature_tasks_review_issue_expected_start,
        issue.date?.let { TaskFormat.fullDate(it) }.orEmpty(),
    )
    IssueKind.ExpectedEnd -> stringResource(
        R.string.feature_tasks_review_issue_expected_end,
        issue.date?.let { TaskFormat.fullDate(it) }.orEmpty(),
    )
}

private const val LONG_TEXT_LINES = 3
