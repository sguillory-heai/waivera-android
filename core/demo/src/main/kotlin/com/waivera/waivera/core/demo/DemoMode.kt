package com.waivera.waivera.core.demo

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.core.content.edit
import com.waivera.waivera.core.data.auth.FirebaseConfig
import com.waivera.waivera.core.network.BuildConfig as NetworkBuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.system.exitProcess

/**
 * Decides, once per process, whether the app runs on demo data. Demo mode is on when any of:
 *  1. the build was made with `-Pwaivera.demoMode=true`;
 *  2. a launch intent carried `waivera.fake=true` (remembered until `waivera.fake=false`);
 *  3. the API base URL or Firebase values are missing, so the live app could not work.
 * This module is a debug-only dependency, so none of this exists in release builds.
 */
@Singleton
class DemoMode @Inject constructor(@ApplicationContext context: Context) {
    val reason: Reason? = when {
        BuildConfig.DEMO_MODE -> Reason.BUILD_FLAG
        prefs(context).getBoolean(KEY_ENABLED, false) -> Reason.LAUNCH_FLAG
        NetworkBuildConfig.API_BASE_URL.isBlank() -> Reason.MISSING_CONFIGURATION
        FirebaseConfig.fromBuildConfig().missingKeys.isNotEmpty() -> Reason.MISSING_CONFIGURATION
        else -> null
    }

    val isEnabled: Boolean get() = reason != null

    enum class Reason { BUILD_FLAG, LAUNCH_FLAG, MISSING_CONFIGURATION }

    companion object {
        const val EXTRA_FAKE = "waivera.fake"
        private const val PREFS = "waivera_demo_mode"
        private const val KEY_ENABLED = "enabled"

        private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        /**
         * Call from `MainActivity.onCreate` before `super.onCreate`. When the intent changes the
         * remembered flag the process restarts, because repositories are chosen once per process.
         * Returns true when a restart was started (the activity should return immediately).
         */
        fun applyLaunchIntent(activity: Activity, intent: Intent?): Boolean {
            if (intent?.hasExtra(EXTRA_FAKE) != true) return false
            val wanted = intent.getBooleanExtra(EXTRA_FAKE, false) || intent.getStringExtra(EXTRA_FAKE) == "true"
            val prefs = prefs(activity)
            if (prefs.getBoolean(KEY_ENABLED, false) == wanted) return false
            // commit() so the value is on disk before the process exits.
            prefs.edit(commit = true) { putBoolean(KEY_ENABLED, wanted) }
            val restart = activity.packageManager.getLaunchIntentForPackage(activity.packageName)
                ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            activity.finishAffinity()
            restart?.let(activity::startActivity)
            exitProcess(0)
        }
    }
}
