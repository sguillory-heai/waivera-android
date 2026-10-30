package com.waivera.waivera.core.network.di

import com.waivera.waivera.core.network.log.WaiveraLog
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Process-lifetime scope for work that must outlive a screen (realtime, sync, outbox). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

/** Dispatcher for blocking I/O (Firebase `Tasks.await`, file streams). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

@Module
@InstallIn(SingletonComponent::class)
object CoroutinesModule {
    @Provides
    @IoDispatcher
    fun ioDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @Singleton
    @ApplicationScope
    fun applicationScope(): CoroutineScope = loggingScope("ApplicationScope")
}

/**
 * A process-lifetime scope whose failures are logged instead of reaching the thread's default
 * handler. Without the handler, one unexpected exception in any background job (sync, realtime,
 * push registration) kills the process on whatever screen is showing. Only the exception's class
 * is logged: messages can carry server text or decoded payloads.
 */
fun loggingScope(tag: String): CoroutineScope =
    CoroutineScope(SupervisorJob() + Dispatchers.Default + loggingExceptionHandler(tag))

fun loggingExceptionHandler(tag: String): CoroutineExceptionHandler = CoroutineExceptionHandler { _, error ->
    WaiveraLog.e(tag, "Background job failed: ${error.javaClass.name}")
}
