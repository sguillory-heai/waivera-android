package com.waivera.waivera.core.data.di

import android.content.Context
import com.waivera.waivera.core.data.uploads.AndroidUploadStager
import com.waivera.waivera.core.data.uploads.TaskUploadDirectory
import com.waivera.waivera.core.data.uploads.TaskUploadScheduler
import com.waivera.waivera.core.data.uploads.UploadStager
import com.waivera.waivera.core.data.uploads.WorkManagerTaskUploadScheduler
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File

/** The task upload queue's storage, file checks and background job. */
@Module
@InstallIn(SingletonComponent::class)
abstract class TaskUploadModule {
    @Binds
    abstract fun stager(impl: AndroidUploadStager): UploadStager

    @Binds
    abstract fun scheduler(impl: WorkManagerTaskUploadScheduler): TaskUploadScheduler

    companion object {
        /** In filesDir, not cacheDir: a queued file must survive cache trimming until it is sent. */
        @Provides
        @TaskUploadDirectory
        fun directory(@ApplicationContext context: Context): File = File(context.filesDir, "task-uploads")
    }
}
