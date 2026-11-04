package com.waivera.waivera.feature.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import com.waivera.waivera.core.data.model.ActivityType
import com.waivera.waivera.core.data.model.TaskDetail
import com.waivera.waivera.core.designsystem.component.BannerTone
import com.waivera.waivera.core.designsystem.component.WaiveraBanner
import com.waivera.waivera.core.designsystem.component.WaiveraBottomSheet
import com.waivera.waivera.core.designsystem.component.WaiveraButton
import com.waivera.waivera.core.designsystem.component.WaiveraButtonSize
import com.waivera.waivera.core.designsystem.component.WaiveraDatePickerDialog
import com.waivera.waivera.core.designsystem.component.WaiveraErrorState
import com.waivera.waivera.core.designsystem.component.WaiveraIconButton
import com.waivera.waivera.core.designsystem.component.WaiveraListRow
import com.waivera.waivera.core.designsystem.component.WaiveraPickerField
import com.waivera.waivera.core.designsystem.component.WaiveraSheetFrame
import com.waivera.waivera.core.designsystem.component.WaiveraTextField
import com.waivera.waivera.core.designsystem.component.WaiveraTimePickerDialog
import com.waivera.waivera.core.designsystem.icon.WaiveraIcons
import com.waivera.waivera.core.designsystem.preview.WaiveraPreviews
import com.waivera.waivera.core.designsystem.theme.WaiveraShapes
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTextStyles
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.feature.tasks.files.LogActivityFile
import com.waivera.waivera.feature.tasks.files.UploadSource
import com.waivera.waivera.feature.tasks.files.rememberUploadPicker
import java.time.LocalDate

internal data class LogActivityActions(
    val onDismiss: () -> Unit,
    val onRetryTypes: () -> Unit,
    val onChooseType: (Int) -> Unit,
    val onStep: (up: Boolean) -> Unit,
    val onWhen: (LocalDate, minuteOfDay: Int) -> Unit,
    val onDescription: (String) -> Unit,
    val onSubmit: () -> Unit,
    val onFilePicked: (List<String>) -> Unit = {},
    val onRemoveFile: () -> Unit = {},
) {
    companion object {
        val None = LogActivityActions({}, {}, {}, {}, { _, _ -> }, {}, {})
    }
}

@Composable
internal fun LogActivitySheet(form: LogForm, task: TaskDetail?, actions: LogActivityActions) {
    var choosingType by rememberSaveable { mutableStateOf(false) }
    val pickFile = rememberUploadPicker(single = true, onPicked = actions.onFilePicked)
    WaiveraBottomSheet(
        onDismissRequest = { if (choosingType) choosingType = false else actions.onDismiss() },
        title = stringResource(if (choosingType) R.string.feature_tasks_log_type else R.string.feature_tasks_log_title),
    ) {
        if (choosingType) {
            TypeChoices(form, actions.onRetryTypes) { id ->
                actions.onChooseType(id)
                choosingType = false
            }
        } else {
            LogActivityFields(form, task, actions, onChooseType = { choosingType = true }, onFileSource = pickFile)
        }
    }
}

@Composable
internal fun ColumnScope.LogActivityFields(
    form: LogForm,
    task: TaskDetail?,
    actions: LogActivityActions,
    onChooseType: () -> Unit,
    onFileSource: (UploadSource) -> Unit = {},
) {
    val colors = WaiveraTheme.colors
    var pickingWhen by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.MediumSmall),
    ) {
        if (task != null) {
            Text(
                text = stringResource(R.string.feature_tasks_activity_row, task.key, task.title),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
        }
        if (form.failed) {
            WaiveraBanner(message = stringResource(R.string.feature_tasks_log_failed), tone = BannerTone.Error)
        }
        WaiveraPickerField(
            label = stringResource(R.string.feature_tasks_log_type),
            value = form.type?.name,
            placeholder = stringResource(R.string.feature_tasks_log_type_placeholder),
            icon = WaiveraIcons.ExpandMore,
            errorText = stringResource(R.string.feature_tasks_log_type_required).takeIf {
                form.showErrors &&
                    form.typeMissing
            },
            enabled = !form.isSaving,
            onClick = onChooseType,
        )
        if (form.needsFile) LogActivityFile(form, onSource = onFileSource, onRemove = actions.onRemoveFile)
        if (form.showsTime) DurationStepper(form, actions.onStep)
        WaiveraPickerField(
            label = stringResource(R.string.feature_tasks_log_when),
            value = form.whenText.text(),
            placeholder = stringResource(R.string.feature_tasks_log_when_placeholder),
            icon = WaiveraIcons.Schedule,
            errorText = stringResource(R.string.feature_tasks_log_when_future).takeIf { form.inFuture },
            enabled = !form.isSaving,
            onClick = { pickingWhen = true },
        )
        WaiveraTextField(
            value = form.description,
            onValueChange = actions.onDescription,
            label = stringResource(R.string.feature_tasks_log_what),
            placeholder = stringResource(R.string.feature_tasks_log_what_placeholder),
            singleLine = false,
            enabled = !form.isSaving && !form.descriptionLocked,
            errorText = stringResource(R.string.feature_tasks_log_what_required)
                .takeIf { form.showErrors && form.descriptionMissing },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        )
    }
    WaiveraButton(
        text = stringResource(R.string.feature_tasks_log_submit),
        onClick = actions.onSubmit,
        fullWidth = true,
        size = WaiveraButtonSize.Large,
        loadingText = when {
            form.isUploading -> stringResource(R.string.feature_tasks_log_uploading)
            form.isSaving -> stringResource(R.string.feature_tasks_log_saving)
            else -> null
        },
    )
    if (pickingWhen) {
        WhenPicker(form, onPicked = actions.onWhen, onDismiss = { pickingWhen = false })
    }
}

