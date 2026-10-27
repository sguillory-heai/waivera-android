package com.waivera.waivera.feature.calendar.event

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import com.waivera.waivera.core.data.model.MyInvitation
import com.waivera.waivera.core.designsystem.component.BannerTone
import com.waivera.waivera.core.designsystem.component.SkeletonLine
import com.waivera.waivera.core.designsystem.component.SkeletonListRow
import com.waivera.waivera.core.designsystem.component.WaiveraBanner
import com.waivera.waivera.core.designsystem.component.WaiveraButton
import com.waivera.waivera.core.designsystem.component.WaiveraButtonVariant
import com.waivera.waivera.core.designsystem.component.WaiveraListRow
import com.waivera.waivera.core.designsystem.component.WaiveraPressable
import com.waivera.waivera.core.designsystem.component.WaiveraSectionHeader
import com.waivera.waivera.core.designsystem.component.rememberSkeletonPulse
import com.waivera.waivera.core.designsystem.component.rememberSkeletonVisible
import com.waivera.waivera.core.designsystem.icon.WaiveraIcons
import com.waivera.waivera.core.designsystem.theme.WaiveraShapes
import com.waivera.waivera.core.designsystem.theme.WaiveraSizes
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTextStyles
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.feature.calendar.R
import com.waivera.waivera.feature.calendar.model.WebLinks
import com.waivera.waivera.feature.calendar.ui.AvatarLoader
import com.waivera.waivera.feature.calendar.ui.CalendarIcons
import com.waivera.waivera.feature.calendar.ui.CalendarSizes
import com.waivera.waivera.feature.calendar.ui.MilestoneAvatar
import com.waivera.waivera.feature.calendar.ui.clock
import com.waivera.waivera.feature.calendar.ui.eventTimeLines
import com.waivera.waivera.feature.calendar.ui.inviteStatusText
import com.waivera.waivera.feature.calendar.ui.rememberAvatarPainter
import com.waivera.waivera.feature.calendar.ui.zoneNoticeText

/** Everything the event screen can ask for. */
class EventActions(
    val onBack: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onAccept: () -> Unit = {},
    val onDecline: () -> Unit = {},
    val onRetryResponse: () -> Unit = {},
    val onJoin: () -> Unit = {},
    val onDismissJoinError: () -> Unit = {},
    val onOpenChat: () -> Unit = {},
    val onOpenPerson: (String) -> Unit = {},
)

/**
 * The loaded event: title and time first, then what the person can do (answer, join, chat),
 * then who organizes it, the details, and the attendees. The last item pads for the
 * navigation bar, since this screen has no bottom bar.
 */
