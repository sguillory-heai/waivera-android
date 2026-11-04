package com.waivera.waivera.feature.calendar.form

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.waivera.waivera.core.data.model.AttendeeOption
import com.waivera.waivera.core.designsystem.component.BannerTone
import com.waivera.waivera.core.designsystem.component.WaiveraAvatar
import com.waivera.waivera.core.designsystem.component.WaiveraAvatarColors
import com.waivera.waivera.core.designsystem.component.WaiveraBanner
import com.waivera.waivera.core.designsystem.component.WaiveraBottomSheet
import com.waivera.waivera.core.designsystem.component.WaiveraButton
import com.waivera.waivera.core.designsystem.component.WaiveraButtonSize
import com.waivera.waivera.core.designsystem.component.WaiveraButtonVariant
import com.waivera.waivera.core.designsystem.component.WaiveraDatePickerDialog
import com.waivera.waivera.core.designsystem.component.WaiveraIconButton
import com.waivera.waivera.core.designsystem.component.WaiveraIconTile
import com.waivera.waivera.core.designsystem.component.WaiveraPickerField
import com.waivera.waivera.core.designsystem.component.WaiveraSheetFrame
import com.waivera.waivera.core.designsystem.component.WaiveraSwitchRow
import com.waivera.waivera.core.designsystem.component.WaiveraTextField
import com.waivera.waivera.core.designsystem.component.WaiveraTileTone
import com.waivera.waivera.core.designsystem.component.WaiveraTimePickerDialog
import com.waivera.waivera.core.designsystem.icon.WaiveraIcons
import com.waivera.waivera.core.designsystem.preview.WaiveraPreviews
import com.waivera.waivera.core.designsystem.theme.WaiveraElevation
import com.waivera.waivera.core.designsystem.theme.WaiveraShapes
import com.waivera.waivera.core.designsystem.theme.WaiveraSizes
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTextStyles
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.core.designsystem.theme.isLargeFontScale
import com.waivera.waivera.feature.calendar.R
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * The New event / Edit event sheet. [onSaved] runs once the server has the event (true for a new
 * one), so the caller can confirm it; [onWebUnavailable] explains a build without the web app.
 */
@Composable
fun EventFormSheet(
    request: EventFormRequest,
    onDismiss: () -> Unit,
    onSaved: (created: Boolean) -> Unit,
    onWebUnavailable: () -> Unit,
) {
    val viewModel = hiltViewModel<EventFormViewModel, EventFormViewModel.Factory>(
        key = "event-form-${request.eventId}-${request.date}-${request.session}",
        creationCallback = { factory -> factory.create(request) },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    val saved by rememberUpdatedState(onSaved)
    val webUnavailable by rememberUpdatedState(onWebUnavailable)
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is EventFormEffect.Saved -> saved(effect.created)
                is EventFormEffect.OpenWeb -> if (runCatching {
                        uriHandler.openUri(effect.url)
                    }.isFailure
                ) {
                    webUnavailable()
                }
                EventFormEffect.WebUnavailable -> webUnavailable()
            }
        }
    }
    WaiveraBottomSheet(
        onDismissRequest = { if (!state.isSaving) onDismiss() },
        title = stringResource(
            if (request.eventId ==
                null
            ) {
                R.string.feature_calendar_new_event
            } else {
                R.string.feature_calendar_edit_event
            },
        ),
    ) {
        EventFormFields(
            state = state,
            actions = EventFormActions(
                onTitle = viewModel::setTitle,
                onAllDay = viewModel::setAllDay,
                onDate = viewModel::setDate,
                onStart = viewModel::setStart,
                onEnd = viewModel::setEnd,
                onQuery = viewModel::setQuery,
                onInvite = viewModel::invite,
                onUninvite = viewModel::uninvite,
                onNotifyParticipants = viewModel::setNotifyParticipants,
                onRetryDirectory = viewModel::loadDirectory,
                onVideo = viewModel::setVideo,
                onLocation = viewModel::setLocation,
                onDescription = viewModel::setDescription,
                onOpenWeb = viewModel::openWeb,
                onSave = viewModel::save,
            ),
        )
    }
}

