package com.waivera.waivera.navigation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.waivera.waivera.core.data.model.AppSettings
import com.waivera.waivera.core.data.model.SessionState
import com.waivera.waivera.core.data.model.currentUser
import com.waivera.waivera.core.data.repository.ConversationRepository
import com.waivera.waivera.core.data.repository.RealtimeStatusRepository
import com.waivera.waivera.core.data.repository.SessionRepository
import com.waivera.waivera.core.data.repository.SettingsRepository
import com.waivera.waivera.core.notifications.deeplink.DeepLinkTarget
import com.waivera.waivera.core.notifications.deeplink.DeepLinkUris
import com.waivera.waivera.core.notifications.permission.NotificationPermissionState
import com.waivera.waivera.core.notifications.presentation.ActiveConversationTracker
import com.waivera.waivera.core.notifications.presentation.Notifier
import com.waivera.waivera.core.notifications.token.PushRegistrar
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import javax.inject.Inject

/**
 * Everything the root composable needs, as one immutable value. [settingsLoaded] is false only
 * for the placeholder before the stored settings are read, so the activity can hold the first
 * frame until the saved theme is known (08 §7).
 */
data class ShellUiState(
    val shell: ShellState = ShellState(),
    val settings: AppSettings = AppSettings(),
    val accountName: String = "",
    val accountUserId: String? = null,
    val unreadTotal: Int = 0,
    val settingsLoaded: Boolean = false,
)

/**
 * Owns the auth gate, the tab back stacks and deep links. Navigation survives process death
 * through [SavedStateHandle]; a pending deep link is saved as its `waivera://` URI.
 */
@HiltViewModel
class AppShellViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    private val session: SessionRepository,
    settings: SettingsRepository,
    conversations: ConversationRepository,
    private val realtime: RealtimeStatusRepository,
    private val permission: NotificationPermissionState,
    private val tracker: ActiveConversationTracker,
    private val pushRegistrar: PushRegistrar,
    private val notifier: Notifier,
) : ViewModel() {

    private val shell = MutableStateFlow(restore())
    private var resumed = false
    private var lastSession: SessionState? = null
    private var reportedChat: Pair<Long, Long?>? = null

    val uiState: StateFlow<ShellUiState> = combine(
        shell,
        settings.settings,
        session.state.map { it.currentUser }.distinctUntilChanged(),
        conversations.observeUnreadTotal(),
    ) { shellState, appSettings, user, unread ->
        ShellUiState(
            shell = shellState,
            settings = appSettings,
            accountName = user?.let { it.displayName ?: it.firstName }.orEmpty(),
            accountUserId = user?.userId,
            unreadTotal = unread,
            settingsLoaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), ShellUiState(shell = shell.value))

    init {
        viewModelScope.launch {
            combine(session.state, permission.primerPending) { state, primer -> state to primer }
                .collect { (state, primer) -> onSession(state, primer) }
        }
        viewModelScope.launch {
            shell.collect { state ->
                persist(state)
                reportVisibleChat(state)
            }
        }
    }

    /** A `waivera://` URI from a notification tap or a link. Unknown or malformed URIs are ignored. */
    fun onDeepLink(uri: String?) {
        val target = DeepLinkUris.parse(uri) ?: return
        shell.update { it.onDeepLink(target) }
    }

    fun onDeepLink(target: DeepLinkTarget) = shell.update { it.onDeepLink(target) }

    fun select(tab: TopLevelTab) = shell.update { state -> state.onNav { it.select(tab) } }

    fun push(route: AppRoute) = shell.update { state -> state.onNav { it.push(route) } }

    /** Account menu choices; Settings goes over Profile so back from it lands on the profile (08 §6). */
    fun openFromAccountMenu(route: AppRoute) = shell.update { state ->
        state.onNav { if (route == SettingsRoute) it.pushOver(ProfileRoute, route) else it.push(route) }
    }

    fun back() = shell.update { state -> state.onNav(NavState::back) }

    fun onPrimerFinished(granted: Boolean) {
        permission.markPrimerCompleted()
        permission.refresh()
        if (granted) viewModelScope.launch { pushRegistrar.registerCurrentToken() }
    }

    /** From the activity's lifecycle: a chat only counts as visible while the app is resumed. */
    fun onResumedChanged(isResumed: Boolean) {
        resumed = isResumed
        if (isResumed) permission.refresh()
        reportVisibleChat(shell.value)
    }

    fun signOut() {
        viewModelScope.launch { session.signOut() }
    }

    private fun onSession(state: SessionState, primerPending: Boolean) {
        val previous = lastSession
        lastSession = state
        val wasSignedIn = previous is SessionState.SignedIn || previous is SessionState.PasswordChangeRequired
        val isSignedIn = state is SessionState.SignedIn
        if (isSignedIn && previous !is SessionState.SignedIn) {
            // Cold start while signed in and every new sign-in re-register the current token (push.md §1).
            viewModelScope.launch { pushRegistrar.registerCurrentToken() }
        }
        if (wasSignedIn && state is SessionState.SignedOut) {
            // The repository unregisters the token; nothing from the previous person may stay on screen.
            notifier.cancelAll()
        }
        shell.update { it.onGate(AppGate.from(state, primerPending)) }
    }

    private fun reportVisibleChat(state: ShellState) {
        val chat = state.nav.top.visibleChat.takeIf { resumed && state.gate == AppGate.Main }
        if (chat == reportedChat) return
        reportedChat = chat
        if (chat == null) {
            tracker.clear()
            realtime.setVisibleConversation(null)
        } else {
            tracker.enter(chat.first, chat.second)
            realtime.setVisibleConversation(chat.first)
        }
    }

    private fun persist(state: ShellState) {
        savedState[KEY_NAV] = json.encodeToString(NavState.serializer(), state.nav)
        savedState[KEY_PENDING] = state.pendingDeepLink?.let(DeepLinkUris::toUri)
    }

    private fun restore(): ShellState {
        val nav = savedState.get<String>(KEY_NAV)?.let { encoded ->
            try {
                json.decodeFromString(NavState.serializer(), encoded)
            } catch (ignored: SerializationException) {
                null
            } catch (ignored: IllegalArgumentException) {
                null
            }
        } ?: NavState()
        val pending = DeepLinkUris.parse(savedState.get<String>(KEY_PENDING))
        // The gate is not restored: it is recomputed from the session as soon as it is read.
        return ShellState(nav = nav, pendingDeepLink = pending)
    }

    private companion object {
        const val KEY_NAV = "shell.nav"
        const val KEY_PENDING = "shell.pendingDeepLink"
        const val STOP_TIMEOUT_MILLIS = 5_000L
        val json = Json { ignoreUnknownKeys = true }
    }
}
