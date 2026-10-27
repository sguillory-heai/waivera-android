package com.waivera.waivera.core.data.outbox

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Durable outbox retry. Gets its dependencies through a Hilt entry point so the app does not
 * need a custom WorkManager configuration or the hilt-work artifact.
 */
class OutboxWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies {
        fun outboxProcessor(): OutboxProcessor
    }

    override suspend fun doWork(): Result {
        val processor = EntryPointAccessors.fromApplication(
            applicationContext,
            Dependencies::class.java,
        ).outboxProcessor()
        return if (processor.process()) Result.retry() else Result.success()
    }
}

@Singleton
class WorkManagerOutboxScheduler @Inject constructor(@ApplicationContext private val context: Context) :
    OutboxScheduler {
    override fun schedule() {
        val request = OneTimeWorkRequestBuilder<OutboxWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .build()
        // Appending keeps a run that starts after the current one finishes, so a message queued
        // while the worker is mid-run is never missed.
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    private companion object {
        const val WORK_NAME = "waivera-outbox"
        const val BACKOFF_SECONDS = 10L
    }
}