internal data class EventFormActions(
    val onTitle: (String) -> Unit = {},
    val onAllDay: (Boolean) -> Unit = {},
    val onDate: (LocalDate) -> Unit = {},
    val onStart: (LocalTime) -> Unit = {},
    val onEnd: (LocalTime) -> Unit = {},
    val onQuery: (String) -> Unit = {},
    val onInvite: (AttendeeOption) -> Unit = {},
    val onUninvite: (AttendeeOption) -> Unit = {},
    val onNotifyParticipants: (Boolean) -> Unit = {},
    val onRetryDirectory: () -> Unit = {},
    val onVideo: (Boolean) -> Unit = {},
    val onLocation: (String) -> Unit = {},
    val onDescription: (String) -> Unit = {},
    val onOpenWeb: () -> Unit = {},
    val onSave: () -> Unit = {},
)

private enum class Picking { None, Day, Start, End }

@Composable
internal fun ColumnScope.EventFormFields(state: EventFormUiState, actions: EventFormActions) {
    when (state.phase) {
        FormPhase.Loading -> {
            Text(
                text = stringResource(R.string.feature_calendar_form_loading),
                style = MaterialTheme.typography.bodyMedium,
                color = WaiveraTheme.colors.textSecondary,
            )
            return
        }
        FormPhase.Unavailable -> {
            Text(
                text = stringResource(R.string.feature_calendar_form_unavailable),
                style = MaterialTheme.typography.bodyMedium,
                color = WaiveraTheme.colors.textSecondary,
            )
            return
        }
        FormPhase.Ready -> Unit
    }
    var picking by remember { mutableStateOf(Picking.None) }
    val locale = formLocale()
    val dayFormat = remember(locale) { DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", locale) }
    val enabled = !state.isSaving
    Column(
        modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.MediumSmall),
    ) {
        if (state.saveFailed) {
            WaiveraBanner(message = stringResource(R.string.feature_calendar_form_save_failed), tone = BannerTone.Error)
        }
        WaiveraTextField(
            value = state.title,
            onValueChange = actions.onTitle,
            label = stringResource(R.string.feature_calendar_form_title),
            placeholder = stringResource(R.string.feature_calendar_form_title_placeholder),
            enabled = enabled,
            errorText = stringResource(R.string.feature_calendar_form_title_required).takeIf {
                state.showErrors &&
                    state.titleMissing
            },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        )
        WaiveraSwitchRow(
            title = stringResource(R.string.feature_calendar_form_all_day),
            checked = state.allDay,
            onCheckedChange = actions.onAllDay,
            enabled = enabled,
            modifier = Modifier.bleedGutter(),
        )
        WaiveraPickerField(
            label = stringResource(R.string.feature_calendar_form_day),
            value = state.date.format(dayFormat),
            placeholder = stringResource(R.string.feature_calendar_form_day_placeholder),
            icon = WaiveraIcons.Calendar,
            enabled = enabled,
            onClick = { picking = Picking.Day },
        )
        if (!state.allDay) TimeFields(state, enabled) { picking = it }
        Text(
            text = stringResource(
                R.string.feature_calendar_form_zone_note,
                state.zone.getDisplayName(TextStyle.FULL, locale),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = WaiveraTheme.colors.textSecondary,
        )
        Invitees(state, actions, enabled)
        WaiveraSwitchRow(
            title = stringResource(R.string.feature_calendar_form_video),
            subtitle = stringResource(R.string.feature_calendar_form_video_subtitle),
            checked = state.waiveraVideo,
            onCheckedChange = actions.onVideo,
            enabled = enabled,
            modifier = Modifier.bleedGutter(),
            leading = {
                WaiveraIconTile(
                    icon = WaiveraIcons.Video,
                    tone = WaiveraTileTone.Brand,
                    size = WaiveraSizes.FieldTile,
                    iconSize = WaiveraSizes.FieldTileGlyph,
                )
            },
        )
        if (!state.waiveraVideo) {
            WaiveraTextField(
                value = state.location,
                onValueChange = actions.onLocation,
                label = stringResource(R.string.feature_calendar_form_location),
                placeholder = stringResource(R.string.feature_calendar_form_location_placeholder),
                enabled = enabled,
            )
        }
        WaiveraTextField(
            value = state.description,
            onValueChange = actions.onDescription,
            label = stringResource(R.string.feature_calendar_form_notes),
            placeholder = stringResource(R.string.feature_calendar_form_notes_placeholder),
            singleLine = false,
            minLines = 3,
            enabled = enabled,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        )
        if (!state.isEdit) {
            WaiveraButton(
                text = stringResource(R.string.feature_calendar_form_on_web),
                onClick = actions.onOpenWeb,
                variant = WaiveraButtonVariant.Text,
                leadingIcon = WaiveraIcons.OpenExternal,
                fullWidth = true,
            )
        }
    }
    WaiveraButton(
        text = stringResource(
            if (state.isEdit) R.string.feature_calendar_form_save else R.string.feature_calendar_form_create,
        ),
        onClick = actions.onSave,
        fullWidth = true,
        size = WaiveraButtonSize.Large,
        loadingText = stringResource(R.string.feature_calendar_form_saving).takeIf { state.isSaving },
    )
    when (picking) {
        Picking.None -> Unit
        Picking.Day -> WaiveraDatePickerDialog(
            initial = state.date,
            onPicked = actions.onDate,
            onDismiss = { picking = Picking.None },
        )
        Picking.Start -> WaiveraTimePickerDialog(
            initial = state.start,
            title = stringResource(R.string.feature_calendar_form_starts),
            onPicked = actions.onStart,
            onDismiss = { picking = Picking.None },
        )
        Picking.End -> WaiveraTimePickerDialog(
            initial = state.end,
            title = stringResource(R.string.feature_calendar_form_ends),
            onPicked = actions.onEnd,
            onDismiss = { picking = Picking.None },
        )
    }
}

@Composable
private fun TimeFields(state: EventFormUiState, enabled: Boolean, onPick: (Picking) -> Unit) {
    val placeholder = stringResource(R.string.feature_calendar_form_time_placeholder)
    val locale = formLocale()
    val timeFormat = remember(locale) { DateTimeFormatter.ofPattern("h:mm a", locale) }
    val start = @Composable { modifier: Modifier ->
        WaiveraPickerField(
            label = stringResource(R.string.feature_calendar_form_starts),
            value = state.start.format(timeFormat),
            placeholder = placeholder,
            icon = WaiveraIcons.Schedule,
            enabled = enabled,
            onClick = { onPick(Picking.Start) },
            modifier = modifier,
        )
    }
    val end = @Composable { modifier: Modifier ->
        WaiveraPickerField(
            label = stringResource(R.string.feature_calendar_form_ends),
            value = state.end.format(timeFormat),
            placeholder = placeholder,
            icon = WaiveraIcons.Schedule,
            enabled = enabled,
            errorText = stringResource(R.string.feature_calendar_form_end_before_start).takeIf { state.endBeforeStart },
            onClick = { onPick(Picking.End) },
            modifier = modifier,
        )
    }
    if (isLargeFontScale()) {
        start(Modifier.fillMaxWidth())
        end(Modifier.fillMaxWidth())
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.MediumSmall)) {
            start(Modifier.weight(1f))
            end(Modifier.weight(1f))
        }
    }
}

