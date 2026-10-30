package com.waivera.waivera.core.realtime

import io.reactivex.rxjava3.exceptions.UndeliverableException
import io.reactivex.rxjava3.functions.Consumer
import io.reactivex.rxjava3.plugins.RxJavaPlugins
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import org.junit.After
import org.junit.Before

class RxErrorGuardTest {
    private val uncaught = mutableListOf<Throwable>()
    private var previousHandler: Thread.UncaughtExceptionHandler? = null

    @Before
    fun setUp() {
        RxJavaPlugins.reset()
        val thread = Thread.currentThread()
        previousHandler = thread.uncaughtExceptionHandler
        thread.uncaughtExceptionHandler = Thread.UncaughtExceptionHandler { _, error -> uncaught += error }
    }

    @After
    fun tearDown() {
        Thread.currentThread().uncaughtExceptionHandler = previousHandler
        RxJavaPlugins.reset()
    }

    @Test
    fun withoutTheGuardALateFailureReachesTheThreadHandler() {
        RxJavaPlugins.onError(UndeliverableException(IOException("socket closed")))

        assertNotNull(uncaught.firstOrNull(), "this is the crash the guard prevents")
    }

    @Test
    fun aLateHubFailureNoLongerReachesTheThreadHandler() {
        RxErrorGuard.install()

        RxJavaPlugins.onError(UndeliverableException(IOException("socket closed")))

        assertNotNull(RxJavaPlugins.getErrorHandler())
        assertNull(uncaught.firstOrNull())
    }

    @Test
    fun anExistingAppHandlerIsKept() {
        val appHandler = Consumer<Throwable> { }
        RxJavaPlugins.setErrorHandler(appHandler)

        RxErrorGuard.install()

        assertSame(appHandler, RxJavaPlugins.getErrorHandler())
    }
}
