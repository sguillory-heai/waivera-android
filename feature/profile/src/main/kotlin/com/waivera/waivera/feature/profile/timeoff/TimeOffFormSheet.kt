package com.waivera.waivera.feature.profile.timeoff

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.waivera.waivera.core.data.model.LeaveBlock
import com.waivera.waivera.core.data.model.LeaveImpact
import com.waivera.waivera.core.data.model.LeavePreview
import com.waivera.waivera.core.data.model.LeaveSendProblem
import com.waivera.waivera.core.data.model.LeaveType
import com.waivera.waivera.core.designsystem.component.BannerTone
import com.waivera.waivera.core.designsystem.component.SkeletonText
import com.waivera.waivera.core.designsystem.component.WaiveraBanner
import com.waivera.waivera.core.designsystem.component.WaiveraBottomSheet
import com.waivera.waivera.core.designsystem.component.WaiveraButton
import com.waivera.waivera.core.designsystem.component.WaiveraButtonSize
import com.waivera.waivera.core.designsystem.component.WaiveraDatePickerDialog
import com.waivera.waivera.core.designsystem.component.WaiveraPickerField
import com.waivera.waivera.core.designsystem.component.WaiveraSegmentedControl
import com.waivera.waivera.core.designsystem.component.WaiveraTextField
import com.waivera.waivera.core.designsystem.component.WaiveraTimePickerDialog
import com.waivera.waivera.core.designsystem.component.rememberSkeletonPulse
import com.waivera.waivera.core.designsystem.icon.WaiveraIcons
import com.waivera.waivera.core.designsystem.theme.WaiveraShapes
import com.waivera.waivera.core.designsystem.theme.WaiveraSizes
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTextStyles
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.feature.profile.R
import java.text.NumberFormat
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

/** What the form's controls call; the sheet wires them to [TimeOffFormViewModel]. */
internal data class TimeOffFormActions(
    val onPickType: () -> Unit = {},
    val onPartialDay: (Boolean) -> Unit = {},
    val onPickFirstDay: () -> Unit = {},
    val onPickLastDay: () -> Unit = {},
    val onPickDay: () -> Unit = {},
    val onPickFrom: () -> Unit = {},
    val onPickTo: () -> Unit = {},
    val onNote: (String) -> Unit = {},
    val onRetryTypes: () -> Unit = {},
    val onRetryCheck: () -> Unit = {},
    val onSend: () -> Unit = {},
)

private enum class Picking { Type, FirstDay, LastDay, Day, From, To }

/** Request time off, opened from the profile. The profile owns [viewModel] and its effects. */
@Composable
internal fun TimeOffFormSheet(viewModel: TimeOffFormViewModel, onDismiss: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var picking by rememberSaveable { mutableStateOf<Picking?>(null) }
    WaiveraBottomSheet(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.feature_profile_time_off_request),
    ) {
        TimeOffFormContent(
            state = state,
            actions = TimeOffFormActions(
                onPickType = { picking = Picking.Type },
                onPartialDay = viewModel::setPartialDay,
                onPickFirstDay = { picking = Picking.FirstDay },
                onPickLastDay = { picking = Picking.LastDay },
                onPickDay = { picking = Picking.Day },
                onPickFrom = { picking = Picking.From },
                onPickTo = { picking = Picking.To },
                onNote = viewModel::setNote,
                onRetryTypes = viewModel::loadTypes,
                onRetryCheck = viewModel::retryCheck,
                onSend = viewModel::send,
            ),
        )
    }
    Pickers(picking = picking, state = state, viewModel = viewModel, onDone = { picking = null })
}

