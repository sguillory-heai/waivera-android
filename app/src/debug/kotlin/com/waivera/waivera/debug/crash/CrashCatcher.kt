package com.waivera.waivera.debug.crash

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import java.time.Instant

/**
 * Debug builds only. Saves a redacted report of an uncaught exception, then hands the exception
 * to the previous handler so the process dies exactly as before. On the next launch the report is
 * offered on [CrashReportActivity] above the app's first screen.
 */
object CrashCatcher {
    @Volatile
    private var installed = false

    @Suppress("TooGenericExceptionCaught")
    fun install(context: Context) {
        if (installed) return
        installed = true
        val app = context.applicationContext
        val store = CrashReportStore(app.filesDir)
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                store.save(CrashReportRedactor.report(error, thread.name, header(app)))
            } catch (ignored: Throwable) {
                // Saving is best effort: the original crash must still reach the platform.
            }
            previous?.uncaughtException(thread, error)
        }
        (app as? Application)?.registerActivityLifecycleCallbacks(ShowOnLaunch(store))
    }

    private fun header(context: Context): String = buildString {
        appendLine("Waivera debug crash report")
        appendLine("Time: ${Instant.now()}")
        val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
        appendLine("App: ${version.getOrNull().orEmpty()}")
        append("Device: Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) ${Build.MODEL}")
    }

    /** Opens the report screen once per process, over the first app screen that starts. */
    private class ShowOnLaunch(private val store: CrashReportStore) : Application.ActivityLifecycleCallbacks {
        private var checked = false

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
            if (checked || activity is CrashReportActivity) return
            checked = true
            if (store.read() != null) activity.startActivity(Intent(activity, CrashReportActivity::class.java))
        }

        override fun onActivityStarted(activity: Activity) = Unit

        override fun onActivityResumed(activity: Activity) = Unit

        override fun onActivityPaused(activity: Activity) = Unit

        override fun onActivityStopped(activity: Activity) = Unit

        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

        override fun onActivityDestroyed(activity: Activity) = Unit
    }
}
