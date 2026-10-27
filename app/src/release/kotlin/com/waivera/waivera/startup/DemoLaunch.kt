package com.waivera.waivera.startup

import android.app.Activity
import android.content.Intent

/** Release builds have no demo mode, so a launching intent's `waivera.fake` extra is ignored. */
object DemoLaunch {
    @Suppress("UnusedParameter", "FunctionOnlyReturningConstant")
    fun restartIfChanged(activity: Activity, intent: Intent?): Boolean = false
}
