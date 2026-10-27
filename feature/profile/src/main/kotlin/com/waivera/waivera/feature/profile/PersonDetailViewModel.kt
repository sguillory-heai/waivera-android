package com.waivera.waivera.feature.profile

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.waivera.waivera.core.data.model.currentUserId
import com.waivera.waivera.core.data.repository.CalendarRepository
import com.waivera.waivera.core.data.repository.ConversationRepository
import com.waivera.waivera.core.data.repository.PeopleRepository
import com.waivera.waivera.core.data.repository.RealtimeStatusRepository
import com.waivera.waivera.core.data.repository.SessionRepository
import com.waivera.waivera.core.designsystem.component.ConnectionBannerState
import com.waivera.waivera.core.network.error.WaiveraError
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Immutable
internal data class PersonDetailUiState(
    val userId: String,
    /** From the directory cache at once, then from the details; blank until either arrives. */
    val name: String = "",
    val email: String? = null,
    val details: Section<PersonDetails> = Section.Loading,
    val celebration: Celebration? = null,
    /** False for the signed-in person's own entry: there is no chat with yourself. */
    val canMessage: Boolean = true,
    val isOpeningChat: Boolean = false,
    val connection: ConnectionBannerState = ConnectionBannerState.Connected,
)

internal sealed interface PersonDetailEffect {
    data class OpenConversation(val conversationId: Long) : PersonDetailEffect

    /** "Message" failed; the person stays on the profile and sees why. */
    data class MessageFailed(val error: WaiveraError) : PersonDetailEffect
}

/** Another person's profile, with a Message action that reuses or creates the direct conversation. */
@HiltViewModel(assistedFactory = PersonDetailViewModel.Factory::class)
internal class PersonDetailViewModel @AssistedInject constructor(
    @Assisted private val userId: String,
    private val sources: PersonDetailsSource,
    private val conversations: ConversationRepository,
    people: PeopleRepository,
    private val session: SessionRepository,
    calendar: CalendarRepository,
    private val realtime: RealtimeStatusRepository,
    clock: Clock,
) : ViewModel() {
    private val details = MutableStateFlow<Section<PersonDetails>>(Section.Loading)
    private val openingChat = MutableStateFlow(false)
    private val effects = Channel<PersonDetailEffect>(Channel.BUFFERED)

    /** One-off navigation and error events, collected once by the screen. */
    val effect: Flow<PersonDetailEffect> = effects.receiveAsFlow()

    private val celebration = combine(calendar.observeMilestones(), calendar.zone) { milestones, zone ->
        Celebration.forUser(userId, milestones.data, LocalDate.now(clock.withZone(zone)))
    }

    val uiState: StateFlow<PersonDetailUiState> = combine(
        people.observePerson(userId),
        details,
        celebration,
        openingChat,
        combine(session.state, realtime.status) { s, r -> s.currentUserId to r.toBannerState() },
    ) { cached, loaded, party, opening, (me, connection) ->
        val info = (loaded as? Section.Ready)?.value
        PersonDetailUiState(
            userId = userId,
            name = info?.displayName?.takeIf(String::isNotBlank) ?: cached?.name.orEmpty(),
            email = info?.email?.takeIf(String::isNotBlank) ?: cached?.email,
            details = loaded,
            celebration = party,
            canMessage = me != null && me != userId,
            isOpeningChat = opening,
            connection = connection,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), PersonDetailUiState(userId))

    init {
        load()
        viewModelScope.launch {
            realtime.status.reconnections().collect { if (details.value !is Section.Ready) load() }
        }
    }

    /** Loads (or, after an error, retries) the details. */
    fun load() {
        viewModelScope.launch {
            if (details.value !is Section.Ready) details.value = Section.Loading
            sources.details(userId)
                .onSuccess { details.value = Section.Ready(it) }
                .onFailure { error ->
                    if (details.value !is Section.Ready) details.value = Section.Failed(error.asWaivera())
                }
        }
    }

    /** Opens the direct conversation, creating it when it does not exist yet. Double taps are ignored. */
    fun message() {
        if (openingChat.value || session.state.value.currentUserId == userId) return
        openingChat.value = true
        viewModelScope.launch {
            conversations.openConversation(listOf(userId))
                .onSuccess { effects.send(PersonDetailEffect.OpenConversation(it)) }
                .onFailure { effects.send(PersonDetailEffect.MessageFailed(it.asWaivera())) }
            openingChat.value = false
        }
    }

    fun retryConnection() = realtime.retryNow()

    @AssistedFactory
    interface Factory {
        fun create(userId: String): PersonDetailViewModel
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
