package com.waivera.waivera.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.waivera.waivera.core.data.image.AvatarModel
import com.waivera.waivera.core.designsystem.component.ConnectionBanner
import com.waivera.waivera.core.designsystem.component.ConnectionBannerState
import com.waivera.waivera.core.designsystem.component.WaiveraAddressText
import com.waivera.waivera.core.designsystem.component.WaiveraAvatar
import com.waivera.waivera.core.designsystem.component.WaiveraButton
import com.waivera.waivera.core.designsystem.component.WaiveraButtonVariant
import com.waivera.waivera.core.designsystem.component.WaiveraHeroCelebration
import com.waivera.waivera.core.designsystem.component.WaiveraNameText
import com.waivera.waivera.core.designsystem.component.WaiveraSnackbarHost
import com.waivera.waivera.core.designsystem.component.WaiveraTopBar
import com.waivera.waivera.core.designsystem.icon.WaiveraIcons
import com.waivera.waivera.core.designsystem.preview.WaiveraPreviews
import com.waivera.waivera.core.designsystem.text.CelebrationCopy
import com.waivera.waivera.core.designsystem.theme.WaiveraHero
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTextStyles
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.core.designsystem.theme.isLargeFontScale

/** Entry point for the person screen. Owned by `:feature:profile`; routed by `:app`. */
@Composable
fun PersonDetailScreen(
    userId: String,
    onBack: () -> Unit,
    onOpenConversation: (conversationId: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = hiltViewModel<PersonDetailViewModel, PersonDetailViewModel.Factory>(
        key = userId,
        creationCallback = { factory -> factory.create(userId) },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val openConversation by rememberUpdatedState(onOpenConversation)
    LaunchedEffect(viewModel) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is PersonDetailEffect.OpenConversation -> openConversation(effect.conversationId)
                is PersonDetailEffect.MessageFailed -> {
                    snackbar.showSnackbar(
                        resources.getString(
                            R.string.feature_profile_person_message_failed,
                            resources.getString(effect.error.messageRes),
                        ),
                    )
                }
            }
        }
    }
    PersonDetailContent(
        state = state,
        snackbar = snackbar,
        onBack = onBack,
        onMessage = viewModel::message,
        onRetry = viewModel::load,
        onRetryConnection = viewModel::retryConnection,
        modifier = modifier,
    )
}

@Composable
internal fun PersonDetailContent(
    state: PersonDetailUiState,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onMessage: () -> Unit,
    onRetry: () -> Unit,
    onRetryConnection: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = WaiveraTheme.colors
    Box(modifier = modifier.fillMaxSize().background(colors.background)) {
        // The person's hero scrolls with the page, as your own profile's does; the bar names the
        // screen and the hero names the person, so the name is not said twice.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.MediumSmall),
        ) {
            WaiveraTopBar(
                title = stringResource(R.string.feature_profile_person_detail_title),
                onBack = onBack,
                heroContent = { PersonHero(state) },
            )
            ConnectionBanner(
                state = bannerFor(state.connection, showsLoadError = state.details is Section.Failed),
                onRetry = onRetryConnection,
                offlineMessage = stringResource(R.string.feature_profile_person_offline_saved),
            )
            Column(verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.MediumSmall)) {
                if (state.canMessage) {
                    PersonActions(
                        celebrating = state.celebration != null,
                        openingChat = state.isOpeningChat,
                        onMessage = onMessage,
                        modifier = Modifier.padding(horizontal = WaiveraSpacing.Gutter),
                    )
                }
                PersonDetailsCard(state.details, onRetry)
                Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
            }
        }
        WaiveraSnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = WaiveraSpacing.Medium),
        )
    }
}

/**
 * Message, always the primary, so the screen's action is the same whether or not it is their
 * day (and the same as while it loads). On their birthday or anniversary "Send wishes" sits beside
 * it as the raised secondary, as on the v5 board; both open the same direct chat (UI8-09), and
 * the one tapped shows the busy label. At large font the two stack, so neither label wraps.
 */
