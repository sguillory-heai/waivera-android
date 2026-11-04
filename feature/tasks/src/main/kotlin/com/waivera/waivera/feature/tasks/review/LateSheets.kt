package com.waivera.waivera.feature.tasks.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.waivera.waivera.core.data.model.FileKind
import com.waivera.waivera.core.data.model.LateReview
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
import com.waivera.waivera.core.designsystem.theme.WaiveraTextStyles
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.core.designsystem.theme.isLargeFontScale
import com.waivera.waivera.feature.tasks.InfoDivider
import com.waivera.waivera.feature.tasks.InfoNote
import com.waivera.waivera.feature.tasks.InfoRow
import com.waivera.waivera.feature.tasks.InfoValue
import com.waivera.waivera.feature.tasks.Loadable
import com.waivera.waivera.feature.tasks.LongNote
import com.waivera.waivera.feature.tasks.R
import com.waivera.waivera.feature.tasks.SheetGroup
import com.waivera.waivera.feature.tasks.TaskFormat
import com.waivera.waivera.feature.tasks.files.PickedFileRow
import com.waivera.waivera.feature.tasks.files.SourceRow
import com.waivera.waivera.feature.tasks.files.UploadSource
import com.waivera.waivera.feature.tasks.files.rememberUploadPicker
import com.waivera.waivera.feature.tasks.files.text
import java.time.LocalDate

internal data class LateActions(
    val onRetryReasons: () -> Unit = {},
    val onReason: (Long) -> Unit = {},
    val onExplanation: (String) -> Unit = {},
    val onDropDelay: () -> Unit = {},
    val onEvidencePicked: (List<String>) -> Unit = {},
    val onRemoveEvidence: () -> Unit = {},
    val onSubmit: () -> Unit = {},
    val onAgree: () -> Unit = {},
    val onEdit: () -> Unit = {},
    val onReturn: () -> Unit = {},
    val onNote: (String) -> Unit = {},
    val onOpenEvidence: (Int) -> Unit = {},
)

/** The late reason, sent before a late task moves on or corrected by a reviewer (mockup C3). */
@Composable
internal fun LateReasonSheet(form: LateForm, today: LocalDate, onDismiss: () -> Unit, actions: LateActions) {
    var choosing by rememberSaveable { mutableStateOf(false) }
    val pick = rememberUploadPicker(single = true, onPicked = actions.onEvidencePicked)
    WaiveraBottomSheet(
        onDismissRequest = { if (choosing) choosing = false else onDismiss() },
        title = stringResource(
            when {
                choosing -> R.string.feature_tasks_late_reason
                form.editingId != null -> R.string.feature_tasks_late_edit_title
                else -> R.string.feature_tasks_late_title
            },
        ),
    ) {
        if (choosing) {
            LateReasonChoices(form, actions.onRetryReasons) {
                actions.onReason(it)
                choosing = false
            }
        } else {
            LateReasonFields(form, today, actions, onChoose = { choosing = true }, onSource = pick)
        }
    }
}

@Composable
internal fun LateReasonFields(
    form: LateForm,
    today: LocalDate,
    actions: LateActions,
    onChoose: () -> Unit,
    onSource: (UploadSource) -> Unit,
) {
    val colors = WaiveraTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.MediumSmall)) {
        Column(
            modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.MediumSmall),
        ) {
            if (form.editingId == null) {
                form.due?.let {
                    Text(
                        text = stringResource(R.string.feature_tasks_late_due, TaskFormat.date(it, today)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                    )
                }
            }
            LateBanners(form, actions)
            WaiveraPickerField(
                label = stringResource(R.string.feature_tasks_late_reason),
                value = form.reason?.name,
                placeholder = stringResource(R.string.feature_tasks_late_choose_reason),
                icon = WaiveraIcons.ExpandMore,
                errorText = stringResource(R.string.feature_tasks_late_choose_reason).takeIf {
                    form.showErrors && form.reasonMissing
                },
                enabled = !form.isSaving,
                onClick = onChoose,
            )
            form.reason?.description?.let { InfoNote(it) }
            ExplanationField(form, actions.onExplanation)
            if (form.reason?.requiresEvidence == true) EvidenceField(form, actions, onSource)
        }
        WaiveraButton(
            text = stringResource(
                if (form.editingId !=
                    null
                ) {
                    R.string.feature_tasks_late_save
                } else {
                    R.string.feature_tasks_late_send
                },
            ),
            onClick = actions.onSubmit,
            fullWidth = true,
            size = WaiveraButtonSize.Large,
            enabled = !form.evidenceChecking && !form.explanationTooLong,
            loadingText = stringResource(R.string.feature_tasks_review_sending).takeIf { form.isSaving },
        )
    }
}

