package com.waivera.waivera.core.data.live

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

/** Retries a push unregister that failed during sign-out, once the device is online. */
class PushUnregisterWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies {
        fun pushRegistration(): LivePushRegistrationRepository
    }

    override suspend fun doWork(): Result {
        val push = EntryPointAccessors.fromApplication(applicationContext, Dependencies::class.java).pushRegistration()
        return if (push.retryPendingUnregister()) Result.retry() else Result.success()
    }
}

@Singleton
class WorkManagerPushUnregisterScheduler @Inject constructor(@ApplicationContext private val context: Context) :
    PushUnregisterScheduler {
    override fun schedule() {
        val request = OneTimeWorkRequestBuilder<PushUnregisterWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    private companion object {
        const val WORK_NAME = "push-unregister"
        const val BACKOFF_SECONDS = 30L
    }
}
