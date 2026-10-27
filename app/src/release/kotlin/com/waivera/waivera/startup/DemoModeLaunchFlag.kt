package com.waivera.waivera.startup

import android.content.Context
import android.content.Intent

/** Release builds never switch to the fake server, whatever a launching intent carries. */
object DemoModeLaunchFlag {
    @Suppress("UnusedParameter")
    fun record(context: Context, intent: Intent?) = Unit

    @Suppress("UnusedParameter", "FunctionOnlyReturningConstant")
    fun isEnabled(context: Context): Boolean = false
}