@Composable
private fun LateBanners(form: LateForm, actions: LateActions) {
    if (form.failed) {
        WaiveraBanner(
            message = stringResource(R.string.feature_tasks_late_failed),
            tone = BannerTone.Error,
            gutter = false,
        )
    }
    if (form.returned) {
        WaiveraBanner(
            title = stringResource(R.string.feature_tasks_late_returned_title),
            message = form.correction?.let { stringResource(R.string.feature_tasks_late_returned_note, it) }
                ?: stringResource(R.string.feature_tasks_late_returned_fix),
            tone = BannerTone.Warning,
            gutter = false,
        )
    }
    if (form.delayId != null) {
        WaiveraBanner(
            title = stringResource(R.string.feature_tasks_late_delay_title),
            message = stringResource(R.string.feature_tasks_late_delay_message),
            tone = BannerTone.Info,
            gutter = false,
            actionLabel = stringResource(R.string.feature_tasks_late_delay_drop),
            onAction = actions.onDropDelay,
        )
    }
}

@Composable
private fun ExplanationField(form: LateForm, onChange: (String) -> Unit) {
    val over = form.explanation.length - LateForm.MAX_EXPLANATION
    WaiveraTextField(
        value = form.explanation,
        onValueChange = onChange,
        label = stringResource(R.string.feature_tasks_late_explanation),
        placeholder = stringResource(R.string.feature_tasks_late_explanation_placeholder),
        singleLine = false,
        minLines = EXPLANATION_LINES,
        enabled = !form.isSaving,
        errorText = when {
            over > 0 -> pluralStringResource(R.plurals.feature_tasks_review_text_over, over, over)
            form.showErrors && form.explanationMissing -> stringResource(
                R.string.feature_tasks_late_explanation_required,
            )
            else -> null
        },
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
    )
}

@Composable
private fun EvidenceField(form: LateForm, actions: LateActions, onSource: (UploadSource) -> Unit) {
    val colors = WaiveraTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.ExtraSmall)) {
        Text(
            text = stringResource(R.string.feature_tasks_late_evidence),
            style = WaiveraTextStyles.FieldLabel,
            color = colors.textSecondary,
        )
        val file = form.evidence
        when {
            file != null -> SheetGroup {
                PickedFileRow(
                    name = file.name,
                    kind = file.kind,
                    sizeBytes = file.sizeBytes,
                    uploaded = false,
                    canRemove = !form.isSaving,
                    onRemove = actions.onRemoveEvidence,
                    pendingText = stringResource(R.string.feature_tasks_late_evidence_pending),
                )
            }
            form.keptEvidenceId != null -> SheetGroup {
                PickedFileRow(
                    name = stringResource(R.string.feature_tasks_late_evidence_kept),
                    kind = FileKind.Other,
                    sizeBytes = null,
                    uploaded = true,
                    canRemove = false,
                    onRemove = {},
                    pendingText = "",
                )
            }
            else -> SheetGroup {
                UploadSource.entries.forEachIndexed { index, source ->
                    if (index > 0) InfoDivider()
                    SourceRow(
                        source,
                        enabled = !form.isSaving && !form.evidenceChecking,
                        single = true,
                        onClick = { onSource(source) },
                    )
                }
            }
        }
        val problem = form.evidenceProblem
        val error = when {
            problem != null -> stringResource(
                R.string.feature_tasks_late_evidence_failed,
                stringResource(problem.text()),
            )
            form.showErrors && form.evidenceMissing -> stringResource(R.string.feature_tasks_late_evidence_required)
            else -> null
        }
        Text(
            text = error ?: stringResource(
                if (form.evidenceChecking) {
                    R.string.feature_tasks_log_file_checking
                } else {
                    R.string.feature_tasks_late_evidence_needed
                },
            ),
            style = MaterialTheme.typography.bodySmall,
            color = if (error != null) colors.dangerText else colors.textSecondary,
        )
    }
}

@Composable
private fun LateReasonChoices(form: LateForm, onRetry: () -> Unit, onPick: (Long) -> Unit) {
    when (val reasons = form.reasons) {
        Loadable.Loading -> InfoNote(stringResource(R.string.feature_tasks_loading))
        Loadable.Failed -> WaiveraErrorState(
            message = stringResource(R.string.feature_tasks_late_reasons_error),
            onRetry = onRetry,
            dense = true,
        )
        is Loadable.Ready -> Column(Modifier.verticalScroll(rememberScrollState())) {
            reasons.value.forEach { reason ->
                val selected = reason.id == form.reasonId
                WaiveraListRow(
                    headline = reason.name,
                    supporting = reason.description,
                    onClick = { onPick(reason.id) },
                    trailing = {
                        if (selected) {
                            Icon(
                                painter = painterResource(WaiveraIcons.Check),
                                contentDescription = null,
                                tint = WaiveraTheme.colors.accent,
                            )
                        }
                    },
                    modifier = Modifier.semantics { if (selected) stateDescription = reason.name },
                )
            }
        }
    }
}

