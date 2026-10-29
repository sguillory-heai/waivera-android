package com.waivera.waivera.feature.chat.members

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.waivera.waivera.core.data.model.Conversation
import com.waivera.waivera.core.data.model.RealtimeStatus
import com.waivera.waivera.core.data.model.currentUserId
import com.waivera.waivera.core.data.repository.ConversationRepository
import com.waivera.waivera.core.data.repository.RealtimeStatusRepository
import com.waivera.waivera.core.data.repository.SessionRepository
import com.waivera.waivera.core.network.error.WaiveraError
import com.waivera.waivera.feature.chat.list.ConversationPresentation
import com.waivera.waivera.feature.chat.message.ConnectionUi
import com.waivera.waivera.feature.chat.newchat.createErrorRes
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Locale
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * One person in a group's member list. [name] is empty when the server sent no name; the row
 * shows a fallback. There is no role or job title: conversation participants carry only a name,
 * an email and an avatar (sprout_api's participant DTO), and a group has no owner.
 */
@Immutable
data class MemberRowUi(val userId: String, val name: String, val isMe: Boolean)

/** Which block the members list shows. */
enum class MembersPhase { Loading, Error, Empty, Content }

@Immutable
data class GroupMembersUiState(
    val phase: MembersPhase = MembersPhase.Loading,
    /** The rows after the search, me first and then by name. */
    val rows: List<MemberRowUi> = emptyList(),
    /** Everyone in the group, before the search: the count in the title. */
    val totalCount: Int = 0,
    val query: String = "",
    /** Why the members could not be loaded (nothing cached and the fetch failed). */
    @StringRes val errorRes: Int? = null,
    val connection: ConnectionUi = ConnectionUi.Connected,
    /** The person whose direct chat is being opened, so their Message button shows it is busy. */
    val openingChatWith: String? = null,
    /** Opening a direct chat failed. */
    @StringRes val messageErrorRes: Int? = null,
) {
    /** Search shows only on a long list: more than [MemberRules.SEARCH_FROM] people. */
    val showsSearch: Boolean get() = totalCount > MemberRules.SEARCH_FROM

    /** The list is filtered down to nothing; the empty state offers to clear the search. */
    val isNoMatch: Boolean get() = phase == MembersPhase.Empty && totalCount > 0
}

sealed interface GroupMembersEffect {
    data class OpenConversation(val conversationId: Long) : GroupMembersEffect
}

/** The member list's rules, kept pure so they are unit tested. */
object MemberRules {
    /** Search shows when a group has more members than this. */
    const val SEARCH_FROM = 10

    /**
     * Everyone in [conversation] with an id, once each: the signed-in person ([myUserId], by
     * backend id) first, then everyone else by name, ignoring case, then by id so equal names
     * keep a stable order.
     */
    fun rows(conversation: Conversation, myUserId: String?): List<MemberRowUi> = conversation.participants
        .filter { it.id.isNotBlank() }
        .distinctBy { it.id }
        .map { person ->
            MemberRowUi(
                userId = person.id,
                name = ConversationPresentation.displayName(person).ifEmpty { person.name },
                isMe = person.id == myUserId,
            )
        }
        .sortedWith(compareBy<MemberRowUi>({ !it.isMe }, { it.name.lowercase(Locale.ROOT) }, { it.userId }))

    /** [rows] whose name holds [query], ignoring case and surrounding spaces. */
    fun filter(rows: List<MemberRowUi>, query: String): List<MemberRowUi> {
        val needle = query.trim().lowercase(Locale.ROOT)
        return if (needle.isEmpty()) rows else rows.filter { it.name.lowercase(Locale.ROOT).contains(needle) }
    }
}

/**
 * A group's members (v7, item 4), opened from the "N members" line in the conversation header.
 * The list comes from the conversation's own participants, so a cached group shows at once and
 * stays current as the conversation updates. Message opens the direct chat through the same
 * repository call New chat makes for one person, which reuses the chat when it exists.
 */
@HiltViewModel(assistedFactory = GroupMembersViewModel.Factory::class)
class GroupMembersViewModel @AssistedInject constructor(
    @Assisted private val conversationId: Long,
    private val conversations: ConversationRepository,
    session: SessionRepository,
    private val realtime: RealtimeStatusRepository,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(conversationId: Long): GroupMembersViewModel
    }

    private data class Local(
        val query: String = "",
        val loading: Boolean = false,
        val loadError: WaiveraError? = null,
        val openingChatWith: String? = null,
        @StringRes val messageErrorRes: Int? = null,
    )

    private val local = MutableStateFlow(Local())
    private val effectChannel = Channel<GroupMembersEffect>(Channel.BUFFERED)
    private var refreshJob: Job? = null

    val effects: Flow<GroupMembersEffect> = effectChannel.receiveAsFlow()

    val state: StateFlow<GroupMembersUiState> = combine(
        conversations.observeConversation(conversationId),
        session.state.map { it.currentUserId }.distinctUntilChanged(),
        local,
        realtime.status,
    ) { conversation, me, l, status ->
        val all = conversation?.let { MemberRules.rows(it, me) }.orEmpty()
        val shown = MemberRules.filter(all, l.query)
        GroupMembersUiState(
            phase = when {
                conversation == null && l.loadError != null && !l.loading -> MembersPhase.Error
                conversation == null -> MembersPhase.Loading
                shown.isEmpty() -> MembersPhase.Empty
                else -> MembersPhase.Content
            },
            rows = shown,
            totalCount = all.size,
            query = l.query,
            errorRes = l.loadError?.messageRes?.takeIf { conversation == null },
            connection = ConnectionUi.from(status),
            openingChatWith = l.openingChatWith,
            messageErrorRes = l.messageErrorRes,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, GroupMembersUiState())

    init {
        viewModelScope.launch {
            if (conversations.observeConversation(conversationId).first() == null) refresh()
        }
        // What changed while the hub was down (someone joined or left) arrives with a refetch.
        viewModelScope.launch { realtime.status.reconnections().collect { refresh() } }
    }

    /** Try again after a failed load; a refresh already running is not restarted. */
    fun refresh() {
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            local.update { it.copy(loading = true) }
            val error = conversations.refreshConversation(conversationId).exceptionOrNull()?.let {
                WaiveraError.from(it)
            }
            local.update { it.copy(loading = false, loadError = error) }
        }
    }

    fun onQueryChange(query: String) = local.update { it.copy(query = query) }

    /** Opens [userId]'s direct chat, or starts it; one at a time, and never with myself. */
    fun onMessage(userId: String) {
        val current = local.value
        if (current.openingChatWith != null) return
        if (state.value.rows.any { it.userId == userId && it.isMe }) return
        local.update { it.copy(openingChatWith = userId, messageErrorRes = null) }
        viewModelScope.launch {
            conversations.openConversation(listOf(userId))
                .onSuccess { id ->
                    local.update { it.copy(openingChatWith = null) }
                    effectChannel.send(GroupMembersEffect.OpenConversation(id))
                }
                .onFailure { error ->
                    val res = createErrorRes(WaiveraError.from(error))
                    local.update { it.copy(openingChatWith = null, messageErrorRes = res) }
                }
        }
    }

    fun dismissMessageError() = local.update { it.copy(messageErrorRes = null) }

    fun retryConnection() = realtime.retryNow()
}

/** Emits each time the hub comes back after being down, so the list can refetch what it missed. */
internal fun Flow<RealtimeStatus>.reconnections(): Flow<Unit> = map { it.isConnected && it.isOnline }
    .distinctUntilChanged()
    .drop(1)
    .filter { it }
    .map { }
