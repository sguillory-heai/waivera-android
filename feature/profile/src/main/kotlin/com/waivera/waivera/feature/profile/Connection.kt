package com.waivera.waivera.feature.profile

import com.waivera.waivera.core.data.model.RealtimeStatus
import com.waivera.waivera.core.designsystem.component.ConnectionBannerState
import com.waivera.waivera.core.realtime.ConnectionState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

/**
 * These screens are REST-backed, so only "offline" and "reconnecting" are worth a banner; the
 * first connect after launch stays quiet.
 */
internal fun RealtimeStatus.toBannerState(): ConnectionBannerState = when {
    !isOnline -> ConnectionBannerState.Offline
    state is ConnectionState.Reconnecting -> ConnectionBannerState.Reconnecting
    else -> ConnectionBannerState.Connected
}

/** Emits each time the hub comes back after being down, so screens can refetch what they missed. */
internal fun Flow<RealtimeStatus>.reconnections(): Flow<Unit> = map { it.isConnected && it.isOnline }
    .distinctUntilChanged()
    .drop(1)
    .filter { it }
    .map { }

/**
 * One connection signal per screen. A failed load already explains itself, so the connection
 * banner stands down while the screen (or its main card) shows an error.
 */
internal fun bannerFor(connection: ConnectionBannerState, showsLoadError: Boolean): ConnectionBannerState =
    if (showsLoadError) ConnectionBannerState.Connected else connection

/** "Couldn't refresh" shows only when no connection banner is up; offline already says the data is saved. */
internal fun showsRefreshFailed(connection: ConnectionBannerState, refreshFailed: Boolean): Boolean =
    refreshFailed && connection == ConnectionBannerState.Connected