@Composable
private fun Invitees(state: EventFormUiState, actions: EventFormActions, enabled: Boolean) {
    WaiveraTextField(
        value = state.query,
        onValueChange = actions.onQuery,
        label = stringResource(R.string.feature_calendar_form_invite),
        placeholder = stringResource(R.string.feature_calendar_form_invite_placeholder),
        enabled = enabled,
        leadingIcon = {
            Icon(
                painterResource(WaiveraIcons.Search),
                contentDescription = null,
                tint = WaiveraTheme.colors.textSecondary,
            )
        },
    )
    if (state.directoryFailed) {
        WaiveraBanner(
            message = stringResource(R.string.feature_calendar_form_directory_failed),
            tone = BannerTone.Warning,
            actionLabel = stringResource(R.string.feature_calendar_retry),
            onAction = actions.onRetryDirectory,
        )
    }
    if (state.query.isNotBlank()) {
        if (state.matches.isEmpty() && !state.directoryFailed) {
            Text(
                text = stringResource(R.string.feature_calendar_form_invite_none),
                style = MaterialTheme.typography.bodyMedium,
                color = WaiveraTheme.colors.textSecondary,
            )
        }
        if (state.matches.isNotEmpty()) MatchList(state.matches, enabled, actions.onInvite)
    }
    InviteeGroups(state.invitees, enabled, actions.onUninvite)
    // Like a calendar's "send invitations" choice: participants are only emailed when asked.
    AnimatedVisibility(
        visible = state.hasParticipants,
        enter = expandVertically(WaiveraTheme.motion.layout(), expandFrom = Alignment.Top) +
            fadeIn(WaiveraTheme.motion.state()),
        exit = shrinkVertically(WaiveraTheme.motion.layout(), shrinkTowards = Alignment.Top) +
            fadeOut(WaiveraTheme.motion.state()),
    ) {
        WaiveraSwitchRow(
            title = stringResource(R.string.feature_calendar_form_notify_participants),
            subtitle = stringResource(
                if (state.isEdit) {
                    R.string.feature_calendar_form_notify_participants_edit
                } else {
                    R.string.feature_calendar_form_notify_participants_note
                },
            ),
            checked = state.notifyParticipants,
            onCheckedChange = actions.onNotifyParticipants,
            enabled = enabled,
            modifier = Modifier.bleedGutter(),
        )
    }
}