@Composable
private fun Pickers(picking: Picking?, state: TimeOffFormState, viewModel: TimeOffFormViewModel, onDone: () -> Unit) {
    val today = state.today
    when (picking) {
        null -> Unit
        Picking.Type -> LeaveTypeSheet(
            types = (state.types as? LeaveTypes.Ready)?.types.orEmpty(),
            selectedId = state.typeId,
            onPick = {
                viewModel.pickType(it)
                onDone()
            },
            onDismiss = onDone,
        )
        Picking.FirstDay -> WaiveraDatePickerDialog(
            initial = state.firstDay ?: today,
            onPicked = viewModel::pickFirstDay,
            onDismiss = onDone,
        )
        Picking.LastDay -> WaiveraDatePickerDialog(
            initial = state.lastDay ?: state.firstDay ?: today,
            onPicked = viewModel::pickLastDay,
            onDismiss = onDone,
            earliest = state.firstDay,
        )
        Picking.Day -> WaiveraDatePickerDialog(
            initial = state.day ?: today,
            onPicked = viewModel::pickDay,
            onDismiss = onDone,
        )
        Picking.From -> WaiveraTimePickerDialog(
            initial = state.from ?: DEFAULT_FROM,
            title = stringResource(R.string.feature_profile_time_off_pick_from),
            onPicked = viewModel::pickFrom,
            onDismiss = onDone,
        )
        Picking.To -> WaiveraTimePickerDialog(
            initial = state.to ?: state.from?.plusHours(1) ?: DEFAULT_TO,
            title = stringResource(R.string.feature_profile_time_off_pick_to),
            onPicked = viewModel::pickTo,
            onDismiss = onDone,
        )
    }
}

/**
 * The form: leave type, full or partial day, dates, what the server says about them, a note,
 * then Send pinned under it all so a long note never pushes it out of reach.
 */
@Composable
internal fun TimeOffFormContent(state: TimeOffFormState, actions: TimeOffFormActions) {
    Column(verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.MediumSmall)) {
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.MediumSmall),
        ) {
            TypeField(state, actions)
            WaiveraSegmentedControl(
                options = listOf(
                    stringResource(R.string.feature_profile_time_off_full_day),
                    stringResource(R.string.feature_profile_time_off_partial_day),
                ),
                selectedIndex = if (state.partialDay) 1 else 0,
                onSelect = { actions.onPartialDay(it == 1) },
            )
            if (state.partialDay) PartialDayFields(state, actions) else FullDayFields(state, actions)
            Findings(state, actions)
            WaiveraTextField(
                value = state.note,
                onValueChange = actions.onNote,
                label = stringResource(R.string.feature_profile_time_off_note),
                placeholder = stringResource(R.string.feature_profile_time_off_note_hint),
                singleLine = false,
                minLines = 2,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
        }
        state.failure?.let {
            WaiveraBanner(message = stringResource(it.problem.text()), tone = BannerTone.Error, gutter = false)
        }
        WaiveraButton(
            text = stringResource(R.string.feature_profile_time_off_send),
            onClick = actions.onSend,
            enabled = state.canSend,
            fullWidth = true,
            size = WaiveraButtonSize.Large,
            loadingText = stringResource(R.string.feature_profile_time_off_sending).takeIf { state.sending },
        )
    }
}

@Composable
private fun TypeField(state: TimeOffFormState, actions: TimeOffFormActions) {
    when (state.types) {
        LeaveTypes.Loading -> SkeletonText(
            style = WaiveraTextStyles.FieldValue,
            widthFraction = TYPE_SKELETON,
            pulse = rememberSkeletonPulse(),
        )
        LeaveTypes.Failed -> WaiveraBanner(
            message = stringResource(R.string.feature_profile_time_off_types_failed),
            tone = BannerTone.Error,
            actionLabel = stringResource(R.string.feature_profile_time_off_try_again),
            onAction = actions.onRetryTypes,
            actionIcon = WaiveraIcons.Refresh,
            gutter = false,
        )
        is LeaveTypes.Ready -> {
            val type = state.type
            WaiveraPickerField(
                label = stringResource(R.string.feature_profile_time_off_leave_type),
                value = type?.let {
                    stringResource(R.string.feature_profile_time_off_type_value, it.name, balanceText(it))
                },
                placeholder = stringResource(R.string.feature_profile_time_off_pick_type),
                icon = WaiveraIcons.ExpandMore,
                onClick = actions.onPickType,
            )
        }
    }
}

