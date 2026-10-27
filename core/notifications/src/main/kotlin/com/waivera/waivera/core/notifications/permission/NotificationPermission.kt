package com.waivera.waivera.core.notifications.permission

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** System notification permission as Settings → Notifications presents it. */
enum class NotificationPermissionStatus {
    /** Notifications can be posted. */
    Granted,

    /** Android 13+ and the runtime permission has not been asked yet (the primer has not run). */
    NotRequested,

    /** Denied, or notifications turned off for the app in system settings. Show a link to system settings. */
    Denied,
}

/** Pure rules for the permission primer, kept free of Android types so they are unit tested. */
object PermissionPrimerRules {
    private const val RUNTIME_PERMISSION_SDK = 33

    /**
     * The primer shows once, right after the first sign-in, on Android 13+ when permission is
     * not granted. It is never re-shown on later launches (re-asking every launch is an
     * anti-pattern); Settings → Notifications carries the denied state from then on.
     */
    fun shouldShowPrimer(sdkInt: Int, permissionGranted: Boolean, primerCompleted: Boolean): Boolean =
        sdkInt >= RUNTIME_PERMISSION_SDK && !permissionGranted && !primerCompleted

    fun status(
        sdkInt: Int,
        notificationsEnabled: Boolean,
        permissionGranted: Boolean,
        primerCompleted: Boolean,
    ): NotificationPermissionStatus = when {
        notificationsEnabled && (sdkInt < RUNTIME_PERMISSION_SDK || permissionGranted) ->
            NotificationPermissionStatus.Granted
        sdkInt >= RUNTIME_PERMISSION_SDK && !permissionGranted && !primerCompleted ->
            NotificationPermissionStatus.NotRequested
        else -> NotificationPermissionStatus.Denied
    }
}

/**
 * Permission and primer state for the app shell (primer route) and Settings. Call [refresh]
 * on resume, since the person can change the permission in system settings at any time.
 * The primer flag is a non-sensitive UI flag in app-private preferences, excluded from backup.
 */
@Singleton
class NotificationPermissionState @Inject constructor(@ApplicationContext private val context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val statusFlow = MutableStateFlow(computeStatus())
    private val primerPendingFlow = MutableStateFlow(computePrimerPending())

    val status: StateFlow<NotificationPermissionStatus> = statusFlow.asStateFlow()

    /** True while the shell should show the primer before the main tabs. */
    val primerPending: StateFlow<Boolean> = primerPendingFlow.asStateFlow()

    fun refresh() {
        statusFlow.value = computeStatus()
        primerPendingFlow.value = computePrimerPending()
    }

    /** Called when the person leaves the primer, whichever button they pressed. */
    fun markPrimerCompleted() {
        prefs.edit { putBoolean(KEY_PRIMER_COMPLETED, true) }
        refresh()
    }

    private fun computeStatus(): NotificationPermissionStatus = PermissionPrimerRules.status(
        sdkInt = Build.VERSION.SDK_INT,
        notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled(),
        permissionGranted = runtimePermissionGranted(),
        primerCompleted = prefs.getBoolean(KEY_PRIMER_COMPLETED, false),
    )

    private fun computePrimerPending(): Boolean = PermissionPrimerRules.shouldShowPrimer(
        sdkInt = Build.VERSION.SDK_INT,
        permissionGranted = runtimePermissionGranted(),
        primerCompleted = prefs.getBoolean(KEY_PRIMER_COMPLETED, false),
    )

    private fun runtimePermissionGranted(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED

    private companion object {
        const val PREFS_NAME = "waivera_notifications"
        const val KEY_PRIMER_COMPLETED = "primer_completed"
    }
}