/** Search results float over the form as one card, so they read as choices, not as invitees. */
@Composable
private fun MatchList(matches: List<AttendeeOption>, enabled: Boolean, onInvite: (AttendeeOption) -> Unit) {
    val colors = WaiveraTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(WaiveraElevation.Card, WaiveraShapes.Field, clip = false)
            .background(colors.surface, WaiveraShapes.Field)
            .border(WaiveraSpacing.Hairline, colors.border, WaiveraShapes.Field)
            .padding(horizontal = WaiveraSpacing.MediumSmall, vertical = WaiveraSpacing.Small),
        verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.Small),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.Small),
        ) {
            Icon(
                painterResource(WaiveraIcons.Search),
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier.size(WaiveraSizes.FieldTileGlyph),
            )
            Text(
                pluralStringResource(R.plurals.feature_calendar_form_matches, matches.size, matches.size),
                style = WaiveraTextStyles.FieldLabel,
                color = colors.textSecondary,
            )
        }
        matches.forEach { person ->
            InviteeRow(person, enabled, adding = true) { onInvite(person) }
        }
    }
}

/**
 * Invited people in two groups: coworkers on a violet tint and participants on a teal one, so
 * who is staff and who is a client reads at a glance without a tag on every row.
 */
@Composable
private fun InviteeGroups(invitees: List<AttendeeOption>, enabled: Boolean, onRemove: (AttendeeOption) -> Unit) {
    val colors = WaiveraTheme.colors
    val (participants, coworkers) = invitees.partition { it.participant }
    listOf(
        Triple(R.string.feature_calendar_form_group_coworkers, coworkers, false),
        Triple(R.string.feature_calendar_form_group_participants, participants, true),
    ).filter { it.second.isNotEmpty() }.forEach { (title, people, isParticipant) ->
        val tile = if (isParticipant) colors.tiles.teal else colors.tiles.brand
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(WaiveraTheme.motion.layout())
                .background(tile.container.copy(alpha = GROUP_TINT_ALPHA), WaiveraShapes.Field)
                .padding(horizontal = WaiveraSpacing.MediumSmall, vertical = WaiveraSpacing.Small),
        ) {
            Row(
                modifier = Modifier.semantics(mergeDescendants = true) {},
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.Small),
            ) {
                Icon(
                    painterResource(if (isParticipant) WaiveraIcons.Person else WaiveraIcons.Building),
                    contentDescription = null,
                    tint = tile.content,
                    modifier = Modifier.size(WaiveraSizes.FieldTileGlyph),
                )
                Text(
                    stringResource(R.string.feature_calendar_form_group_count, stringResource(title), people.size),
                    style = WaiveraTextStyles.FieldLabel,
                    color = tile.content,
                )
            }
            people.forEach { person ->
                key(person.key) { InviteeRow(person, enabled, adding = false) { onRemove(person) } }
            }
        }
    }
}

