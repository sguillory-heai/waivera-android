package com.waivera.waivera.feature.chat.list

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Bindings for the list's local seams (see `docs/requests/chat-list.md`). */
@Module
@InstallIn(SingletonComponent::class)
abstract class ChatListModule {
    @Binds
    abstract fun chatListText(impl: ResourceChatListText): ChatListText

    @Binds
    abstract fun callLobby(impl: HandoffCallLobby): CallLobby
}
