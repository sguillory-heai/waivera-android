package com.waivera.waivera.feature.chat.message

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.waivera.waivera.core.data.model.RealtimeStatus
import com.waivera.waivera.core.designsystem.component.ConnectionBannerState
import com.waivera.waivera.core.realtime.ConnectionState
import com.waivera.waivera.feature.chat.R

/** The hub connection as a chat screen shows it: a header subtitle and the quiet banner (05 §2). */
@Immutable
data class ConnectionUi(val banner: ConnectionBannerState, @StringRes val subtitle: Int?) {
    companion object {
        val Connected = ConnectionUi(ConnectionBannerState.Connected, null)

        fun from(status: RealtimeStatus): ConnectionUi = when {
            !status.isOnline -> ConnectionUi(ConnectionBannerState.Offline, R.string.feature_chat_subtitle_offline)
            else -> when (status.state) {
                ConnectionState.Connected -> Connected
                is ConnectionState.Reconnecting ->
                    ConnectionUi(ConnectionBannerState.Reconnecting, R.string.feature_chat_subtitle_reconnecting)
                // Disconnected while a chat is open means the client is starting up; it is not offline.
                ConnectionState.Connecting, ConnectionState.Disconnected ->
                    ConnectionUi(ConnectionBannerState.Connecting, R.string.feature_chat_subtitle_connecting)
            }
        }
    }
}
