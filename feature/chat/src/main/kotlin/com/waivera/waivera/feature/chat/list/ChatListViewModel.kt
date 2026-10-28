package com.waivera.waivera.feature.chat.list

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.waivera.waivera.core.data.repository.ConversationRepository
import com.waivera.waivera.core.data.repository.PeopleRepository
import com.waivera.waivera.core.data.repository.RealtimeStatusRepository
import com.waivera.waivera.core.data.repository.SessionRepository
import com.waivera.waivera.feature.chat.message.ChatComputeDispatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Owns one [ChatListStateHolder] for the inbox entry, plus its avatar cache. */
@HiltViewModel
@Suppress("LongParameterList")
class ChatListViewModel @Inject constructor(
    conversations: ConversationRepository,
    session: SessionRepository,
    realtime: RealtimeStatusRepository,
    people: PeopleRepository,
    text: ChatListText,
    clock: Clock,
    // Injected so tests decode on their own dispatcher instead of racing a real thread.
    @ChatComputeDispatcher decodeDispatcher: CoroutineDispatcher,
) : ViewModel() {
    private val holder = ChatListStateHolder(viewModelScope, conversations, session, realtime, text, clock)
    private val avatarCache = AvatarCache(
        people = people,
        decode = ::decodeAvatar,
        scope = viewModelScope,
        decodeDispatcher = decodeDispatcher,
    )

    val state: StateFlow<ChatListUiState> = holder.state
    val effects: Flow<ChatListEffect> = holder.effects
    val avatars: StateFlow<Map<String, ImageBitmap>> = avatarCache.images

    init {
        viewModelScope.launch { holder.avatarIds.collect(avatarCache::prefetch) }
    }

    fun onQueryChange(query: String) = holder.onQueryChange(query)

    fun onFilterChange(filter: ChatListFilter) = holder.onFilterChange(filter)

    fun onConversationClick(conversationId: Long) = holder.onConversationClick(conversationId)

    fun onResumed() = holder.onResumed()

    fun onRefresh() = holder.onRefresh()

    fun onRetryConnection() = holder.onRetryConnection()

    fun onOpenProfile(userId: String) = holder.onOpenProfile(userId)

    fun onMarkRead(conversationId: Long) = holder.onMarkRead(conversationId)

    fun onJoinCall(conversationId: Long) = holder.onJoinCall(conversationId)

    fun onRetry(retry: ChatListRetry) = holder.onRetry(retry)
}

/** Avatar bytes from [PeopleRepository.avatar]; null when they are not a decodable image. */
internal fun decodeAvatar(bytes: ByteArray): ImageBitmap? =
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
