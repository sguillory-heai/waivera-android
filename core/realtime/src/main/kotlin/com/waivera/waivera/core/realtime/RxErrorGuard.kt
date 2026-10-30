package com.waivera.waivera.core.realtime

import com.waivera.waivera.core.network.log.WaiveraLog
import io.reactivex.rxjava3.exceptions.UndeliverableException
import io.reactivex.rxjava3.plugins.RxJavaPlugins

/**
 * The SignalR Java client runs on RxJava. When a handshake or invocation fails after its
 * subscriber is gone (a coroutine cancelled by backgrounding or sign-out, a second socket
 * failure), RxJava reports an [UndeliverableException] to its global handler, which by default
 * rethrows on the socket thread and kills the app. The hub is an optional channel with REST as
 * the source of truth, so such late failures are logged by class and dropped.
 */
internal object RxErrorGuard {
    private const val TAG = "Realtime"

    /** Installs the handler unless the app already set its own. Safe to call more than once. */
    @Synchronized
    fun install() {
        if (RxJavaPlugins.getErrorHandler() != null || RxJavaPlugins.isLockdown()) return
        RxJavaPlugins.setErrorHandler { error ->
            val cause = if (error is UndeliverableException) error.cause ?: error else error
            WaiveraLog.w(TAG, "Dropped late hub failure: ${cause.javaClass.name}")
        }
    }
}