/** A reviewer's look at the late reason: agree, correct it, or send the task back (mockup C4). */
@Composable
internal fun LateReviewSheet(check: LateCheck, onDismiss: () -> Unit, actions: LateActions) {
    WaiveraBottomSheet(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.feature_tasks_late_review_title),
    ) { LateReviewFields(check, actions) }
}

@Composable
internal fun LateReviewFields(check: LateCheck, actions: LateActions) {
    val justification = check.context.justification ?: return
    Column(verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.MediumSmall)) {
        Column(
            modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.MediumSmall),
        ) {
            if (check.failed) {
                WaiveraBanner(
                    message = stringResource(R.string.feature_tasks_late_review_failed),
                    tone = BannerTone.Error,
                    gutter = false,
                )
            }
            SheetGroup {
                justification.reasonName?.let {
                    InfoRow(stringResource(R.string.feature_tasks_late_reason), WaiveraIcons.Hash) { InfoValue(it) }
                    InfoDivider()
                }
                InfoRow(
                    label = justification.submittedBy?.let { stringResource(R.string.feature_tasks_late_wrote, it) }
                        ?: stringResource(R.string.feature_tasks_late_explanation_row),
                    icon = WaiveraIcons.Chat,
                ) { LongNote(justification.explanation) }
                justification.evidenceId?.let { id ->
                    InfoDivider()
                    InfoRow(
                        label = stringResource(R.string.feature_tasks_late_evidence),
                        icon = WaiveraIcons.Attachment,
                        onClick = { actions.onOpenEvidence(id) },
                    ) { InfoValue(stringResource(R.string.feature_tasks_late_open_file)) }
                }
                if (justification.delayId != null) {
                    InfoDivider()
                    InfoRow(stringResource(R.string.feature_tasks_late_linked_delay), WaiveraIcons.Schedule) {
                        InfoValue(stringResource(R.string.feature_tasks_flag_delayed))
                    }
                }
            }
            if (check.context.reviews.isNotEmpty()) EarlierReviews(check.context.reviews)
            WaiveraTextField(
                value = check.note,
                onValueChange = actions.onNote,
                label = stringResource(R.string.feature_tasks_late_note),
                placeholder = stringResource(R.string.feature_tasks_late_note_placeholder),
                singleLine = false,
                minLines = NOTE_LINES,
                enabled = !check.isSaving,
                errorText = (check.note.length - LateCheck.MAX_NOTE).takeIf { it > 0 }?.let {
                    pluralStringResource(R.plurals.feature_tasks_review_text_over, it, it)
                },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
        }
        WaiveraButton(
            text = stringResource(R.string.feature_tasks_late_agree),
            onClick = actions.onAgree,
            leadingIcon = WaiveraIcons.Check,
            fullWidth = true,
            size = WaiveraButtonSize.Large,
            loadingText = stringResource(R.string.feature_tasks_review_sending).takeIf { check.isSaving },
        )
        val edit = @Composable { modifier: Modifier ->
            WaiveraButton(
                text = stringResource(R.string.feature_tasks_late_edit),
                onClick = actions.onEdit,
                variant = WaiveraButtonVariant.Secondary,
                size = WaiveraButtonSize.Large,
                enabled = !check.isSaving,
                modifier = modifier,
            )
        }
        val sendBack = @Composable { modifier: Modifier ->
            WaiveraButton(
                text = stringResource(R.string.feature_tasks_review_send_back),
                onClick = actions.onReturn,
                variant = WaiveraButtonVariant.DestructiveOutline,
                size = WaiveraButtonSize.Large,
                enabled = !check.isSaving,
                modifier = modifier,
            )
        }
        // Side by side, the two labels wrap unevenly at large font; stacked they stay one line each.
        if (isLargeFontScale()) {
            edit(Modifier.fillMaxWidth())
            sendBack(Modifier.fillMaxWidth())
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.Small)) {
                edit(Modifier.weight(1f))
                sendBack(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun EarlierReviews(reviews: List<LateReview>) {
    Text(
        text = stringResource(R.string.feature_tasks_late_earlier),
        style = MaterialTheme.typography.labelMedium,
        color = WaiveraTheme.colors.textSecondary,
    )
    SheetGroup {
        reviews.forEachIndexed { index, review ->
            if (index > 0) InfoDivider()
            val who = review.name.orEmpty()
            val role = review.role.orEmpty()
            InfoRow(
                label = stringResource(
                    if (review.agreed) {
                        R.string.feature_tasks_late_review_agreed
                    } else {
                        R.string.feature_tasks_late_review_returned
                    },
                    who,
                    role,
                ),
                icon = if (review.agreed) WaiveraIcons.Check else WaiveraIcons.Warning,
            ) { review.note?.let { LongNote(it) } }
        }
    }
}

private const val EXPLANATION_LINES = 3
private const val NOTE_LINES = 2