@Composable
internal fun EventBody(
    details: EventDetails,
    actions: EventActions,
    modifier: Modifier = Modifier,
    avatarLoader: AvatarLoader? = null,
) {
    val event = details.event
    val navBar = WindowInsets.navigationBars.asPaddingValues()
    val sectionModifier = Modifier.padding(horizontal = WaiveraSpacing.Gutter, vertical = WaiveraSpacing.Small)
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            top = WaiveraSpacing.Small,
            bottom = navBar.calculateBottomPadding() + WaiveraSpacing.Large,
        ),
    ) {
        item(key = "title") {
            Text(
                text =
                event.title?.takeIf(String::isNotBlank) ?: stringResource(R.string.feature_calendar_untitled_event),
                style = MaterialTheme.typography.headlineSmall,
                color = WaiveraTheme.colors.text,
                modifier = sectionModifier.semantics { heading() },
            )
        }
        details.refreshError?.let {
            item(key = "refreshError") {
                WaiveraBanner(
                    message = stringResource(R.string.feature_calendar_event_refresh_failed),
                    tone = BannerTone.Error,
                    actionLabel = stringResource(R.string.feature_calendar_retry),
                    onAction = actions.onRetry,
                    modifier = sectionModifier,
                )
            }
        }
        item(key = "time") { TimeBlock(details, sectionModifier) }
        locationText(details)?.let { location ->
            item(key = "location") {
                DetailRow(
                    icon = if (event.online || event.isSprout) WaiveraIcons.Video else CalendarIcons.Place,
                    iconLabel = stringResource(
                        if (event.online || event.isSprout) {
                            R.string.feature_calendar_event_online
                        } else {
                            R.string.feature_calendar_event_location
                        },
                    ),
                    primary = location,
                    modifier = sectionModifier,
                )
            }
        }
        details.invitation?.let { invitation ->
            item(key = "invitation") { InvitationBlock(invitation, actions, sectionModifier) }
        }
        details.join?.let { join -> item(key = "join") { JoinBlock(join, details, actions, sectionModifier) } }
        details.chat?.let { chat ->
            item(key = "chat") {
                WaiveraButton(
                    text = stringResource(
                        if (chat.kind == ChatActionKind.MEETING_CHAT) {
                            R.string.feature_calendar_chat_meeting
                        } else {
                            R.string.feature_calendar_chat_organizer
                        },
                    ),
                    onClick = actions.onOpenChat,
                    variant = WaiveraButtonVariant.Secondary,
                    loading = chat.isBusy,
                    fullWidth = true,
                    leadingIcon = WaiveraIcons.Chat,
                    modifier = sectionModifier,
                )
            }
        }
        details.organizer?.let { organizer ->
            item(key = "organizer") {
                OrganizerBlock(organizer, details.isOrganizer, actions, avatarLoader, sectionModifier)
            }
        }
        event.description?.takeIf(String::isNotBlank)?.let { description ->
            item(key = "description") {
                LabeledBlock(
                    eyebrow = stringResource(R.string.feature_calendar_event_details),
                    text = description,
                    strongBorder = false,
                    modifier = sectionModifier,
                )
            }
        }
        event.taskTitle?.takeIf(String::isNotBlank)?.let { task ->
            item(key = "task") {
                LabeledBlock(
                    eyebrow = stringResource(R.string.feature_calendar_event_linked_task),
                    text = task,
                    strongBorder = true,
                    modifier = sectionModifier,
                )
            }
        }
        item(key = "attendeesHeader") { AttendeesHeader(details.attendees.size) }
        if (details.attendees.isEmpty()) {
            item(key = "noAttendees") {
                Text(
                    text = stringResource(R.string.feature_calendar_event_no_attendees),
                    style = MaterialTheme.typography.bodyMedium,
                    color = WaiveraTheme.colors.textSecondary,
                    modifier = sectionModifier,
                )
            }
        } else {
            items(details.attendees, key = { "attendee:${it.id}" }) { attendee ->
                AttendeeItem(attendee, actions, avatarLoader)
            }
        }
    }
}

/** The location line, unless it is just the meeting link (the Join button covers that). */
private fun locationText(details: EventDetails): String? {
    val event = details.event
    val location = event.location?.takeIf { it.isNotBlank() && !WebLinks.isWebUrl(it) }
    val meeting = event.meetingDetails?.takeIf { it.isNotBlank() && !WebLinks.isWebUrl(it) }
    return location ?: meeting
}

@Composable
private fun TimeBlock(details: EventDetails, modifier: Modifier = Modifier) {
    val (primary, secondary) = eventTimeLines(details.timing, details.currentYear)
    DetailRow(
        icon = WaiveraIcons.Schedule,
        iconLabel = null,
        primary = primary,
        secondary = secondary,
        tertiary = details.zoneNotice?.let { zoneNoticeText(it) },
        modifier = modifier,
    )
}

@Composable
private fun DetailRow(
    @DrawableRes icon: Int,
    iconLabel: String?,
    primary: String,
    modifier: Modifier = Modifier,
    secondary: String? = null,
    tertiary: String? = null,
) {
    val colors = WaiveraTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { },
        horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.MediumSmall),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = iconLabel,
            tint = colors.accent,
            modifier = Modifier.size(WaiveraSizes.Icon),
        )
        Column(verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.XxSmall)) {
            Text(text = primary, style = MaterialTheme.typography.titleSmall, color = colors.text)
            secondary?.let {
                Text(text = it, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
            }
            tertiary?.let {
                Text(text = it, style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
            }
        }
    }
}

