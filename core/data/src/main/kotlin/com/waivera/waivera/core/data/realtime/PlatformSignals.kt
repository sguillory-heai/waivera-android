package com.waivera.waivera.core.data.realtime

import kotlinx.coroutines.flow.StateFlow

/** Whether the device has a validated network. */
interface ConnectivityMonitor {
    val isOnline: StateFlow<Boolean>
}

/** Whether any activity of the app is started (process-level foreground). */
interface AppForegroundMonitor {
    val isForeground: StateFlow<Boolean>
}