@Composable
private fun PersonActions(
    celebrating: Boolean,
    openingChat: Boolean,
    onMessage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var wishesTapped by rememberSaveable { mutableStateOf(false) }
    val busy = stringResource(R.string.feature_profile_person_opening_chat)
    val message = @Composable { buttonModifier: Modifier ->
        WaiveraButton(
            text = stringResource(R.string.feature_profile_person_message),
            onClick = {
                wishesTapped = false
                onMessage()
            },
            loadingText = busy.takeIf { openingChat && !wishesTapped },
            enabled = !(openingChat && wishesTapped),
            fullWidth = true,
            leadingIcon = WaiveraIcons.Chat,
            modifier = buttonModifier,
        )
    }
    if (!celebrating) {
        message(modifier)
        return
    }
    val wishes = @Composable { buttonModifier: Modifier ->
        WaiveraButton(
            text = stringResource(R.string.feature_profile_person_send_wishes),
            onClick = {
                wishesTapped = true
                onMessage()
            },
            variant = WaiveraButtonVariant.Secondary,
            loadingText = busy.takeIf { openingChat && wishesTapped },
            enabled = !(openingChat && !wishesTapped),
            fullWidth = true,
            modifier = buttonModifier,
        )
    }
    if (isLargeFontScale()) {
        Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.Small)) {
            message(Modifier)
            wishes(Modifier)
        }
    } else {
        Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.Small)) {
            message(Modifier.weight(1f))
            wishes(Modifier.weight(1f))
        }
    }
}

/** The person on the plum hero: the large avatar, their name and address, and today's celebration. */
@Composable
private fun PersonHero(state: PersonDetailUiState) {
    val hero = WaiveraHero.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = WaiveraSpacing.Gutter, end = WaiveraSpacing.Gutter, bottom = WaiveraSpacing.Medium),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.Small),
    ) {
        WaiveraAvatar(
            name = state.name,
            imageModel = AvatarModel.of(state.userId),
            size = if (isLargeFontScale()) WaiveraHero.ProfileAvatarCompact else WaiveraHero.ProfileAvatar,
            milestone = state.celebration?.milestoneKind,
            contentDescription = null,
        )
        if (state.name.isNotBlank()) {
            // Two lines, three at large font, wrapping only at a space or after a hyphen, so a
            // long name leaves Message on the first screen at 200% (UI5-05, wave 29 N4).
            WaiveraNameText(
                text = state.name,
                maxLines = heroNameMaxLines(),
                style = WaiveraTextStyles.HeroGreetingCompact,
                color = hero.onHeader,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .testTag(PERSON_HERO_NAME_TAG)
                    .semantics { heading() },
            )
        }
        state.email?.let {
            WaiveraAddressText(
                address = it,
                style = MaterialTheme.typography.bodyMedium,
                color = hero.onHeaderSecondary,
                textAlign = TextAlign.Center,
            )
        }
        state.celebration?.let { party ->
            // The name is right above, so the note says only the first name (wave 29 N4).
            val firstName = CelebrationCopy.firstName(state.name).orEmpty()
            WaiveraHeroCelebration(
                emoji = party.emoji(),
                text = party.fullLabel(firstName),
                name = firstName,
                modifier = Modifier.padding(top = WaiveraSpacing.ExtraSmall),
            )
        }
    }
}

@Composable
private fun PersonDetailsCard(section: Section<PersonDetails>, onRetry: () -> Unit) {
    when (section) {
        Section.Loading -> if (rememberDelayedSkeleton(loading = true)) {
            ProfileCard { SectionSkeleton(rows = 3) }
        }
        is Section.Failed -> ProfileCard(title = stringResource(R.string.feature_profile_person_error_title)) {
            SectionError(section.error, onRetry)
        }
        is Section.Ready -> {
            val details = section.value
            val roles = details.roles.map { ProfileFormat.roleLabel(it) }.joinedOrNull()
            // Absent values are left out here: this card describes someone else, not a form to fill.
            if (details.organization != null || roles != null || details.phoneNumber != null) {
                val organizationLabel = stringResource(R.string.feature_profile_organization_title)
                val rolesLabel = stringResource(R.string.feature_profile_person_roles)
                val phoneLabel = stringResource(R.string.feature_profile_phone)
                ProfileCard {
                    InfoFields(
                        listOfNotNull(
                            details.organization?.let { InfoField(organizationLabel, it, WaiveraIcons.Building) },
                            roles?.let { InfoField(rolesLabel, it, WaiveraIcons.Badge) },
                            details.phoneNumber?.let { InfoField(phoneLabel, it, WaiveraIcons.Phone) },
                        ),
                    )
                }
            }
        }
    }
}

@WaiveraPreviews
@Composable
private fun PersonDetailScreenPreview() {
    WaiveraTheme {
        PersonDetailContent(
            state = PersonDetailUiState(
                userId = "preview",
                name = "Priya Raman",
                email = "priya.raman@example.com",
                details = Section.Ready(
                    PersonDetails(
                        displayName = "Priya Raman",
                        email = "priya.raman@example.com",
                        phoneNumber = "(512) 555-0100",
                        roles = listOf("Care lead"),
                        organization = null,
                    ),
                ),
                celebration = Celebration(birthday = true, anniversaryYears = null),
                connection = ConnectionBannerState.Connected,
            ),
            snackbar = remember { SnackbarHostState() },
            onBack = {},
            onMessage = {},
            onRetry = {},
            onRetryConnection = {},
        )
    }
}
