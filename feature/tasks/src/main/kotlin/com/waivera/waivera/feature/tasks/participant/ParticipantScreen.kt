package com.waivera.waivera.feature.tasks.participant

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.waivera.waivera.core.data.model.ParticipantProfile
import com.waivera.waivera.core.designsystem.component.BadgeTone
import com.waivera.waivera.core.designsystem.component.TopBarAction
import com.waivera.waivera.core.designsystem.component.WaiveraCardGroup
import com.waivera.waivera.core.designsystem.component.WaiveraCountTone
import com.waivera.waivera.core.designsystem.component.WaiveraEmptyState
import com.waivera.waivera.core.designsystem.component.WaiveraErrorState
import com.waivera.waivera.core.designsystem.component.WaiveraLoadSwap
import com.waivera.waivera.core.designsystem.component.WaiveraSectionHeader
import com.waivera.waivera.core.designsystem.component.WaiveraSnackbarHost
import com.waivera.waivera.core.designsystem.component.WaiveraStatusBadge
import com.waivera.waivera.core.designsystem.component.WaiveraTileTone
import com.waivera.waivera.core.designsystem.component.WaiveraTopBar
import com.waivera.waivera.core.designsystem.component.rememberSkeletonVisible
import com.waivera.waivera.core.designsystem.icon.WaiveraIcons
import com.waivera.waivera.core.designsystem.preview.WaiveraPreviews
import com.waivera.waivera.core.designsystem.theme.WaiveraShapes
import com.waivera.waivera.core.designsystem.theme.WaiveraSizes
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.feature.tasks.DetailSkeleton
import com.waivera.waivera.feature.tasks.InfoDivider
import com.waivera.waivera.feature.tasks.InfoNote
import com.waivera.waivera.feature.tasks.InfoRow
import com.waivera.waivera.feature.tasks.InfoValue
import com.waivera.waivera.feature.tasks.Loadable
import com.waivera.waivera.feature.tasks.R
import com.waivera.waivera.feature.tasks.TaskCard
import com.waivera.waivera.feature.tasks.TaskFormat
import com.waivera.waivera.feature.tasks.TaskRow
import com.waivera.waivera.feature.tasks.TasksSkeleton
import java.time.LocalDate
import kotlinx.coroutines.launch

/** Entry point for a participant's read-only profile. Owned by `:feature:tasks`; routed by `:app`. */
@Composable
fun ParticipantScreen(
    participantId: Int,
    onBack: () -> Unit,
    onOpenTask: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = hiltViewModel<ParticipantViewModel, ParticipantViewModel.Factory>(
        key = "participant-$participantId",
        creationCallback = { factory -> factory.create(participantId) },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is ParticipantEffect.OpenWeb -> if (runCatching { uriHandler.openUri(effect.url) }.isFailure) {
                    snackbar.showSnackbar(resources.getString(R.string.feature_tasks_participant_web_failed))
                }
                ParticipantEffect.WebUnavailable ->
                    snackbar.showSnackbar(resources.getString(R.string.feature_tasks_participant_web_unavailable))
            }
        }
    }
    ParticipantContent(
        state = state,
        snackbar = snackbar,
        actions = ParticipantActions(
            onBack = onBack,
            onOpenWeb = viewModel::openOnWeb,
            onRetry = viewModel::load,
            onRetryTasks = viewModel::loadTasks,
            onOpenTask = onOpenTask,
            onContact = { uri ->
                // A phone without a dialler or mail app says so instead of doing nothing.
                if (runCatching { uriHandler.openUri(uri) }.isFailure) {
                    scope.launch {
                        snackbar.showSnackbar(resources.getString(R.string.feature_tasks_participant_contact_failed))
                    }
                }
            },
        ),
        modifier = modifier,
    )
}

internal data class ParticipantActions(
    val onBack: () -> Unit = {},
    val onOpenWeb: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onRetryTasks: () -> Unit = {},
    val onOpenTask: (Int) -> Unit = {},
    val onContact: (String) -> Unit = {},
)

