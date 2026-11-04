package com.waivera.waivera.feature.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextDecoration
import com.waivera.waivera.core.data.model.LeaveImpact
import com.waivera.waivera.core.data.model.ProfileField
import com.waivera.waivera.core.data.model.RequestKind
import com.waivera.waivera.core.data.model.TrainingBlock
import com.waivera.waivera.core.data.model.TrainingCredit
import com.waivera.waivera.core.designsystem.component.BadgeTone
import com.waivera.waivera.core.designsystem.component.BannerTone
import com.waivera.waivera.core.designsystem.component.SkeletonText
import com.waivera.waivera.core.designsystem.component.WaiveraAvatar
import com.waivera.waivera.core.designsystem.component.WaiveraBanner
import com.waivera.waivera.core.designsystem.component.WaiveraBottomSheet
import com.waivera.waivera.core.designsystem.component.WaiveraButton
import com.waivera.waivera.core.designsystem.component.WaiveraButtonSize
import com.waivera.waivera.core.designsystem.component.WaiveraButtonVariant
import com.waivera.waivera.core.designsystem.component.WaiveraErrorState
import com.waivera.waivera.core.designsystem.component.WaiveraIconTile
import com.waivera.waivera.core.designsystem.component.WaiveraStatusBadge
import com.waivera.waivera.core.designsystem.component.WaiveraTextField
import com.waivera.waivera.core.designsystem.component.WaiveraTileTone
import com.waivera.waivera.core.designsystem.component.rememberSkeletonPulse
import com.waivera.waivera.core.designsystem.icon.WaiveraIcons
import com.waivera.waivera.core.designsystem.theme.WaiveraShapes
import com.waivera.waivera.core.designsystem.theme.WaiveraSizes
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTextStyles
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.core.designsystem.theme.isLargeFontScale

/** What a review sheet loads after it opens: the balance for time off, the credit for training. */
internal data class ReviewExtras(
    val balance: Loadable<BalanceChange?> = Loadable.Ready(null),
    val training: Loadable<TrainingCredit> = Loadable.Loading,
    val openingCertificate: Boolean = false,
)

internal data class ReviewActions(
    val onApprove: () -> Unit,
    val onReject: () -> Unit,
    val onOpenTask: (() -> Unit)? = null,
    val onRetry: () -> Unit = {},
    val onOpenCertificate: () -> Unit = {},
    val onOpenWeb: () -> Unit = {},
)

/**
 * The review: who asked and when, what changes and why, then the decision. The details scroll
 * and the buttons stay pinned under them, so a long note never pushes Approve out of reach.
 */
@Composable
internal fun ReviewFields(item: RequestItem, extras: ReviewExtras, actions: ReviewActions) {
    Column(verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.MediumSmall)) {
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.MediumSmall),
        ) {
            Requester(item)
            when (val change = item.change) {
                is RequestChange.TimeOff -> TimeOffDetails(item, change, extras.balance, actions.onRetry)
                is RequestChange.Profile -> ProfileDetails(change)
                RequestChange.Training -> TrainingDetails(extras, actions)
                else -> TaskRequestDetails(item, actions)
            }
        }
        Decisions(item, extras, actions)
    }
}

/** Kept for the screenshot tests and previews of the task requests. */
@Composable
internal fun ReviewFields(item: RequestItem, onApprove: () -> Unit, onReject: () -> Unit, onOpenTask: (() -> Unit)?) =
    ReviewFields(item, ReviewExtras(), ReviewActions(onApprove, onReject, onOpenTask))

