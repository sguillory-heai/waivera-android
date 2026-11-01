package com.waivera.waivera.core.notifications

import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Where a push ended up. Steps and details never carry payload text, names or tokens. */
enum class PushStep { Shown, Hidden, CannotPost, Unusable, Failed, NoToken, Cleared, Gone, Muted, ClearedAll, Received }

/** Notes each push outcome, so a developer can tell where a push stopped. */
fun interface PushTrace {
    fun note(step: PushStep, detail: String?)
}

/** Writes the note to Logcat in debuggable builds only; release builds stay silent. */
class LogcatPushTrace @Inject constructor(@ApplicationContext private val context: Context) : PushTrace {
    override fun note(step: PushStep, detail: String?) {
        if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) return
        Log.d(TAG, if (detail == null) step.name else "${step.name}: $detail")
    }

    private companion object {
        const val TAG = "WaiveraPush"
    }
}
