package com.waivera.waivera.feature.chat.newchat

import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.waivera.waivera.core.data.repository.ConversationRepository
import com.waivera.waivera.core.data.repository.PeopleRepository
import com.waivera.waivera.core.data.repository.SessionRepository
import com.waivera.waivera.feature.chat.list.AvatarCache
import com.waivera.waivera.feature.chat.list.decodeAvatar
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

@HiltViewModel
class NewChatViewModel @Inject constructor(
    people: PeopleRepository,
    conversations: ConversationRepository,
    session: SessionRepository,
    clock: Clock,
) : ViewModel() {
    private val holder = NewChatStateHolder(viewModelScope, people, conversations, session, clock)
    private val avatarCache = AvatarCache(people, ::decodeAvatar, viewModelScope)

    val state: StateFlow<NewChatUiState> = holder.state
    val effects: Flow<NewChatEffect> = holder.effects
    val avatars: StateFlow<Map<String, ImageBitmap>> = avatarCache.images

    fun onOpened() = holder.onOpened()

    fun onRetryLoad() = holder.onRetryLoad()

    fun onQueryChange(query: String) = holder.onQueryChange(query)

    fun onToggle(userId: String) = holder.onToggle(userId)

    fun onSubmit() = holder.onSubmit()

    fun onDismissed() = holder.onDismissed()

    /** Rows coming into view ask for their avatars; the cache dedupes and limits concurrency. */
    fun onVisiblePeople(userIds: List<String>) = avatarCache.prefetch(userIds)
}