@Composable
private fun InviteeRow(person: AttendeeOption, enabled: Boolean, adding: Boolean, onAction: () -> Unit) {
    val colors = WaiveraTheme.colors
    val teal = colors.tiles.teal
    val large = isLargeFontScale()
    val actionLabel = stringResource(
        if (adding) R.string.feature_calendar_form_invite_add else R.string.feature_calendar_form_invite_remove,
        person.name,
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = if (large) WaiveraSpacing.ExtraSmall else WaiveraSpacing.XxSmall),
        // A wrapped email at large font keeps the avatar on the name line.
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.MediumSmall),
    ) {
        WaiveraAvatar(
            name = person.name,
            size = WaiveraSizes.AvatarSmall,
            contentDescription = null,
            modifier = Modifier.padding(top = WaiveraSpacing.XxSmall),
            colors = if (person.participant) {
                WaiveraAvatarColors(
                    teal.container,
                    teal.content,
                )
            } else {
                WaiveraAvatarColors.default()
            },
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = person.name,
                style = MaterialTheme.typography.titleSmall,
                color = colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (adding) {
                Text(
                    stringResource(
                        if (person.participant) {
                            R.string.feature_calendar_form_kind_participant
                        } else {
                            R.string.feature_calendar_form_kind_coworker
                        },
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (person.participant) teal.content else colors.textSecondary,
                )
            }
            person.email?.let { email ->
                Text(
                    // Break opportunities after "@" and never before a dot, so a long address wraps cleanly.
                    email.replace("@", "@\u200B").replace(".", "\u2060."),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        WaiveraIconButton(
            icon = if (adding) WaiveraIcons.Add else WaiveraIcons.Close,
            contentDescription = actionLabel,
            onClick = onAction,
            enabled = enabled,
            tint = if (adding) colors.accent else colors.textSecondary,
            modifier = Modifier.align(Alignment.CenterVertically),
        )
    }
}

private const val GROUP_TINT_ALPHA = 0.35f

private fun Modifier.bleedGutter(): Modifier = layout { measurable, constraints ->
    val extra = WaiveraSpacing.Gutter.roundToPx() * 2
    val placeable = measurable.measure(
        constraints.copy(minWidth = constraints.minWidth + extra, maxWidth = constraints.maxWidth + extra),
    )
    layout(constraints.maxWidth, placeable.height) { placeable.place(-extra / 2, 0) }
}

/** "Wed, Nov 4, 2026" and "3:00 PM": 12-hour clock times, as everywhere in the app (pitfall 39). */
@Composable
private fun formLocale(): Locale = LocalConfiguration.current.locales[0]

@WaiveraPreviews
@Composable
private fun EventFormPreview() {
    WaiveraTheme {
        WaiveraSheetFrame(title = stringResource(R.string.feature_calendar_new_event)) {
            EventFormFields(
                state = EventFormUiState(
                    phase = FormPhase.Ready,
                    title = "Team huddle",
                    date = LocalDate.of(2026, 11, 4),
                    start = LocalTime.of(15, 0),
                    end = LocalTime.of(15, 30),
                    zone = ZoneId.of("America/Chicago"),
                    invitees = listOf(
                        AttendeeOption("u1", "Priya Raman", null),
                        AttendeeOption("u2", "Jordan Lee", null),
                    ),
                ),
                actions = EventFormActions(),
            )
        }
    }
}