/**
 * Join is a filled button only while the window is open. Before that it is an outlined, disabled
 * button under "Join opens at …"; after the meeting, or with no set time, only the reason shows.
 */
@Composable
private fun JoinBlock(join: JoinUi, details: EventDetails, actions: EventActions, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.Small)) {
        val helper = when (join.state) {
            JoinState.OPEN -> null
            JoinState.TOO_EARLY -> join.opensAt?.let {
                stringResource(R.string.feature_calendar_join_opens, clock(it.atZone(details.zone)))
            }
            JoinState.ENDED -> stringResource(R.string.feature_calendar_join_ended)
            JoinState.NO_TIME -> stringResource(R.string.feature_calendar_join_no_time)
        }
        helper?.let {
            Text(text = it, style = MaterialTheme.typography.bodyMedium, color = WaiveraTheme.colors.textSecondary)
        }
        if (join.state == JoinState.OPEN || join.state == JoinState.TOO_EARLY) {
            val open = join.state == JoinState.OPEN
            WaiveraButton(
                text = stringResource(R.string.feature_calendar_join),
                onClick = actions.onJoin,
                variant = if (open) WaiveraButtonVariant.Primary else WaiveraButtonVariant.Secondary,
                enabled = open,
                loading = join.isBusy,
                fullWidth = true,
                leadingIcon = WaiveraIcons.OpenExternal,
            )
        }
        join.error?.let { error ->
            val message = when (error) {
                JoinError.NoLink -> stringResource(R.string.feature_calendar_join_no_link)
                is JoinError.Failed -> error.error?.let { stringResource(it.messageRes) }
                    ?: stringResource(R.string.feature_calendar_join_open_failed)
            }
            WaiveraBanner(
                title = stringResource(R.string.feature_calendar_join_failed_title),
                message = message,
                tone = BannerTone.Error,
                onDismiss = actions.onDismissJoinError,
            )
        }
    }
}

@Composable
private fun InvitationBlock(invitation: InvitationUi, actions: EventActions, modifier: Modifier = Modifier) {
    val colors = WaiveraTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(WaiveraShapes.Card)
            .background(colors.surface)
            .border(WaiveraSpacing.Hairline, colors.border, WaiveraShapes.Card)
            .padding(WaiveraSpacing.MediumSmall),
        verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.Small),
    ) {
        Text(
            text = stringResource(R.string.feature_calendar_response_heading),
            style = WaiveraTextStyles.Eyebrow,
            color = colors.textSecondary,
            modifier = Modifier.semantics { heading() },
        )
        val status = when {
            invitation.phase == ResponsePhase.SAVING -> stringResource(R.string.feature_calendar_response_saving)
            !invitation.canChange && invitation.response == MyInvitation.PENDING ->
                stringResource(R.string.feature_calendar_response_started)
            else -> responseText(invitation.response)
        }
        Text(text = status, style = MaterialTheme.typography.bodyMedium, color = colors.text)
        if (invitation.canChange || invitation.phase == ResponsePhase.SAVING) {
            val saving = invitation.phase == ResponsePhase.SAVING
            val accepted = invitation.response == MyInvitation.ACCEPTED
            val declined = invitation.response == MyInvitation.DECLINED
            ResponseChoices(saving = saving, accepted = accepted, declined = declined, actions = actions)
        }
        invitation.failure?.let { error ->
            WaiveraBanner(
                title = stringResource(R.string.feature_calendar_response_failed_title),
                message = stringResource(error.messageRes),
                tone = BannerTone.Error,
                actionLabel = stringResource(R.string.feature_calendar_retry),
                onAction = actions.onRetryResponse,
            )
        }
    }
}

