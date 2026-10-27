package com.waivera.waivera.core.data.di

import com.waivera.waivera.core.data.model.ConversationTitleText
import com.waivera.waivera.core.data.text.ResourceConversationTitleText
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** The shared conversation-title copy, the same in live, demo and test builds. */
@Module
@InstallIn(SingletonComponent::class)
abstract class ConversationTitleModule {
    @Binds
    abstract fun conversationTitleText(impl: ResourceConversationTitleText): ConversationTitleText
}
