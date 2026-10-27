package com.waivera.waivera.di

import com.waivera.waivera.core.data.di.LiveRepositoriesModule
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Release builds bind every repository to its live implementation. Debug builds must not have
 * this module: `:core:demo`'s `DemoSwitchModule` binds the same interfaces there.
 */
@Module(includes = [LiveRepositoriesModule::class])
@InstallIn(SingletonComponent::class)
object ReleaseDataModule
