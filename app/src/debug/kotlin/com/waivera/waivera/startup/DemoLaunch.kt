package com.waivera.waivera.startup

import android.app.Activity
import android.content.Intent
import com.waivera.waivera.core.demo.DemoMode

/**
 * Debug only: `adb shell am start -S -n com.waivera.waivera/.MainActivity --ez waivera.fake true`
 * switches the data layer to demo data and `--ez waivera.fake false` switches it back. The choice
 * belongs to `:core:demo`'s [DemoMode] (see docs/architecture/data-layer.md).
 */
object DemoLaunch {
    /** True when the flag changed and the process is restarting; the activity must stop at once. */
    fun restartIfChanged(activity: Activity, intent: Intent?): Boolean = DemoMode.applyLaunchIntent(activity, intent)
}