@Composable
private fun FullDayFields(state: TimeOffFormState, actions: TimeOffFormActions) {
    Row(horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.Small)) {
        WaiveraPickerField(
            label = stringResource(R.string.feature_profile_time_off_first_day),
            value = state.firstDay?.let { dayText(it, state.today) },
            placeholder = stringResource(R.string.feature_profile_time_off_pick_start),
            icon = WaiveraIcons.Calendar,
            onClick = actions.onPickFirstDay,
            modifier = Modifier.weight(1f),
        )
        WaiveraPickerField(
            label = stringResource(R.string.feature_profile_time_off_last_day),
            value = state.lastDay?.let { dayText(it, state.today) },
            placeholder = stringResource(R.string.feature_profile_time_off_pick_end),
            icon = WaiveraIcons.Calendar,
            onClick = actions.onPickLastDay,
            modifier = Modifier.weight(1f),
            errorText = stringResource(R.string.feature_profile_time_off_issue_order)
                .takeIf { state.issue == LeaveIssue.EndBeforeStart },
        )
    }
}

@Composable
private fun PartialDayFields(state: TimeOffFormState, actions: TimeOffFormActions) {
    WaiveraPickerField(
        label = stringResource(R.string.feature_profile_time_off_date),
        value = state.day?.let { dayText(it, state.today) },
        placeholder = stringResource(R.string.feature_profile_time_off_pick_date),
        icon = WaiveraIcons.Calendar,
        onClick = actions.onPickDay,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.Small)) {
        WaiveraPickerField(
            label = stringResource(R.string.feature_profile_time_off_from),
            value = state.from?.let { timeText(it) },
            placeholder = stringResource(R.string.feature_profile_time_off_pick_start),
            icon = WaiveraIcons.Schedule,
            onClick = actions.onPickFrom,
            modifier = Modifier.weight(1f),
        )
        WaiveraPickerField(
            label = stringResource(R.string.feature_profile_time_off_to),
            value = state.to?.let { timeText(it) },
            placeholder = stringResource(R.string.feature_profile_time_off_pick_end),
            icon = WaiveraIcons.Schedule,
            onClick = actions.onPickTo,
            modifier = Modifier.weight(1f),
        )
    }
}

/** The local issue, the past-date note, then what the server said: blocked, balance, impact. */
@Composable
private fun Findings(state: TimeOffFormState, actions: TimeOffFormActions) {
    when (state.issue) {
        LeaveIssue.TooShort, LeaveIssue.TooLong -> WaiveraBanner(
            title = stringResource(R.string.feature_profile_time_off_issue_title),
            message = stringResource(
                if (state.issue == LeaveIssue.TooShort) {
                    R.string.feature_profile_time_off_issue_short
                } else {
                    R.string.feature_profile_time_off_issue_long
                },
            ),
            tone = BannerTone.Error,
            gutter = false,
        )
        else -> Unit
    }
    if (state.past) {
        WaiveraBanner(
            message = stringResource(R.string.feature_profile_time_off_past),
            tone = BannerTone.Warning,
            gutter = false,
        )
    }
    when (val check = state.check) {
        LeaveCheck.Idle -> Unit
        LeaveCheck.Checking -> Text(
            text = stringResource(R.string.feature_profile_time_off_checking),
            style = MaterialTheme.typography.bodySmall,
            color = WaiveraTheme.colors.textSecondary,
        )
        LeaveCheck.Failed -> WaiveraBanner(
            message = stringResource(R.string.feature_profile_time_off_check_failed),
            tone = BannerTone.Error,
            actionLabel = stringResource(R.string.feature_profile_time_off_try_again),
            onAction = actions.onRetryCheck,
            actionIcon = WaiveraIcons.Refresh,
            gutter = false,
        )
        is LeaveCheck.Ready -> PreviewFindings(check.preview, state.overBalance)
    }
}

@Composable
private fun PreviewFindings(preview: LeavePreview, overBalance: Boolean) {
    val block = preview.block
    if (block != null) {
        WaiveraBanner(
            title = stringResource(R.string.feature_profile_time_off_block_title),
            message = stringResource(block.text()),
            tone = BannerTone.Error,
            gutter = false,
        )
        return
    }
    BalanceGroup(preview)
    if (overBalance) {
        WaiveraBanner(
            title = stringResource(R.string.feature_profile_time_off_over_title),
            message = stringResource(
                R.string.feature_profile_time_off_over,
                hoursText(preview.requestedMinutes),
                hoursText(preview.beforeMinutes ?: 0),
            ),
            tone = BannerTone.Error,
            gutter = false,
        )
    }
    preview.impact?.let { ImpactBanner(it) }
}

