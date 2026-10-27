package com.waivera.waivera.core.data.di

import com.waivera.waivera.core.data.image.AuthenticatedImageLoader
import com.waivera.waivera.core.data.live.ChatCache
import com.waivera.waivera.core.data.live.LiveAttachmentRepository
import com.waivera.waivera.core.data.live.LivePeopleRepository
import com.waivera.waivera.core.data.live.SignOutCleaner
import com.waivera.waivera.core.data.outbox.OutboxDirectory
import com.waivera.waivera.core.data.outbox.OutboxStore
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import dagger.multibindings.Multibinds
import java.io.File

/**
 * Per-user caches that every session end empties. Other modules add their own with `@IntoSet`
 * (the chat share folder, the notification shade), so nothing a signed-out person left behind
 * survives into the next session.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class SignOutCleanersModule {
    @Multibinds
    abstract fun signOutCleaners(): Set<SignOutCleaner>

    @Binds
    @IntoSet
    abstract fun attachmentCleaner(repository: LiveAttachmentRepository): SignOutCleaner

    @Binds
    @IntoSet
    abstract fun avatarCleaner(repository: LivePeopleRepository): SignOutCleaner

    @Binds
    @IntoSet
    abstract fun imageCleaner(loader: AuthenticatedImageLoader): SignOutCleaner

    companion object {
        @Provides
        @IntoSet
        fun cacheCleaner(cache: ChatCache): SignOutCleaner = SignOutCleaner { cache.clear() }

        @Provides
        @IntoSet
        fun outboxCleaner(store: OutboxStore, @OutboxDirectory directory: File): SignOutCleaner =
            SignOutCleaner { store.clear(directory) }
    }
}
