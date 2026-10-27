package com.waivera.waivera.feature.chat.newchat

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.waivera.waivera.core.data.model.Person
import com.waivera.waivera.core.data.model.Synced
import com.waivera.waivera.core.data.model.currentUserId
import com.waivera.waivera.core.data.repository.ConversationRepository
import com.waivera.waivera.core.data.repository.PeopleRepository
import com.waivera.waivera.core.data.repository.SessionRepository
import com.waivera.waivera.core.network.error.WaiveraError
import com.waivera.waivera.feature.chat.R
import com.waivera.waivera.feature.chat.list.ConversationPresentation
import java.time.Clock
import java.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class PersonOptionUi(val userId: String, val name: String, val selected: Boolean)

enum class NewChatAction { OpenChat, CreateGroup }

@Immutable
data class NewChatUiState(
    val query: String = "",
    val people: List<PersonOptionUi> = emptyList(),
    val selectedCount: Int = 0,
    /** Directory not loaded yet; the skeleton shows after ~300 ms. */
    val isLoading: Boolean = true,
    /** The directory could not be loaded and nothing is cached. */
    @StringRes val loadErrorRes: Int? = null,
    val isCreating: Boolean = false,
    /** Opening or creating the conversation failed. */
    @StringRes val errorRes: Int? = null,
) {
    val action: NewChatAction get() = if (selectedCount <= 1) NewChatAction.OpenChat else NewChatAction.CreateGroup

    val canSubmit: Boolean get() = selectedCount > 0 && !isCreating
}

sealed interface NewChatEffect {
    data class OpenConversation(val conversationId: Long) : NewChatEffect
}

/**
 * New chat and people picker (spec 02 §7). The repository does the exact-participant-set reuse
 * (no request when the DM or group exists), the create, and the re-fetch-and-match recovery
 * when create fails with 409, all against one inbox endpoint.
 */
class NewChatStateHolder(
    private val scope: CoroutineScope,
    private val people: PeopleRepository,
    private val conversations: ConversationRepository,
    private val session: SessionRepository,
    private val clock: Clock,
) {
    private data class Local(
        val query: String = "",
        val selected: Set<String> = emptySet(),
        val isCreating: Boolean = false,
        @StringRes val errorRes: Int? = null,
    )

    private val local = MutableStateFlow(Local())
    private val directory: StateFlow<Synced<List<Person>>?> =
        people.observeDirectory().stateIn(scope, SharingStarted.Eagerly, null)
    private val effectChannel = Channel<NewChatEffect>(Channel.BUFFERED)

    val effects: Flow<NewChatEffect> = effectChannel.receiveAsFlow()

    val state: StateFlow<NewChatUiState> = combine(directory, local, session.state) { d, l, s ->
        val me = s.currentUserId
        val q = l.query.trim().lowercase()
        val options = d?.data.orEmpty()
            .filter { it.id.isNotBlank() && it.id != me }
            .map { person ->
                PersonOptionUi(
                    userId = person.id,
                    name = ConversationPresentation.displayName(person).ifEmpty { person.name },
                    selected = person.id in l.selected,
                )
            }
            .filter { q.isEmpty() || it.name.lowercase().contains(q) }
        val empty = d?.data.isNullOrEmpty()
        NewChatUiState(
            query = l.query,
            people = options,
            selectedCount = l.selected.size,
            isLoading = d == null || (empty && d.isInitialLoad),
            loadErrorRes = d?.error?.messageRes?.takeIf { empty },
            isCreating = l.isCreating,
            errorRes = l.errorRes,
        )
    }.stateIn(scope, SharingStarted.Eagerly, NewChatUiState())

    /** Called when the sheet opens: refetch a directory older than a minute (spec staleTime). */
    fun onOpened() {
        val d = directory.value
        val at = d?.lastSyncedAt
        if (at == null || Duration.between(at, clock.instant()) >= STALE_AFTER) scope.launch { people.refresh() }
    }

    fun onRetryLoad() {
        scope.launch { people.refresh() }
    }

    fun onQueryChange(query: String) = local.update { it.copy(query = query) }

    fun onToggle(userId: String) = local.update {
        val selected = if (userId in it.selected) it.selected - userId else it.selected + userId
        it.copy(selected = selected, errorRes = null)
    }

    fun onSubmit() {
        val current = local.value
        if (current.selected.isEmpty() || current.isCreating) return
        local.update { it.copy(isCreating = true, errorRes = null) }
        scope.launch {
            conversations.openConversation(current.selected.toList())
                .onSuccess { id ->
                    local.value = Local()
                    effectChannel.send(NewChatEffect.OpenConversation(id))
                }
                .onFailure { error ->
                    val res = createErrorRes(WaiveraError.from(error))
                    local.update { it.copy(isCreating = false, errorRes = res) }
                }
        }
    }

    /** Closing the sheet clears the search and the selection. */
    fun onDismissed() {
        if (!local.value.isCreating) local.value = Local()
    }

    companion object {
        val STALE_AFTER: Duration = Duration.ofSeconds(60)
    }
}

/**
 * Copy for a failed create. A 409 here is the server racing on the same chat, not someone
 * editing a record, so the generic "someone else changed this" copy would be wrong.
 */
internal fun createErrorRes(error: WaiveraError): Int = when (error) {
    is WaiveraError.Conflict -> R.string.feature_chat_newchat_create_conflict
    else -> error.messageRes
}
