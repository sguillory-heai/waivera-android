package com.waivera.waivera.core.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.waivera.waivera.core.data.live.SignOutCleaner
import com.waivera.waivera.core.data.repository.HandledTodayRepository
import com.waivera.waivera.core.data.today.DataStoreHandledTodayRepository
import com.waivera.waivera.core.data.today.HandledTodayStore
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton

private val Context.handledTodayDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "waivera_handled_today",
)

/**
 * The handled-today tally, the same in live and demo builds (it only watches what the screen
 * shows). Kept apart from `DataInfrastructureModule` so a test graph that swaps that module
 * keeps this one, and can swap this one on its own.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class HandledTodayModule {
    @Binds
    abstract fun repository(impl: DataStoreHandledTodayRepository): HandledTodayRepository

    @Binds
    @IntoSet
    abstract fun cleaner(impl: DataStoreHandledTodayRepository): SignOutCleaner

    companion object {
        @Provides
        @Singleton
        @HandledTodayStore
        fun store(@ApplicationContext context: Context): DataStore<Preferences> = context.handledTodayDataStore
    }
}