/**
 * "Not going" and "Going" as one single-choice group: the chosen answer is tonal with a check and
 * announced as selected, the other is outlined, so the pair reads as a state rather than two
 * calls to action. Above 130% font the pair stacks and the check is dropped so labels stay whole.
 */
@Composable
private fun ResponseChoices(saving: Boolean, accepted: Boolean, declined: Boolean, actions: EventActions) {
    val large = LocalDensity.current.fontScale > RESPONSE_STACK_FONT_SCALE
    val decline: @Composable (Modifier) -> Unit = { buttonModifier ->
        ResponseChoice(
            text = stringResource(R.string.feature_calendar_decline),
            chosen = declined,
            saving = saving,
            showCheck = !large,
            onClick = actions.onDecline,
            modifier = buttonModifier,
        )
    }
    val accept: @Composable (Modifier) -> Unit = { buttonModifier ->
        ResponseChoice(
            text = stringResource(R.string.feature_calendar_accept),
            chosen = accepted,
            saving = saving,
            showCheck = !large,
            onClick = actions.onAccept,
            modifier = buttonModifier,
        )
    }
    if (large) {
        Column(
            modifier = Modifier.selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.Small),
        ) {
            accept(Modifier.fillMaxWidth())
            decline(Modifier.fillMaxWidth())
        }
    } else {
        Row(
            modifier = Modifier.selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.Small),
        ) {
            decline(Modifier.weight(1f))
            accept(Modifier.weight(1f))
        }
    }
}

@Composable
private fun ResponseChoice(
    text: String,
    chosen: Boolean,
    saving: Boolean,
    showCheck: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    WaiveraButton(
        text = text,
        onClick = onClick,
        variant = if (chosen) WaiveraButtonVariant.Tonal else WaiveraButtonVariant.Secondary,
        enabled = !saving,
        loading = saving && chosen,
        leadingIcon = if (chosen && showCheck) WaiveraIcons.Check else null,
        modifier = modifier.semantics { selected = chosen },
    )
}

@Composable
private fun responseText(response: MyInvitation): String = when (response) {
    MyInvitation.ACCEPTED -> stringResource(R.string.feature_calendar_response_accepted)
    MyInvitation.DECLINED -> stringResource(R.string.feature_calendar_response_declined)
    MyInvitation.TENTATIVE -> stringResource(R.string.feature_calendar_response_tentative)
    else -> stringResource(R.string.feature_calendar_response_pending)
}

@Composable
private fun OrganizerBlock(
    organizer: PersonRow,
    isMine: Boolean,
    actions: EventActions,
    avatarLoader: AvatarLoader?,
    modifier: Modifier = Modifier,
) {
    val colors = WaiveraTheme.colors
    val name = organizer.name?.takeIf(String::isNotBlank) ?: stringResource(R.string.feature_calendar_event_guest)
    val painter = rememberAvatarPainter(organizer.userId, avatarLoader)
    val content: @Composable () -> Unit = {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = WaiveraSpacing.MinTouchTarget)
                .padding(WaiveraSpacing.MediumSmall),
            horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.MediumSmall),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MilestoneAvatar(name, organizer.celebration, CalendarSizes.OrganizerAvatar, painter = painter)
            Column(verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.XxSmall)) {
                Text(
                    text = stringResource(R.string.feature_calendar_event_organized_by).uppercase(),
                    style = WaiveraTextStyles.Eyebrow,
                    color = colors.onAccentSoft,
                )
                Text(text = name, style = MaterialTheme.typography.titleSmall, color = colors.onAccentSoft)
                if (isMine) {
                    Text(
                        text = stringResource(R.string.feature_calendar_event_you_organize),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onAccentSoft,
                    )
                }
            }
        }
    }
    val container = modifier
        .fillMaxWidth()
        .clip(WaiveraShapes.Card)
        .background(colors.accentSoft)
    val userId = organizer.userId
    if (userId != null) {
        WaiveraPressable(
            onClick = { actions.onOpenPerson(userId) },
            onClickLabel = stringResource(R.string.feature_calendar_open_profile),
            shape = WaiveraShapes.Card,
            modifier = container,
        ) { content() }
    } else {
        Column(modifier = container.semantics(mergeDescendants = true) { }) { content() }
    }
}

