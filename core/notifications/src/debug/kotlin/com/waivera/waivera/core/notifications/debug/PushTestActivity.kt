package com.waivera.waivera.core.notifications.debug

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.waivera.waivera.core.notifications.R
import com.waivera.waivera.core.notifications.ToastPushTrace
import com.waivera.waivera.core.notifications.channel.WaiveraChannel

/**
 * Debug builds only: a second launcher entry ("Waivera push test") for testers without adb. It
 * shows what the system reports about Waivera's notifications (settings, channels, the active
 * list and the latest push notes) and posts a plain test notification on demand. Plain views,
 * so it works even when the app's own UI does not.
 */
class PushTestActivity : Activity() {
    private lateinit var report: TextView
    private var testCount = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val padding = resources.getDimensionPixelSize(R.dimen.core_notifications_debug_padding)
        report = TextView(this).apply { setTextIsSelectable(true) }
        val post = Button(this).apply {
            text = getString(R.string.core_notifications_debug_post_test)
            setOnClickListener { postTest() }
        }
        val refresh = Button(this).apply {
            text = getString(R.string.core_notifications_debug_refresh)
            setOnClickListener { render() }
        }
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
            addView(post)
            addView(refresh)
            addView(report)
        }
        setContentView(ScrollView(this).apply { addView(column) })
        // Below the status bar on edge-to-edge devices.
        ViewCompat.setOnApplyWindowInsetsListener(column) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(padding, padding + bars.top, padding, padding + bars.bottom)
            insets
        }
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun postTest() {
        // Without the permission the system drops the post silently, so say so instead.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            report.text = getString(
                R.string.core_notifications_debug_post_failed,
                Manifest.permission.POST_NOTIFICATIONS,
            )
            return
        }
        testCount += 1
        val notification = NotificationCompat.Builder(this, WaiveraChannel.Messages.id)
            .setSmallIcon(R.drawable.core_notifications_ic_notification)
            .setContentTitle(getString(R.string.core_notifications_debug_test_title, testCount))
            .setContentText(getString(R.string.core_notifications_debug_test_text))
            .build()
        runCatching { NotificationManagerCompat.from(this).notify(TEST_TAG, testCount, notification) }
            .onFailure { report.text = getString(R.string.core_notifications_debug_post_failed, it.javaClass.name) }
        report.postDelayed({ render() }, RENDER_DELAY_MILLIS)
    }

    // A diagnostic report read by developers, not product copy.
    @SuppressLint("SetTextI18n")
    private fun render() {
        val system = getSystemService<NotificationManager>() ?: return
        val lines = buildList {
            add("Android ${Build.VERSION.SDK_INT}, ${Build.MANUFACTURER} ${Build.MODEL}")
            add("app enabled ${system.areNotificationsEnabled()}")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) add("app paused ${system.areNotificationsPaused()}")
            add("interruption filter ${system.currentInterruptionFilter}")
            add("")
            add("Channels (importance: 0 off, 2 low, 3 default, 4 high)")
            system.notificationChannels.forEach { add("  ${it.id}: ${it.importance}") }
            add("")
            val active = system.activeNotifications
            add("Active notifications: ${active.size}")
            active.forEach {
                add("  ${it.tag} #${it.id} channel ${it.notification.channelId} group ${it.notification.group}")
            }
            add("")
            add("Latest push notes")
            ToastPushTrace.recentNotes().forEach { add("  $it") }
        }
        report.text = lines.joinToString("\n")
    }

    private companion object {
        const val TEST_TAG = "debug-test"
        const val RENDER_DELAY_MILLIS = 1_000L
    }
}
