package com.waivera.waivera.feature.chat.message

import androidx.compose.runtime.Immutable
import com.waivera.waivera.core.data.model.RealtimeStatus
import com.waivera.waivera.core.designsystem.component.ConnectionBannerState
import com.waivera.waivera.core.realtime.ConnectionState

/**
 * The hub connection as a chat screen shows it (05 §2). The banner is the one connection signal
 * on the screen: the header never repeats it (UI review UI2-02).
 */
@Immutable
data class ConnectionUi(val banner: ConnectionBannerState) {
    companion object {
        val Connected = ConnectionUi(ConnectionBannerState.Connected)

        fun from(status: RealtimeStatus): ConnectionUi = when {
            !status.isOnline -> ConnectionUi(ConnectionBannerState.Offline)
            else -> when (status.state) {
                ConnectionState.Connected -> Connected
                is ConnectionState.Reconnecting -> ConnectionUi(ConnectionBannerState.Reconnecting)
                // Disconnected while a chat is open means the client is starting up; it is not offline.
                ConnectionState.Connecting, ConnectionState.Disconnected -> ConnectionUi(
                    ConnectionBannerState.Connecting,
                )
            }
        }
    }
}
