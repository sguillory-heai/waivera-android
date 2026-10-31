package com.waivera.waivera.core.notifications

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.annotation.StringRes
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Where a push ended up. Steps and details never carry payload text, names or tokens. */
enum class PushStep(@StringRes val text: Int) {
    Shown(R.string.core_notifications_debug_shown),
    Hidden(R.string.core_notifications_debug_hidden),
    CannotPost(R.string.core_notifications_debug_cannot_post),
    Unusable(R.string.core_notifications_debug_unusable),
    Failed(R.string.core_notifications_debug_failed),
    NoToken(R.string.core_notifications_debug_no_token),
    Cleared(R.string.core_notifications_debug_cleared),
    Gone(R.string.core_notifications_debug_gone),
    Muted(R.string.core_notifications_debug_muted),
    ClearedAll(R.string.core_notifications_debug_cleared_all),
    Received(R.string.core_notifications_debug_received),
}

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
        val id = step.text
        val text = if (detail == null) context.getString(id) else context.getString(id, detail)
        synchronized(recent) {
            recent.addLast("${java.time.LocalTime.now().withNano(0)}  $text")
            while (recent.size > MAX_RECENT) recent.removeFirst()
        }
        main.post { Toast.makeText(context, text, Toast.LENGTH_LONG).show() }
    }

    companion object {
        private const val MAX_RECENT = 40
        private val recent = ArrayDeque<String>()

        /** The latest notes, oldest first, for the debug push test screen; empty in release builds. */
        fun recentNotes(): List<String> = synchronized(recent) { recent.toList() }
    }
}