@Composable
private fun BalanceGroup(preview: LeavePreview) {
    val colors = WaiveraTheme.colors
    val before = preview.beforeMinutes
    val after = preview.afterMinutes
    FormGroup {
        if (preview.tracksBalance && before != null && after != null) {
            val spoken = stringResource(
                R.string.feature_profile_time_off_balance_spoken,
                hoursText(before),
                hoursText(after),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clearAndSetSemantics { contentDescription = spoken },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.Small),
            ) {
                Figure(stringResource(R.string.feature_profile_time_off_now), hoursText(before), Modifier.weight(1f))
                Text(
                    text = stringResource(R.string.feature_profile_time_off_arrow),
                    style = WaiveraTextStyles.FieldValue,
                    color = colors.textSecondary,
                )
                // Below zero reads in red, as on the web.
                Figure(
                    label = stringResource(R.string.feature_profile_time_off_after),
                    value = hoursText(after),
                    modifier = Modifier.weight(1f),
                    valueColor = if (after < 0) colors.dangerText else colors.accent,
                )
            }
        }
        Text(
            text = stringResource(R.string.feature_profile_time_off_in_request, hoursText(preview.requestedMinutes)),
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary,
        )
    }
}

@Composable
private fun Figure(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = WaiveraTheme.colors.text,
) {
    Column(modifier = modifier) {
        Text(text = label, style = WaiveraTextStyles.Kicker, color = WaiveraTheme.colors.textSecondary)
        Text(text = value, style = WaiveraTextStyles.FieldValue, color = valueColor)
    }
}

/** sprout_web `LeaveImpactSummary` in the request modal, with its advisory line. */
@Composable
private fun ImpactBanner(impact: LeaveImpact) {
    val total = impact.dueBefore + impact.dueDuring
    val details = listOfNotNull(
        stringResource(R.string.feature_profile_time_off_short_notice).takeIf { impact.shortNotice },
        stringResource(R.string.feature_profile_time_off_extended_sick).takeIf { impact.extendedSick },
        stringResource(R.string.feature_profile_time_off_impact_advice),
    )
    WaiveraBanner(
        title = if (total > 0) {
            pluralStringResource(R.plurals.feature_profile_time_off_impact_tasks, total, total)
        } else {
            stringResource(R.string.feature_profile_time_off_impact_none)
        },
        message = details.joinToString(" "),
        tone = if (total > 0 || impact.shortNotice) BannerTone.Warning else BannerTone.Info,
        icon = if (total > 0) WaiveraIcons.Warning else WaiveraIcons.Check,
        gutter = false,
    )
}

/** Every leave type the agency has, searchable, each with the person's balance. */
@Composable
private fun LeaveTypeSheet(types: List<LeaveType>, selectedId: Int?, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    WaiveraBottomSheet(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.feature_profile_time_off_leave_type),
    ) {
        LeaveTypeList(types = types, selectedId = selectedId, onPick = onPick)
    }
}

@Composable
internal fun LeaveTypeList(types: List<LeaveType>, selectedId: Int?, onPick: (Int) -> Unit, initialQuery: String = "") {
    val colors = WaiveraTheme.colors
    var query by rememberSaveable { mutableStateOf(initialQuery) }
    val shown = remember(types, query) {
        val needle = query.trim()
        if (needle.isEmpty()) types else types.filter { it.name.contains(needle, ignoreCase = true) }
    }
    Column(verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.MediumSmall)) {
        if (types.size > SEARCH_FROM) {
            WaiveraTextField(
                value = query,
                onValueChange = { query = it },
                label = stringResource(R.string.feature_profile_time_off_find_type),
                placeholder = stringResource(R.string.feature_profile_time_off_search),
                leadingIcon = {
                    Icon(
                        painter = painterResource(WaiveraIcons.Search),
                        contentDescription = null,
                        tint = colors.textSecondary,
                        modifier = Modifier.size(WaiveraSizes.Icon),
                    )
                },
            )
        }
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState()),
        ) {
            FormGroup(padded = false) {
                if (shown.isEmpty()) {
                    Text(
                        text = stringResource(R.string.feature_profile_time_off_no_match),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(WaiveraSpacing.Medium),
                    )
                }
                shown.forEach { type -> LeaveTypeRow(type, selected = type.id == selectedId, onPick = onPick) }
            }
        }
    }
}

