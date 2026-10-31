package com.waivera.waivera.core.notifications

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Where a push ended up. Steps and details never carry payload text, names or tokens. */
enum class PushStep { Shown, Hidden, CannotPost, Unusable, Failed, NoToken, Cleared, Gone }

/** Notes each push outcome, so a tester holding the phone can tell where a push stopped. */
fun interface PushTrace {
    fun note(step: PushStep, detail: String?)
}

/**
 * Shows the note as a short toast, in debuggable builds only: testers on a real phone have no
 * adb, and a push that ends without a notification otherwise leaves no trace at all.
 */
class ToastPushTrace @Inject constructor(@ApplicationContext private val context: Context) : PushTrace {
    private val main = Handler(Looper.getMainLooper())

    override fun note(step: PushStep, detail: String?) {
        if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) return
        val id = when (step) {
            PushStep.Shown -> R.string.core_notifications_debug_shown
            PushStep.Hidden -> R.string.core_notifications_debug_hidden
            PushStep.CannotPost -> R.string.core_notifications_debug_cannot_post
            PushStep.Unusable -> R.string.core_notifications_debug_unusable
            PushStep.Failed -> R.string.core_notifications_debug_failed
            PushStep.NoToken -> R.string.core_notifications_debug_no_token
            PushStep.Cleared -> R.string.core_notifications_debug_cleared
            PushStep.Gone -> R.string.core_notifications_debug_gone
        }
        val text = if (detail == null) context.getString(id) else context.getString(id, detail)
        main.post { Toast.makeText(context, text, Toast.LENGTH_LONG).show() }
    }
}