@Composable
private fun LabeledBlock(eyebrow: String, text: String, strongBorder: Boolean, modifier: Modifier = Modifier) {
    val colors = WaiveraTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(WaiveraShapes.Card)
            .background(colors.surfaceMuted)
            .border(
                WaiveraSpacing.Hairline,
                if (strongBorder) colors.accentBorderStrong else colors.border,
                WaiveraShapes.Card,
            )
            .padding(WaiveraSpacing.MediumSmall)
            .semantics(mergeDescendants = true) { },
        verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.ExtraSmall),
    ) {
        Text(
            text = eyebrow.uppercase(),
            style = WaiveraTextStyles.Eyebrow,
            color = colors.textSecondary,
            modifier = Modifier.semantics { heading() },
        )
        Text(text = text, style = MaterialTheme.typography.bodyMedium, color = colors.text)
    }
}

@Composable
private fun AttendeesHeader(count: Int) {
    val heading = stringResource(R.string.feature_calendar_event_attendees)
    if (count == 0) {
        // "No one else is invited." follows, so a zero count would only repeat it.
        WaiveraSectionHeader(text = heading, modifier = Modifier.semantics { heading() })
        return
    }
    val countText = pluralStringResource(R.plurals.feature_calendar_event_attendee_count, count, count)
    WaiveraSectionHeader(
        text = stringResource(R.string.feature_calendar_with_detail, heading, countText),
        modifier = Modifier.clearAndSetSemantics {
            contentDescription = "$heading, $countText"
            heading()
        },
    )
}

@Composable
private fun AttendeeItem(attendee: AttendeeRow, actions: EventActions, avatarLoader: AvatarLoader?) {
    val guest = stringResource(R.string.feature_calendar_event_guest)
    val baseName = attendee.name ?: attendee.email ?: guest
    val name = if (attendee.isMe) stringResource(R.string.feature_calendar_event_you, baseName) else baseName
    val painter = rememberAvatarPainter(attendee.userId, avatarLoader)
    val userId = attendee.userId
    WaiveraListRow(
        headline = name,
        supporting = inviteStatusText(attendee.status),
        onClick = userId?.let { { actions.onOpenPerson(it) } },
        onClickLabel = userId?.let { stringResource(R.string.feature_calendar_open_profile) },
        leading = {
            MilestoneAvatar(baseName, attendee.celebration, CalendarSizes.AttendeeAvatar, painter = painter)
        },
    )
}

/** Skeleton shaped like the event body, shown only when loading takes longer than ~300 ms. */
@Composable
internal fun EventBodySkeleton(modifier: Modifier = Modifier) {
    if (!rememberSkeletonVisible(loading = true)) return
    val pulse = rememberSkeletonPulse()
    val loading = stringResource(R.string.feature_calendar_loading)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(WaiveraSpacing.Gutter)
            .clearAndSetSemantics { contentDescription = loading },
        verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.MediumSmall),
    ) {
        SkeletonLine(widthFraction = TITLE_FRACTION, pulse = pulse)
        SkeletonLine(widthFraction = LINE_FRACTION, pulse = pulse)
        SkeletonLine(widthFraction = SHORT_FRACTION, pulse = pulse)
        repeat(SKELETON_ROWS) { SkeletonListRow(pulse = pulse) }
    }
}

private const val TITLE_FRACTION = 0.7f
private const val LINE_FRACTION = 0.5f
private const val SHORT_FRACTION = 0.35f
private const val SKELETON_ROWS = 3
private const val RESPONSE_STACK_FONT_SCALE = 1.3f
