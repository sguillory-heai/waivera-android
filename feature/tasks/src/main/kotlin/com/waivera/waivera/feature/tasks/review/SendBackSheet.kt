package com.waivera.waivera.feature.tasks.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import com.waivera.waivera.core.data.model.SendBackOption
import com.waivera.waivera.core.designsystem.component.BannerTone
import com.waivera.waivera.core.designsystem.component.WaiveraBanner
import com.waivera.waivera.core.designsystem.component.WaiveraBottomSheet
import com.waivera.waivera.core.designsystem.component.WaiveraButton
import com.waivera.waivera.core.designsystem.component.WaiveraButtonSize
import com.waivera.waivera.core.designsystem.component.WaiveraButtonVariant
import com.waivera.waivera.core.designsystem.component.WaiveraErrorState
import com.waivera.waivera.core.designsystem.component.WaiveraListRow
import com.waivera.waivera.core.designsystem.component.WaiveraPickerField
import com.waivera.waivera.core.designsystem.component.WaiveraTextField
import com.waivera.waivera.core.designsystem.icon.WaiveraIcons
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.feature.tasks.InfoNote
import com.waivera.waivera.feature.tasks.Loadable
import com.waivera.waivera.feature.tasks.R

internal data class SendBackActions(
    val onRetry: () -> Unit = {},
    val onChoose: (Int) -> Unit = {},
    val onFeedback: (String) -> Unit = {},
    val onMinutes: (String) -> Unit = {},
    val onSubmit: () -> Unit = {},
)

/** web `RejectTaskModal`: who fixes it, what to fix, and the reviewer's time (mockup C10). */
@Composable
internal fun SendBackSheet(form: SendBackForm, onDismiss: () -> Unit, actions: SendBackActions) {
    var choosing by rememberSaveable { mutableStateOf(false) }
    WaiveraBottomSheet(
        onDismissRequest = { if (choosing) choosing = false else onDismiss() },
        title = stringResource(
            when {
                choosing -> R.string.feature_tasks_review_send_to
                form.returnsLateReason -> R.string.feature_tasks_review_return_late_title
                else -> R.string.feature_tasks_review_send_back_title
            },
        ),
    ) {
        if (choosing) {
            SendBackChoices(form, actions.onRetry) {
                actions.onChoose(it)
                choosing = false
            }
        } else {
            SendBackFields(form, actions, onChoose = { choosing = true })
        }
    }
}

@Composable
internal fun SendBackFields(form: SendBackForm, actions: SendBackActions, onChoose: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.MediumSmall)) {
        Column(
            modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.MediumSmall),
        ) {
            if (form.failed) {
                WaiveraBanner(
                    message = stringResource(R.string.feature_tasks_review_send_back_failed),
                    tone = BannerTone.Error,
                    gutter = false,
                )
            }
            when {
                form.noOptions -> WaiveraBanner(
                    message = stringResource(R.string.feature_tasks_review_no_earlier_step),
                    tone = BannerTone.Warning,
                    gutter = false,
                )
                form.options is Loadable.Failed -> WaiveraErrorState(
                    message = stringResource(R.string.feature_tasks_review_send_to_error),
                    onRetry = actions.onRetry,
                    dense = true,
                )
                else -> WaiveraPickerField(
                    label = stringResource(R.string.feature_tasks_review_send_to),
                    value = form.option?.label(),
                    placeholder = stringResource(R.string.feature_tasks_review_send_to_placeholder),
                    icon = WaiveraIcons.ExpandMore,
                    errorText = stringResource(R.string.feature_tasks_review_send_to_required).takeIf {
                        form.showErrors && form.optionMissing
                    },
                    enabled = !form.isSaving && form.options is Loadable.Ready,
                    onClick = onChoose,
                )
            }
            val over = form.feedback.length - SendBackForm.MAX_FEEDBACK
            WaiveraTextField(
                value = form.feedback,
                onValueChange = actions.onFeedback,
                label = stringResource(
                    if (form.returnsLateReason) {
                        R.string.feature_tasks_review_correction
                    } else {
                        R.string.feature_tasks_review_feedback
                    },
                ),
                placeholder = stringResource(
                    if (form.returnsLateReason) {
                        R.string.feature_tasks_review_correction_placeholder
                    } else {
                        R.string.feature_tasks_review_feedback_placeholder
                    },
                ),
                singleLine = false,
                minLines = FEEDBACK_LINES,
                enabled = !form.isSaving,
                errorText = when {
                    over > 0 -> pluralStringResource(R.plurals.feature_tasks_review_text_over, over, over)
                    form.showErrors && form.feedbackMissing -> stringResource(
                        R.string.feature_tasks_review_feedback_required,
                    )
                    else -> null
                },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
            // Returning a late reason records no review time, as on the web.
            if (!form.returnsLateReason) {
                WaiveraTextField(
                    value = form.minutes,
                    onValueChange = actions.onMinutes,
                    label = stringResource(R.string.feature_tasks_review_minutes),
                    placeholder = stringResource(R.string.feature_tasks_review_minutes_placeholder),
                    enabled = !form.isSaving,
                    errorText = stringResource(R.string.feature_tasks_review_minutes_invalid).takeIf {
                        form.minutesInvalid
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
        }
        WaiveraButton(
            text = stringResource(R.string.feature_tasks_review_send_back),
            onClick = actions.onSubmit,
            variant = WaiveraButtonVariant.Destructive,
            fullWidth = true,
            size = WaiveraButtonSize.Large,
            enabled = !form.noOptions && !form.feedbackTooLong,
            loadingText = stringResource(R.string.feature_tasks_review_sending).takeIf { form.isSaving },
        )
    }
}

@Composable
private fun SendBackChoices(form: SendBackForm, onRetry: () -> Unit, onPick: (Int) -> Unit) {
    when (val options = form.options) {
        Loadable.Loading -> InfoNote(stringResource(R.string.feature_tasks_loading))
        Loadable.Failed -> WaiveraErrorState(
            message = stringResource(R.string.feature_tasks_review_send_to_error),
            onRetry = onRetry,
            dense = true,
        )
        is Loadable.Ready -> Column(Modifier.verticalScroll(rememberScrollState())) {
            options.value.forEach { option ->
                val selected = option.approvalId == form.approvalId
                WaiveraListRow(
                    headline = option.name,
                    supporting = option.role,
                    onClick = { onPick(option.approvalId) },
                    trailing = {
                        if (selected) {
                            Icon(
                                painter = painterResource(WaiveraIcons.Check),
                                contentDescription = null,
                                tint = WaiveraTheme.colors.accent,
                            )
                        }
                    },
                    modifier = Modifier.semantics { if (selected) stateDescription = option.name },
                )
            }
        }
    }
}

@Composable
private fun SendBackOption.label(): String =
    role?.let { stringResource(R.string.feature_tasks_review_person_role, name, it) } ?: name

private const val FEEDBACK_LINES = 3