@Composable
private fun TypeChoices(form: LogForm, onRetry: () -> Unit, onPick: (Int) -> Unit) {
    when (val types = form.types) {
        Loadable.Loading -> Text(
            text = stringResource(R.string.feature_tasks_loading),
            style = MaterialTheme.typography.bodyMedium,
            color = WaiveraTheme.colors.textSecondary,
        )
        Loadable.Failed -> WaiveraErrorState(
            message = stringResource(R.string.feature_tasks_log_types_error),
            onRetry = onRetry,
            dense = true,
        )
        is Loadable.Ready -> Column(Modifier.verticalScroll(rememberScrollState())) {
            types.value.forEach { type -> TypeRow(type, selected = type.id == form.typeId, onPick = onPick) }
        }
    }
}

@Composable
private fun TypeRow(type: ActivityType, selected: Boolean, onPick: (Int) -> Unit) {
    WaiveraListRow(
        headline = type.name,
        supporting = listOfNotNull(
            stringResource(R.string.feature_tasks_log_type_needs_file).takeIf { type.needsFile },
            type.recommendedMinutes?.let { durationText(it) },
        ).joinToString(" · ").ifEmpty { null },
        onClick = { onPick(type.id) },
        trailing = {
            if (selected) {
                Icon(
                    painter = painterResource(WaiveraIcons.Check),
                    contentDescription = null,
                    tint = WaiveraTheme.colors.accent,
                )
            }
        },
        modifier = Modifier.semantics { if (selected) stateDescription = type.name },
    )
}

@Composable
private fun DurationStepper(form: LogForm, onStep: (Boolean) -> Unit) {
    val colors = WaiveraTheme.colors
    val locked = form.minutesLocked || form.isSaving
    val label = stringResource(R.string.feature_tasks_log_time)
    val value = durationText(form.minutes)
    Column(verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.ExtraSmall)) {
        Text(text = label, style = WaiveraTextStyles.FieldLabel, color = colors.textSecondary)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.Small),
        ) {
            StepButton(
                icon = R.drawable.feature_tasks_ic_minus,
                label = stringResource(R.string.feature_tasks_log_less),
                enabled = !locked && form.minutes > LogForm.STEP_MINUTES,
                onClick = { onStep(false) },
            )
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                color = colors.text,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f).semantics { contentDescription = "$label, $value" },
            )
            StepButton(
                icon = WaiveraIcons.Add,
                label = stringResource(R.string.feature_tasks_log_more),
                enabled = !locked && form.minutes < LogForm.MAX_MINUTES,
                onClick = { onStep(true) },
            )
        }
        if (form.minutesLocked) {
            Text(
                text = stringResource(R.string.feature_tasks_log_time_set_by_type),
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )
        }
    }
}

@Composable
private fun StepButton(icon: Int, label: String, enabled: Boolean, onClick: () -> Unit) {
    val colors = WaiveraTheme.colors
    Box(
        modifier = Modifier.background(colors.surfaceMuted, WaiveraShapes.SheetCard),
        contentAlignment = Alignment.Center,
    ) {
        WaiveraIconButton(
            icon = icon,
            contentDescription = label,
            onClick = onClick,
            enabled = enabled,
            tint = if (enabled) colors.accent else colors.textSecondary,
        )
    }
}

/** Day, then time, both wall-clock values in the employee's saved zone; later days can't be picked. */
@Composable
private fun WhenPicker(form: LogForm, onPicked: (LocalDate, Int) -> Unit, onDismiss: () -> Unit) {
    val local = form.completedAt.atZone(form.zone)
    var day by remember { mutableStateOf<LocalDate?>(null) }
    val picked = day
    if (picked == null) {
        WaiveraDatePickerDialog(
            initial = local.toLocalDate(),
            latest = form.today ?: local.toLocalDate(),
            onPicked = { day = it },
            onDismiss = { if (day == null) onDismiss() },
        )
    } else {
        WaiveraTimePickerDialog(
            initial = local.toLocalTime(),
            title = stringResource(R.string.feature_tasks_log_when),
            onPicked = { time -> onPicked(picked, time.hour * MINUTES_PER_HOUR + time.minute) },
            onDismiss = onDismiss,
        )
    }
}

@WaiveraPreviews
@Composable
private fun LogActivityPreview() {
    WaiveraTheme {
        WaiveraSheetFrame(title = stringResource(R.string.feature_tasks_log_title)) {
            LogActivityFields(
                form = TaskPreviewData.logForm(),
                task = TaskPreviewData.task(),
                actions = LogActivityActions.None,
                onChooseType = {},
            )
        }
    }
}