@Composable
private fun LeaveTypeRow(type: LeaveType, selected: Boolean, onPick: (Int) -> Unit) {
    val colors = WaiveraTheme.colors
    val empty = type.tracksBalance && (type.availableMinutes ?: 1) <= 0
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = WaiveraSpacing.MinTouchTarget)
            .semantics { this.selected = selected }
            .clickable { onPick(type.id) }
            .padding(horizontal = WaiveraSpacing.Medium, vertical = WaiveraSpacing.Small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.Small),
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = type.name, style = MaterialTheme.typography.titleSmall, color = colors.text)
            Text(
                text = balanceText(type),
                style = MaterialTheme.typography.bodySmall,
                color = if (empty) colors.dangerText else colors.textSecondary,
            )
        }
        if (selected) {
            Icon(
                painter = painterResource(WaiveraIcons.Check),
                contentDescription = stringResource(R.string.feature_profile_time_off_selected),
                tint = colors.accent,
                modifier = Modifier.size(WaiveraSizes.Icon),
            )
        }
    }
}

@Composable
private fun FormGroup(padded: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    val colors = WaiveraTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface, WaiveraShapes.SheetCard)
            .border(WaiveraSpacing.Hairline, colors.border, WaiveraShapes.SheetCard)
            .then(if (padded) Modifier.padding(WaiveraSpacing.Medium) else Modifier),
        verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.ExtraSmall),
        content = content,
    )
}

@Composable
private fun balanceText(type: LeaveType): String {
    val available = type.availableMinutes
    return when {
        !type.tracksBalance -> stringResource(R.string.feature_profile_time_off_no_balance)
        available == null -> stringResource(R.string.feature_profile_time_off_balance_unknown)
        else -> stringResource(R.string.feature_profile_time_off_available, hoursText(available))
    }
}

/** "40 hrs", "1 hr", "1.5 hrs"; negative balances keep their sign. */
@Composable
internal fun hoursText(minutes: Int): String {
    val hours = minutes / MINUTES_PER_HOUR.toDouble()
    val whole = minutes % MINUTES_PER_HOUR == 0
    val locale = LocalConfiguration.current.locales[0]
    val number = remember(minutes, locale) {
        NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 2 }.format(hours)
    }
    val quantity = if (whole && abs(hours) == 1.0) 1 else 2
    return pluralStringResource(R.plurals.feature_profile_time_off_hours, quantity, number)
}

@Composable
private fun dayText(day: LocalDate, today: LocalDate): String = remember(day, today) {
    val pattern = if (day.year == today.year) "EEE, MMM d" else "EEE, MMM d, yyyy"
    day.format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))
}

@Composable
private fun timeText(time: LocalTime): String {
    val locale = LocalConfiguration.current.locales[0]
    val format = remember(locale) { DateTimeFormatter.ofPattern("h:mm a", locale) }
    return time.format(format)
}

private fun LeaveBlock.text(): Int = when (this) {
    LeaveBlock.Weekend -> R.string.feature_profile_time_off_block_weekend
    LeaveBlock.Holiday -> R.string.feature_profile_time_off_block_holiday
    LeaveBlock.Overlap -> R.string.feature_profile_time_off_block_overlap
    LeaveBlock.Other -> R.string.feature_profile_time_off_block_other
}

private fun LeaveSendProblem?.text(): Int = when (this) {
    LeaveSendProblem.NotEnoughBalance -> R.string.feature_profile_time_off_failed_balance
    LeaveSendProblem.Probation -> R.string.feature_profile_time_off_failed_probation
    LeaveSendProblem.Weekend -> R.string.feature_profile_time_off_block_weekend
    LeaveSendProblem.Holiday -> R.string.feature_profile_time_off_block_holiday
    LeaveSendProblem.NoWorkdays -> R.string.feature_profile_time_off_failed_dates
    LeaveSendProblem.Overlap -> R.string.feature_profile_time_off_block_overlap
    LeaveSendProblem.Other, null -> R.string.feature_profile_time_off_failed
}

private const val MINUTES_PER_HOUR = 60
private const val TYPE_SKELETON = 0.6f

/** Short lists need no search field. */
private const val SEARCH_FROM = 6
private val DEFAULT_FROM: LocalTime = LocalTime.of(9, 0)
private val DEFAULT_TO: LocalTime = LocalTime.of(17, 0)
