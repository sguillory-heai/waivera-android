package com.waivera.waivera.startup

import android.content.Context
import android.content.Intent
import androidx.core.content.edit

/**
 * Debug only: `adb shell am start -S -n com.waivera.waivera/.MainActivity --ez waivera.fake true`
 * switches the data layer to the fake server, and `--ez waivera.fake false` switches it back.
 * The choice is stored so it holds across restarts; `-Pwaivera.demoMode=true` forces it at build time.
 */
object DemoModeLaunchFlag {
    const val EXTRA = "waivera.fake"
    const val PREFS = "waivera_debug"
    const val KEY = "fake_server"

    fun record(context: Context, intent: Intent?) {
        if (intent == null || !intent.hasExtra(EXTRA)) return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putBoolean(KEY, intent.getBooleanExtra(EXTRA, false))
        }
    }

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, false)
}