@Composable
internal fun ParticipantContent(
    state: ParticipantUiState,
    snackbar: SnackbarHostState,
    actions: ParticipantActions,
    modifier: Modifier = Modifier,
) {
    val profile = state.profile
    Column(modifier = modifier.fillMaxSize().background(WaiveraTheme.colors.background)) {
        WaiveraTopBar(
            title = stringResource(R.string.feature_tasks_participant_title),
            onBack = actions.onBack,
            actions = if (state.phase == ParticipantPhase.Forbidden) {
                emptyList()
            } else {
                listOf(
                    TopBarAction(
                        icon = WaiveraIcons.OpenExternal,
                        contentDescription = stringResource(R.string.feature_tasks_participant_open_web),
                        onClick = actions.onOpenWeb,
                    ),
                )
            },
        )
        Box(Modifier.weight(1f)) {
            WaiveraLoadSwap(
                loading = state.phase == ParticipantPhase.Loading,
                modifier = Modifier.fillMaxSize(),
            ) { loading ->
                when {
                    loading -> if (rememberSkeletonVisible(loading = true)) DetailSkeleton()
                    state.phase == ParticipantPhase.Forbidden -> WaiveraEmptyState(
                        title = stringResource(R.string.feature_tasks_participant_forbidden_title),
                        message = stringResource(R.string.feature_tasks_participant_forbidden),
                        icon = WaiveraIcons.Lock,
                        modifier = Modifier.padding(top = WaiveraSpacing.Large),
                    )
                    profile == null -> WaiveraErrorState(
                        title = stringResource(R.string.feature_tasks_participant_error_title),
                        message = stringResource(R.string.feature_tasks_participant_error),
                        onRetry = actions.onRetry,
                        modifier = Modifier.padding(top = WaiveraSpacing.Large),
                    )
                    else -> ParticipantBody(state, profile, actions)
                }
            }
            WaiveraSnackbarHost(
                hostState = snackbar,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = WaiveraSpacing.Medium),
            )
        }
    }
}

@Composable
private fun ParticipantBody(state: ParticipantUiState, profile: ParticipantProfile, actions: ParticipantActions) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = WaiveraSpacing.Large),
    ) {
        Identity(profile)
        if (profile.mobile != null || profile.email != null) {
            WaiveraSectionHeader(
                title = stringResource(R.string.feature_tasks_participant_contact),
                modifier = Modifier.padding(
                    horizontal = WaiveraSpacing.Gutter,
                ).padding(top = WaiveraSpacing.MediumLarge),
            )
            Spacer(Modifier.size(WaiveraSpacing.Small))
            ContactCard(profile, actions.onContact)
        }
        WaiveraSectionHeader(
            title = stringResource(R.string.feature_tasks_participant_plan_section),
            modifier = Modifier.padding(horizontal = WaiveraSpacing.Gutter).padding(top = WaiveraSpacing.MediumLarge),
        )
        Spacer(Modifier.size(WaiveraSpacing.Small))
        PlanCard(profile, state.plan)
        WaiveraSectionHeader(
            title = stringResource(R.string.feature_tasks_participant_details),
            modifier = Modifier.padding(horizontal = WaiveraSpacing.Gutter).padding(top = WaiveraSpacing.MediumLarge),
        )
        Spacer(Modifier.size(WaiveraSpacing.Small))
        DetailsCard(profile)
        OpenTasks(state, actions)
        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }
}

/**
 * Who this is, at a glance: teal initials (the participant colour, never a coworker's avatar, so
 * people we serve and staff stay distinct), the name and at most the badges that matter today.
 */
