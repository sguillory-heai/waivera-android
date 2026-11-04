package com.waivera.waivera.feature.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
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
import androidx.compose.ui.semantics.Role
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
import com.waivera.waivera.core.designsystem.component.WaiveraErrorState
import com.waivera.waivera.core.designsystem.component.WaiveraIconButton
import com.waivera.waivera.core.designsystem.component.WaiveraListRow
import com.waivera.waivera.core.designsystem.component.WaiveraPressable
import com.waivera.waivera.core.designsystem.component.WaiveraSheetFrame
import com.waivera.waivera.core.designsystem.component.WaiveraTextField
import com.waivera.waivera.core.designsystem.icon.WaiveraIcons
import com.waivera.waivera.core.designsystem.preview.WaiveraPreviews
import com.waivera.waivera.core.designsystem.theme.PressDepth
import com.waivera.waivera.core.designsystem.theme.WaiveraShapes
import com.waivera.waivera.core.designsystem.theme.WaiveraSizes
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTextStyles
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

internal data class LogActivityActions(
    val onDismiss: () -> Unit,
    val onRetryTypes: () -> Unit,
    val onChooseType: (Int) -> Unit,
    val onStep: (up: Boolean) -> Unit,
    val onWhen: (LocalDate, minuteOfDay: Int) -> Unit,
    val onDescription: (String) -> Unit,
    val onSubmit: () -> Unit,
) {
    companion object {
        val None = LogActivityActions({}, {}, {}, {}, { _, _ -> }, {}, {})
    }
}

@Composable
internal fun LogActivitySheet(form: LogForm, task: TaskDetail?, actions: LogActivityActions) {
    var choosingType by rememberSaveable { mutableStateOf(false) }
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
            LogActivityFields(form, task, actions, onChooseType = { choosingType = true })
        }
    }
}

@Composable
internal fun ColumnScope.LogActivityFields(
    form: LogForm,
    task: TaskDetail?,
    actions: LogActivityActions,
    onChooseType: () -> Unit,
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
        PickerField(
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
        DurationStepper(form, actions.onStep)
        PickerField(
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
        loadingText = stringResource(R.string.feature_tasks_log_saving).takeIf { form.isSaving },
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
            Text(
                text = stringResource(R.string.feature_tasks_log_file_types_on_web),
                style = MaterialTheme.typography.bodySmall,
                color = WaiveraTheme.colors.textSecondary,
                modifier = Modifier.padding(top = WaiveraSpacing.Small),
            )
        }
    }
}

@Composable
private fun TypeRow(type: ActivityType, selected: Boolean, onPick: (Int) -> Unit) {
    WaiveraListRow(
        headline = type.name,
        supporting = type.recommendedMinutes?.let { durationText(it) },
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

/** A field that opens a chooser: it looks like a text field, reads as a button. */
@Composable
private fun PickerField(
    label: String,
    value: String?,
    placeholder: String,
    icon: Int,
    errorText: String?,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = WaiveraTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.ExtraSmall + WaiveraSpacing.XxSmall)) {
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
        WaiveraPressable(
            onClick = onClick,
            enabled = enabled,
            onClickLabel = label,
            role = Role.Button,
            shape = WaiveraShapes.Field,
            depth = PressDepth.Card,
            modifier = Modifier.semantics { contentDescription = "$label, ${value ?: placeholder}" },
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = WaiveraSizes.FieldHeight)
                    .background(colors.field, WaiveraShapes.Field)
                    .border(
                        WaiveraSpacing.Hairline,
                        if (errorText != null) colors.dangerText else colors.fieldBorder,
                        WaiveraShapes.Field,
                    )
                    .padding(horizontal = WaiveraSpacing.Medium),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = value ?: placeholder,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (value == null) colors.textSecondary else colors.text,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    painter = painterResource(icon),
                    contentDescription = null,
                    tint = colors.textSecondary,
                    modifier = Modifier.size(WaiveraSizes.Icon),
                )
            }
        }
        if (errorText != null) {
            Text(text = errorText, style = MaterialTheme.typography.bodySmall, color = colors.dangerText)
        }
    }
}

/**
 * Day, then time, both as wall-clock values in the employee's saved zone. The date picker works
 * in UTC midnights, so the day crosses that boundary as a plain [LocalDate] and never through
 * the device zone.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WhenPicker(form: LogForm, onPicked: (LocalDate, Int) -> Unit, onDismiss: () -> Unit) {
    val local = form.completedAt.atZone(form.zone)
    val today = form.today ?: local.toLocalDate()
    var day by remember { mutableStateOf<LocalDate?>(null) }
    val picked = day
    if (picked == null) {
        val dateState = rememberDatePickerState(
            initialSelectedDateMillis = local.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = remember(today) {
                object : SelectableDates {
                    override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                        !Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate().isAfter(today)

                    override fun isSelectableYear(year: Int): Boolean = year <= today.year
                }
            },
        )
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(onClick = {
                    dateState.selectedDateMillis?.let {
                        day = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                }) { Text(stringResource(R.string.feature_tasks_pick_time_ok)) }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.feature_tasks_pick_cancel)) }
            },
        ) {
            DatePicker(state = dateState)
        }
    } else {
        val timeState = rememberTimePickerState(initialHour = local.hour, initialMinute = local.minute)
        TimePickerDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.feature_tasks_log_when)) },
            confirmButton = {
                TextButton(onClick = {
                    onPicked(picked, timeState.hour * MINUTES_PER_HOUR + timeState.minute)
                    onDismiss()
                }) { Text(stringResource(R.string.feature_tasks_pick_time_ok)) }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.feature_tasks_pick_cancel)) }
            },
        ) {
            TimePicker(state = timeState)
        }
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