@Composable
private fun Requester(item: RequestItem) {
    val colors = WaiveraTheme.colors
    val request = item.request
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.MediumSmall),
    ) {
        WaiveraAvatar(name = request.requesterName, size = WaiveraSizes.AvatarMedium, contentDescription = null)
        Column(Modifier.weight(1f)) {
            Text(request.requesterName, style = MaterialTheme.typography.titleSmall, color = colors.text)
            item.asked?.let {
                Text(
                    text = stringResource(R.string.feature_tasks_review_requested, it.text().lowercaseFirstWord()),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun ColumnScope.TaskRequestDetails(item: RequestItem, actions: ReviewActions) {
    val request = item.request
    SheetGroup {
        TaskChangeRow(item)
        request.reason?.takeIf { it.isNotBlank() }?.let { reason ->
            InfoDivider()
            InfoRow(stringResource(request.kind.reasonLabel()), WaiveraIcons.Hash) { InfoValue(reason) }
        }
        request.explanation?.takeIf { it.isNotBlank() }?.let { note ->
            InfoDivider()
            InfoRow(stringResource(R.string.feature_tasks_review_note), WaiveraIcons.Chat) { LongNote(note) }
        }
    }
    TaskLink(item, actions.onOpenTask)
}

@Composable
private fun TaskLink(item: RequestItem, onOpenTask: (() -> Unit)?) {
    val request = item.request
    request.taskTitle?.let { title ->
        SheetGroup {
            InfoRow(
                label = stringResource(R.string.feature_tasks_review_task),
                icon = R.drawable.feature_tasks_ic_task,
                onClick = onOpenTask,
                onClickLabel = stringResource(R.string.feature_tasks_open_task),
            ) {
                InfoValue(title)
                if (item.due != DueText.None) InfoNote(item.due.text())
                if (request.isTrainingTask) InfoNote(stringResource(R.string.feature_tasks_review_training))
            }
        }
    }
}

@Composable
private fun TaskChangeRow(item: RequestItem) {
    val colors = WaiveraTheme.colors
    when (val change = item.change) {
        is RequestChange.DueMove -> InfoRow(
            stringResource(R.string.feature_tasks_review_due_change),
            WaiveraIcons.Schedule,
        ) {
            InfoValue(change.text().orEmpty())
            change.daysLater?.let {
                Text(
                    text = pluralStringResource(R.plurals.feature_tasks_days_later, it, it),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.warningText,
                )
            }
        }
        is RequestChange.DelayWindow -> InfoRow(
            stringResource(R.string.feature_tasks_review_delay_window),
            WaiveraIcons.Warning,
            tone = WaiveraTileTone.Amber,
        ) {
            InfoValue(change.text().orEmpty())
        }
        RequestChange.Closure -> InfoRow(stringResource(R.string.feature_tasks_review_closure), WaiveraIcons.Archive) {
            InfoValue(stringResource(R.string.feature_tasks_change_closure))
        }
        RequestChange.Reopen -> InfoRow(stringResource(R.string.feature_tasks_review_closure), WaiveraIcons.Refresh) {
            InfoValue(stringResource(R.string.feature_tasks_change_reopen))
            InfoNote(stringResource(R.string.feature_tasks_review_reopen_note))
        }
        RequestChange.RemoveFile -> InfoRow(
            stringResource(R.string.feature_tasks_review_closure),
            WaiveraIcons.Attachment,
        ) {
            InfoValue(stringResource(R.string.feature_tasks_change_remove_file))
            InfoNote(stringResource(R.string.feature_tasks_review_remove_file_note))
        }
        else -> InfoRow(item.request.kindLabel(), item.request.kind.icon()) {
            InfoValue(item.request.typeName)
        }
    }
}

@Composable
private fun TimeOffDetails(
    item: RequestItem,
    change: RequestChange.TimeOff,
    balance: Loadable<BalanceChange?>,
    onRetry: () -> Unit,
) {
    val timeOff = item.request.timeOff
    SheetGroup {
        InfoRow(stringResource(R.string.feature_tasks_review_dates), WaiveraIcons.Calendar) {
            InfoValue(spanText(change.dates))
            val length = hoursText(change.minutes)
            InfoNote(
                change.times?.let { stringResource(R.string.feature_tasks_review_partial_day, spanText(it), length) }
                    ?: stringResource(R.string.feature_tasks_review_full_days, length),
            )
        }
        if (change.leaveType.isNotEmpty()) {
            InfoDivider()
            InfoRow(stringResource(R.string.feature_tasks_review_leave_type), WaiveraIcons.Hash) {
                InfoValue(change.leaveType)
            }
        }
        BalanceRow(balance, change.cancellation, onRetry)
        timeOff?.note?.let { note ->
            InfoDivider()
            InfoRow(stringResource(R.string.feature_tasks_review_note), WaiveraIcons.Chat) { LongNote(note) }
        }
    }
    timeOff?.let { ImpactNotices(item.request.requesterName, it.impact, it.impactChanged, it.acknowledged) }
}

@Composable
private fun BalanceRow(balance: Loadable<BalanceChange?>, cancellation: Boolean, onRetry: () -> Unit) {
    val colors = WaiveraTheme.colors
    when (balance) {
        Loadable.Loading -> {
            InfoDivider()
            InfoRow(stringResource(R.string.feature_tasks_review_balance), WaiveraIcons.Schedule) {
                SkeletonText(
                    style = WaiveraTextStyles.FieldValue,
                    widthFraction = BALANCE_SKELETON,
                    pulse = rememberSkeletonPulse(),
                )
            }
        }
        is Loadable.Ready -> balance.value?.let { change ->
            InfoDivider()
            InfoRow(stringResource(R.string.feature_tasks_review_balance), WaiveraIcons.Schedule) {
                val spoken = stringResource(
                    R.string.feature_tasks_review_balance_spoken,
                    hoursText(change.before),
                    hoursText(change.after),
                )
                Row(
                    modifier = Modifier.clearAndSetSemantics { contentDescription = spoken },
                    horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.ExtraSmall),
                ) {
                    InfoValue(hoursText(change.before))
                    InfoValue(stringResource(R.string.feature_tasks_review_balance_arrow))
                    // The web marks a balance that would go below zero in red; it does not block approval.
                    Text(
                        text = hoursText(change.after),
                        style = WaiveraTextStyles.FieldValue,
                        color = if (change.after < 0) colors.dangerText else colors.text,
                    )
                }
                InfoNote(
                    stringResource(
                        if (cancellation) {
                            R.string.feature_tasks_review_balance_after_cancel
                        } else {
                            R.string.feature_tasks_review_balance_after
                        },
                    ),
                )
            }
        }
        Loadable.Failed -> {
            InfoDivider()
            InfoRow(
                label = stringResource(R.string.feature_tasks_review_balance),
                icon = WaiveraIcons.Schedule,
                onClick = onRetry,
                onClickLabel = stringResource(R.string.feature_tasks_review_balance_retry),
            ) {
                InfoValue(stringResource(R.string.feature_tasks_review_balance_failed))
                InfoNote(stringResource(R.string.feature_tasks_review_balance_retry))
            }
        }
    }
}

/** sprout_web `LeaveImpactSummary`, with the acknowledgement line from the review modal. */
@Composable
private fun ImpactNotices(name: String, impact: LeaveImpact?, changed: Boolean, acknowledged: Boolean?) {
    if (acknowledged == null && impact == null) return
    val ack = when (acknowledged) {
        true -> stringResource(R.string.feature_tasks_review_impact_acknowledged)
        false -> stringResource(R.string.feature_tasks_review_impact_not_acknowledged)
        null -> null
    }
    if (impact == null) {
        WaiveraBanner(
            message = stringResource(R.string.feature_tasks_review_impact_unavailable),
            tone = BannerTone.Warning,
            gutter = false,
        )
        return
    }
    if (impact.shortNotice) {
        WaiveraBanner(
            title = stringResource(R.string.feature_tasks_review_short_notice_title),
            message = stringResource(R.string.feature_tasks_review_short_notice),
            tone = BannerTone.Info,
            icon = WaiveraIcons.Schedule,
            gutter = false,
        )
    }
    val total = impact.dueBefore + impact.dueDuring
    val details = listOfNotNull(
        stringResource(R.string.feature_tasks_review_impact_counts, impact.dueBefore, impact.dueDuring)
            .takeIf { total > 0 },
        stringResource(R.string.feature_tasks_review_impact_changed).takeIf { changed },
        stringResource(R.string.feature_tasks_review_extended_sick).takeIf { impact.extendedSick },
        ack,
    )
    WaiveraBanner(
        title = if (total > 0) {
            pluralStringResource(R.plurals.feature_tasks_review_impact_tasks, total, total, name.firstName())
        } else {
            stringResource(R.string.feature_tasks_review_impact_none)
        },
        message = details.joinToString(" "),
        tone = if (total > 0) BannerTone.Warning else BannerTone.Info,
        icon = if (total > 0) WaiveraIcons.Warning else WaiveraIcons.Check,
        gutter = false,
    )
}

@Composable
private fun ProfileDetails(change: RequestChange.Profile) {
    Row(horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.Small)) {
        if (change.changed.isNotEmpty()) {
            WaiveraStatusBadge(
                text = pluralStringResource(
                    R.plurals.feature_tasks_review_changed,
                    change.changed.size,
                    change.changed.size,
                ),
                tone = BadgeTone.Warning,
            )
        }
        if (change.unchanged.isNotEmpty()) {
            WaiveraStatusBadge(
                text = pluralStringResource(
                    R.plurals.feature_tasks_review_unchanged,
                    change.unchanged.size,
                    change.unchanged.size,
                ),
            )
        }
    }
    if (change.changed.isEmpty() && change.unchanged.isEmpty()) {
        SheetGroup {
            InfoRow(stringResource(R.string.feature_tasks_kind_profile), WaiveraIcons.Person) {
                InfoValue(stringResource(R.string.feature_tasks_review_profile_empty))
            }
        }
        return
    }
    if (change.changed.isNotEmpty()) {
        SheetGroup {
            change.changed.forEachIndexed { index, row ->
                if (index > 0) InfoDivider()
                ProfileCompare(row)
            }
        }
    }
    if (change.unchanged.isNotEmpty()) UnchangedFields(change.unchanged)
}

/** Previous beside requested; stacked when either is long or the text is large, so nothing is cut. */
@Composable
private fun ProfileCompare(row: ProfileRow) {
    val colors = WaiveraTheme.colors
    val none = stringResource(R.string.feature_tasks_review_none)
    val previous = row.previous ?: none
    val requested = row.requested ?: none
    val stack = isLargeFontScale() || previous.length > SIDE_BY_SIDE_CHARS || requested.length > SIDE_BY_SIDE_CHARS
    Column(
        modifier = Modifier.padding(horizontal = WaiveraSpacing.Medium, vertical = WaiveraSpacing.MediumSmall),
        verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.Small),
    ) {
        Text(row.field.label(), style = WaiveraTextStyles.FieldLabel, color = colors.textSecondary)
        val previousBox = @Composable { modifier: Modifier ->
            ValueBox(stringResource(R.string.feature_tasks_review_previous), previous, struck = true, modifier)
        }
        val requestedBox = @Composable { modifier: Modifier ->
            ValueBox(stringResource(R.string.feature_tasks_review_requested_value), requested, struck = false, modifier)
        }
        if (stack) {
            previousBox(Modifier.fillMaxWidth())
            requestedBox(Modifier.fillMaxWidth())
        } else {
            Row(
                horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.Small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                previousBox(Modifier.weight(1f))
                Icon(
                    painter = painterResource(WaiveraIcons.ChevronRight),
                    contentDescription = null,
                    tint = colors.textSecondary,
                    modifier = Modifier.size(WaiveraSizes.IconSmall),
                )
                requestedBox(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ValueBox(label: String, value: String, struck: Boolean, modifier: Modifier) {
    val colors = WaiveraTheme.colors
    Column(
        modifier = modifier
            .background(colors.surfaceMuted, WaiveraShapes.Tile)
            .padding(horizontal = WaiveraSpacing.MediumSmall, vertical = WaiveraSpacing.Small),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = if (struck) colors.textSecondary else colors.text,
            textDecoration = if (struck) TextDecoration.LineThrough else null,
        )
    }
}

/** The fields that stay the same, folded into one row; opening it lists their values. */
@Composable
private fun UnchangedFields(rows: List<ProfileRow>) {
    var open by rememberSaveable { mutableStateOf(false) }
    val state = stringResource(
        if (open) R.string.feature_tasks_activity_expanded else R.string.feature_tasks_activity_collapsed,
    )
    SheetGroup {
        InfoRow(
            label = stringResource(R.string.feature_tasks_review_unchanged_fields),
            icon = WaiveraIcons.Check,
            tone = WaiveraTileTone.Teal,
            modifier = Modifier.semantics { stateDescription = state },
            onClick = { open = !open },
            onClickLabel = stringResource(
                if (open) R.string.feature_tasks_activity_show_less else R.string.feature_tasks_activity_show_more,
            ),
        ) {
            if (open) {
                val none = stringResource(R.string.feature_tasks_review_none)
                rows.forEach { row ->
                    InfoNote(
                        stringResource(
                            R.string.feature_tasks_review_field_value,
                            row.field.label(),
                            row.requested ?: none,
                        ),
                    )
                }
            } else {
                InfoValue(rows.map { it.field.label() }.joinToString(", "))
            }
        }
    }
}

@Composable
private fun TrainingDetails(extras: ReviewExtras, actions: ReviewActions) {
    val training = extras.training
    when (training) {
        Loadable.Loading -> SheetGroup {
            InfoRow(stringResource(R.string.feature_tasks_review_course), WaiveraIcons.Award) {
                SkeletonText(
                    style = WaiveraTextStyles.FieldValue,
                    widthFraction = BALANCE_SKELETON,
                    pulse = rememberSkeletonPulse(),
                )
            }
        }
        Loadable.Failed -> WaiveraErrorState(
            message = stringResource(R.string.feature_tasks_review_training_failed),
            onRetry = actions.onRetry,
            dense = true,
        )
        is Loadable.Ready -> {
            val credit = training.value
            val notRecorded = stringResource(R.string.feature_tasks_review_not_recorded)
            SheetGroup {
                InfoRow(stringResource(R.string.feature_tasks_review_course), WaiveraIcons.Award) {
                    InfoValue(credit.course)
                    val hours = pluralStringResource(
                        R.plurals.feature_tasks_review_hours,
                        credit.hours.toPluralCount(),
                        credit.hours.hoursLabel(),
                    )
                    InfoNote(
                        credit.completed?.let {
                            stringResource(R.string.feature_tasks_review_completed, hours, TaskFormat.fullDate(it))
                        } ?: hours,
                    )
                }
                InfoDivider()
                InfoRow(stringResource(R.string.feature_tasks_review_trainer), WaiveraIcons.Person) {
                    InfoValue(credit.trainer ?: notRecorded)
                }
                InfoDivider()
                InfoRow(stringResource(R.string.feature_tasks_review_provider), WaiveraIcons.Building) {
                    InfoValue(credit.provider ?: notRecorded)
                }
                credit.uploadedBy?.let { uploader ->
                    InfoDivider()
                    InfoRow(stringResource(R.string.feature_tasks_review_uploaded_by), WaiveraIcons.Upload) {
                        InfoValue(uploader)
                    }
                }
                InfoDivider()
                InfoRow(
                    label = stringResource(R.string.feature_tasks_review_certificate),
                    icon = WaiveraIcons.Attachment,
                    onClick = actions.onOpenCertificate.takeUnless { extras.openingCertificate },
                    onClickLabel = stringResource(R.string.feature_tasks_review_view_certificate),
                ) {
                    InfoValue(stringResource(R.string.feature_tasks_review_view_certificate))
                    if (extras.openingCertificate) {
                        InfoNote(
                            stringResource(R.string.feature_tasks_review_certificate_opening),
                        )
                    }
                }
            }
            if (credit.pending && credit.decidable && credit.needsApproveReason) {
                InfoNote(stringResource(R.string.feature_tasks_review_uploaded_yourself, credit.employeeName))
            }
            credit.blocked?.takeIf { !credit.decidable }?.let { InfoNote(stringResource(it.text())) }
        }
    }
}

@Composable
private fun Decisions(item: RequestItem, extras: ReviewExtras, actions: ReviewActions) {
    val training = item.request.kind == RequestKind.TrainingCredit
    val credit = (extras.training as? Loadable.Ready)?.value
    when {
        !training -> DecisionButtons(
            approve = stringResource(R.string.feature_tasks_approve),
            reject = stringResource(R.string.feature_tasks_reject),
            actions = actions,
        )
        credit?.decidable == true -> DecisionButtons(
            approve = stringResource(R.string.feature_tasks_review_approve_credit),
            reject = stringResource(R.string.feature_tasks_review_reject_submission),
            actions = actions,
        )
        credit?.reviewOnWeb == true -> WaiveraButton(
            text = stringResource(R.string.feature_tasks_review_on_web),
            onClick = actions.onOpenWeb,
            leadingIcon = WaiveraIcons.OpenExternal,
            variant = WaiveraButtonVariant.Secondary,
            fullWidth = true,
            size = WaiveraButtonSize.Large,
        )
    }
}

@Composable
private fun DecisionButtons(approve: String, reject: String, actions: ReviewActions) {
    Column(verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.MediumSmall)) {
        WaiveraButton(
            text = approve,
            onClick = actions.onApprove,
            leadingIcon = WaiveraIcons.Check,
            fullWidth = true,
            size = WaiveraButtonSize.Large,
        )
        WaiveraButton(
            text = reject,
            onClick = actions.onReject,
            leadingIcon = WaiveraIcons.Close,
            variant = WaiveraButtonVariant.DestructiveOutline,
            fullWidth = true,
            size = WaiveraButtonSize.Large,
        )
    }
}

/** Notes over four lines open in place, like long activity notes on a task. */
@Composable
internal fun LongNote(text: String) {
    ActivityNote(text = text, startExpanded = false, lines = NOTE_LINES, style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun hoursText(minutes: Int): String {
    val hours = minutes / MINUTES_PER_HOUR.toDouble()
    return pluralStringResource(R.plurals.feature_tasks_review_hours, hours.toPluralCount(), hours.hoursLabel())
}

private fun Double.toPluralCount(): Int = if (this == 1.0) 1 else 2

/** "2", "1.5", "-3.25": at most two decimals, as the web shows hours. */
private fun Double.hoursLabel(): String =
    java.math.BigDecimal(this).setScale(2, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()

private fun String.firstName(): String = trim().substringBefore(' ').ifEmpty { this }

private fun TrainingBlock.text(): Int = when (this) {
    TrainingBlock.OwnCredit -> R.string.feature_tasks_review_blocked_own
    TrainingBlock.NoReviewer -> R.string.feature_tasks_review_blocked_no_reviewer
    TrainingBlock.NotReviewer -> R.string.feature_tasks_review_blocked_not_reviewer
}

@Composable
internal fun ProfileField.Field.label(): String = stringResource(
    when (this) {
        ProfileField.Field.DateOfBirth -> R.string.feature_tasks_field_birth
        ProfileField.Field.FirstName -> R.string.feature_tasks_field_first_name
        ProfileField.Field.LastName -> R.string.feature_tasks_field_last_name
        ProfileField.Field.Email -> R.string.feature_tasks_field_email
        ProfileField.Field.Mobile -> R.string.feature_tasks_field_mobile
        ProfileField.Field.Waiver -> R.string.feature_tasks_field_waiver
        ProfileField.Field.WaiverId -> R.string.feature_tasks_field_waiver_id
        ProfileField.Field.Ssn -> R.string.feature_tasks_field_ssn
        ProfileField.Field.Certification -> R.string.feature_tasks_field_certification
        ProfileField.Field.CpocStart -> R.string.feature_tasks_field_cpoc_start
        ProfileField.Field.CpocEnd -> R.string.feature_tasks_field_cpoc_end
    },
)

internal fun RequestKind.reasonLabel(): Int = when (this) {
    RequestKind.Delay -> R.string.feature_tasks_review_delay_reason
    RequestKind.Closure -> R.string.feature_tasks_review_closure_reason
    else -> R.string.feature_tasks_review_reason_code
}

/** "Today, 9:12 AM" reads "today, 9:12 AM" after "Requested". */
internal fun String.lowercaseFirstWord(): String = replaceFirstChar { it.lowercaseChar() }

private const val NOTE_LINES = 4
private const val SIDE_BY_SIDE_CHARS = 12
private const val BALANCE_SKELETON = 0.5f

/**
 * sprout_web `ExternalTrainingReview`'s confirm step: rejecting always asks why, so the employee
 * can fix the certificate; approving asks only when the reviewer uploaded it themselves.
 */
@Composable
internal fun TrainingDecisionSheet(
    title: String,
    message: String,
    approve: Boolean,
    credit: TrainingCredit,
    reason: String,
    canConfirm: Boolean,
    onReason: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    WaiveraBottomSheet(onDismissRequest = onDismiss) {
        TrainingDecisionContent(title, message, approve, credit, reason, canConfirm, onReason, onConfirm, onDismiss)
    }
}

@Composable
internal fun TrainingDecisionContent(
    title: String,
    message: String,
    approve: Boolean,
    credit: TrainingCredit,
    reason: String,
    canConfirm: Boolean,
    onReason: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = WaiveraTheme.colors
    val required = !approve || credit.needsApproveReason
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.MediumSmall),
    ) {
        WaiveraIconTile(
            icon = if (approve) WaiveraIcons.Check else WaiveraIcons.Close,
            tone = if (approve) WaiveraTileTone.Brand else WaiveraTileTone.Danger,
            size = WaiveraSizes.CardTile,
        )
        Text(title, style = MaterialTheme.typography.titleLarge, color = colors.text)
        Text(message, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
        if (approve && credit.needsApproveReason) {
            InfoNote(stringResource(R.string.feature_tasks_review_uploaded_yourself, credit.employeeName))
        }
        WaiveraTextField(
            value = reason,
            onValueChange = onReason,
            label = stringResource(
                if (required) {
                    R.string.feature_tasks_review_reason_required
                } else {
                    R.string.feature_tasks_review_reason_optional
                },
            ),
            placeholder = stringResource(R.string.feature_tasks_review_reason_placeholder),
            singleLine = false,
            minLines = REASON_LINES,
        )
        WaiveraButton(
            text = stringResource(
                if (approve) {
                    R.string.feature_tasks_review_approve_credit
                } else {
                    R.string.feature_tasks_review_reject_submission
                },
            ),
            onClick = onConfirm,
            enabled = canConfirm,
            variant = if (approve) WaiveraButtonVariant.Primary else WaiveraButtonVariant.Destructive,
            leadingIcon = if (approve) WaiveraIcons.Check else WaiveraIcons.Close,
            fullWidth = true,
            size = WaiveraButtonSize.Large,
        )
        WaiveraButton(
            text = stringResource(com.waivera.waivera.core.designsystem.R.string.core_designsystem_cancel),
            onClick = onDismiss,
            variant = WaiveraButtonVariant.Secondary,
            fullWidth = true,
            size = WaiveraButtonSize.Large,
        )
    }
}

private const val REASON_LINES = 3

/** "Tue, Nov 24 – Thu, Nov 26", or the one day or time alone. */
@Composable
internal fun spanText(span: TaskFormat.Span): String =
    span.end?.let { stringResource(R.string.feature_tasks_span, span.start, it) } ?: span.start