@Composable
private fun Identity(profile: ParticipantProfile) {
    val teal = WaiveraTheme.colors.tiles.teal
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = WaiveraSpacing.Gutter, vertical = WaiveraSpacing.Small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.Medium),
    ) {
        Box(
            modifier = Modifier.size(
                WaiveraSizes.AvatarLarge,
            ).clip(WaiveraShapes.avatar(WaiveraSizes.AvatarLarge)).background(teal.container),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = profile.initials, style = MaterialTheme.typography.titleLarge, color = teal.content)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.ExtraSmall)) {
            Text(
                text = profile.name,
                style = MaterialTheme.typography.titleLarge,
                color = WaiveraTheme.colors.text,
                modifier = Modifier.semantics { heading() },
            )
            val badges = profile.deactivated || profile.changePending
            if (badges) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.Small),
                    verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.ExtraSmall),
                ) {
                    if (profile.deactivated) {
                        WaiveraStatusBadge(
                            stringResource(R.string.feature_tasks_participant_deactivated),
                            tone = BadgeTone.Neutral,
                        )
                    }
                    if (profile.changePending) {
                        WaiveraStatusBadge(
                            stringResource(R.string.feature_tasks_participant_change_pending),
                            tone = BadgeTone.Warning,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ContactCard(profile: ParticipantProfile, onContact: (String) -> Unit) {
    WaiveraCardGroup {
        Column(Modifier.fillMaxWidth().padding(vertical = WaiveraSpacing.Small)) {
            profile.mobile?.let { mobile ->
                InfoRow(
                    label = stringResource(R.string.feature_tasks_participant_mobile),
                    icon = WaiveraIcons.Phone,
                    onClick = { onContact("tel:" + mobile.filter { it.isDigit() || it == '+' }) },
                    onClickLabel = stringResource(R.string.feature_tasks_participant_call, profile.name),
                ) { InfoValue(mobile) }
            }
            if (profile.mobile != null && profile.email != null) InfoDivider()
            profile.email?.let { email ->
                InfoRow(
                    label = stringResource(R.string.feature_tasks_participant_email),
                    icon = WaiveraIcons.Mail,
                    onClick = { onContact("mailto:$email") },
                    onClickLabel = stringResource(R.string.feature_tasks_participant_write, profile.name),
                ) { InfoValue(email) }
            }
        }
    }
}

@Composable
private fun PlanCard(profile: ParticipantProfile, plan: PlanStatus?) {
    val notSet = stringResource(R.string.feature_tasks_participant_not_set)
    WaiveraCardGroup {
        Column(Modifier.fillMaxWidth().padding(vertical = WaiveraSpacing.Small)) {
            InfoRow(stringResource(R.string.feature_tasks_participant_plan), WaiveraIcons.Calendar) {
                val start = profile.planStart?.let(TaskFormat::fullDate)
                val end = profile.planEnd?.let(TaskFormat::fullDate)
                InfoValue(
                    if (start == null && end == null) {
                        notSet
                    } else {
                        stringResource(R.string.feature_tasks_participant_plan_range, start ?: notSet, end ?: notSet)
                    },
                )
                plan?.let { PlanNote(it) }
            }
            InfoDivider()
            DateRow(
                stringResource(R.string.feature_tasks_participant_certification),
                WaiveraIcons.Badge,
                profile.certification,
            )
            InfoDivider()
            DateRow(stringResource(R.string.feature_tasks_participant_linkage), WaiveraIcons.Today, profile.linkage)
        }
    }
}

@Composable
private fun PlanNote(plan: PlanStatus) {
    // Day one has its own words: a plural "one" also covers 21, 31... in some languages.
    val text = when (plan) {
        is PlanStatus.EndsIn -> if (plan.days == 1) {
            stringResource(R.string.feature_tasks_participant_plan_ends_tomorrow)
        } else {
            pluralStringResource(R.plurals.feature_tasks_participant_plan_ends_in, plan.days, plan.days)
        }
        PlanStatus.EndsToday -> stringResource(R.string.feature_tasks_participant_plan_ends_today)
        is PlanStatus.EndedAgo -> if (plan.days == 1) {
            stringResource(R.string.feature_tasks_participant_plan_ended_yesterday)
        } else {
            pluralStringResource(R.plurals.feature_tasks_participant_plan_ended, plan.days, plan.days)
        }
    }
    // An ended plan is the one thing on this card that needs someone's attention.
    if (plan is PlanStatus.EndedAgo) {
        Text(text = text, style = MaterialTheme.typography.bodySmall, color = WaiveraTheme.colors.dangerText)
    } else {
        InfoNote(text)
    }
}

@Composable
private fun DateRow(label: String, icon: Int, date: LocalDate?) {
    InfoRow(label, icon) {
        InfoValue(
            date?.let(TaskFormat::fullDate) ?: stringResource(R.string.feature_tasks_participant_not_set),
        )
    }
}

@Composable
private fun DetailsCard(profile: ParticipantProfile) {
    WaiveraCardGroup {
        Column(Modifier.fillMaxWidth().padding(vertical = WaiveraSpacing.Small)) {
            DateRow(stringResource(R.string.feature_tasks_participant_birth), WaiveraIcons.Cake, profile.birthDate)
            profile.waiver?.let {
                InfoDivider()
                InfoRow(stringResource(R.string.feature_tasks_participant_waiver), WaiveraIcons.Badge) { InfoValue(it) }
            }
            profile.region?.let {
                InfoDivider()
                InfoRow(stringResource(R.string.feature_tasks_participant_region), WaiveraIcons.Location) {
                    InfoValue(it)
                }
            }
            profile.intake?.let {
                InfoDivider()
                InfoRow(stringResource(R.string.feature_tasks_participant_intake), WaiveraIcons.Hash) { InfoValue(it) }
            }
            InfoDivider()
            InfoRow(
                stringResource(R.string.feature_tasks_participant_compliance),
                if (profile.compliant) WaiveraIcons.Check else WaiveraIcons.Warning,
                tone = if (profile.compliant) WaiveraTileTone.Brand else WaiveraTileTone.Danger,
            ) {
                InfoValue(
                    stringResource(
                        if (profile.compliant) {
                            R.string.feature_tasks_participant_compliant
                        } else {
                            R.string.feature_tasks_participant_not_compliant
                        },
                    ),
                )
                if (!profile.compliant) InfoNote(stringResource(R.string.feature_tasks_participant_not_compliant_note))
            }
        }
    }
}

@Composable
private fun OpenTasks(state: ParticipantUiState, actions: ParticipantActions) {
    val rows = (state.tasks as? Loadable.Ready)?.value
    WaiveraSectionHeader(
        title = stringResource(R.string.feature_tasks_participant_tasks),
        count = rows?.size?.takeIf { it > 0 },
        countDescription = rows?.size?.let {
            pluralStringResource(R.plurals.feature_tasks_participant_tasks_count, it, it)
        },
        countTone = if (state.overdue > 0) WaiveraCountTone.Attention else WaiveraCountTone.Info,
        modifier = Modifier.padding(horizontal = WaiveraSpacing.Gutter).padding(top = WaiveraSpacing.MediumLarge),
    )
    Spacer(Modifier.size(WaiveraSpacing.Small))
    when (val tasks = state.tasks) {
        Loadable.Loading -> if (rememberSkeletonVisible(loading = true)) TasksSkeleton(cards = SKELETON_TASKS)
        Loadable.Failed -> WaiveraErrorState(
            message = stringResource(R.string.feature_tasks_participant_tasks_error),
            onRetry = actions.onRetryTasks,
            dense = true,
            modifier = Modifier.padding(horizontal = WaiveraSpacing.Gutter),
        )
        is Loadable.Ready -> if (tasks.value.isEmpty()) {
            WaiveraEmptyState(
                title = stringResource(R.string.feature_tasks_participant_no_tasks_title),
                message = stringResource(R.string.feature_tasks_participant_no_tasks),
            )
        } else {
            Column(
                modifier = Modifier.padding(horizontal = WaiveraSpacing.Gutter),
                verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.MediumSmall),
            ) {
                tasks.value.forEach { row -> TaskCard(row = row, onClick = { actions.onOpenTask(row.id) }) }
            }
        }
    }
}

private const val SKELETON_TASKS = 2

internal object ParticipantPreviewData {
    val today: LocalDate = LocalDate.of(2026, 11, 4)

    val profile = ParticipantProfile(
        id = 501,
        name = "Jordan Ellis",
        initials = "JE",
        waiver = "Community Choices",
        region = "Region 4",
        mobile = "(555) 010-2231",
        email = "jordan.ellis@example.com",
        planStart = LocalDate.of(2026, 4, 1),
        planEnd = LocalDate.of(2027, 3, 31),
        certification = LocalDate.of(2026, 3, 15),
        linkage = LocalDate.of(2025, 2, 10),
        birthDate = LocalDate.of(1988, 6, 2),
        intake = "Transition",
        deactivated = false,
        compliant = true,
    )

    fun state(
        profile: ParticipantProfile = this.profile,
        tasks: Loadable<List<TaskRow>> = Loadable.Ready(emptyList()),
    ) = ParticipantUiState(
        phase = ParticipantPhase.Content,
        profile = profile,
        plan = profile.planEnd?.let { ParticipantViewModel.planStatus(it, today) },
        tasks = tasks,
        overdue = (tasks as? Loadable.Ready)?.value.orEmpty().count { it.daysLate != null },
        today = today,
    )
}

@WaiveraPreviews
@Composable
private fun ParticipantPreview() {
    WaiveraTheme {
        ParticipantContent(
            state = ParticipantPreviewData.state(),
            snackbar = remember { SnackbarHostState() },
            actions = ParticipantActions(),
        )
    }
}
