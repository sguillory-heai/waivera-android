package com.waivera.waivera.feature.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.waivera.waivera.feature.chat.message.MessageRowCallbacks
import com.waivera.waivera.feature.chat.message.TimelineItem
import kotlinx.coroutines.delay

/**
 * How the conversation list moves while a send lands or a time line opens (M2-01, M2-05).
 *
 * [landingKeys] are the rows that arrived with my own send, the bubble and any day or time line
 * inserted above it, which grow to their height instead of appearing whole. While a send lands or
 * a footer toggles, [holdsPlacement] turns the list's placement animation off: the list lays out
 * again on every frame of the growth, so the rows above follow it exactly, whereas a placement
 * spring chased each frame's shift and trailed it, and the growing row drew over its neighbour
 * for about 70 ms. [rows] are the row callbacks, with the timestamp toggle starting that hold.
 */
@Immutable
internal class ListMotion(val landingKeys: Set<String>, val holdsPlacement: Boolean, val rows: MessageRowCallbacks)

/** Follows [newestFirst] for sends that land, and wraps [rows] so a timestamp toggle holds placement. */
@Composable
internal fun rememberListMotion(newestFirst: List<TimelineItem>, rows: MessageRowCallbacks): ListMotion {
    val newest = newestFirst.firstOrNull()
    // What was already newest when the list first showed (a send from before a rotation or a
    // return to the screen) does not land again.
    val initialNewestKey = remember { newest?.key }
    val landingKey = newest?.key?.takeIf { it != initialNewestKey && newest.isOwnLiveSend() }
    var settledLanding by remember { mutableStateOf<String?>(null) }
    val landing = landingKey?.takeIf { it != settledLanding }
    LaunchedEffect(landing) {
        if (landing == null) return@LaunchedEffect
        delay(LIST_SETTLE_MILLIS)
        settledLanding = landing
    }
    var footerToggles by remember { mutableIntStateOf(0) }
    var footerSettling by remember { mutableStateOf(false) }
    LaunchedEffect(footerToggles) {
        if (footerToggles == 0) return@LaunchedEffect
        delay(LIST_SETTLE_MILLIS)
        footerSettling = false
    }
    val toggling = remember(rows) {
        rows.withToggleTimestamp { key ->
            footerSettling = true
            footerToggles++
            rows.onToggleTimestamp(key)
        }
    }
    val landingKeys = remember(newestFirst, landing) {
        if (landing == null) emptySet() else landingRows(newestFirst)
    }
    return ListMotion(landingKeys = landingKeys, holdsPlacement = landing != null || footerSettling, rows = toggling)
}

/** My own message arriving while the list is on screen: a send from here or another device. */
internal fun TimelineItem?.isOwnLiveSend(): Boolean = this is TimelineItem.MessageRow && isMine && isLiveArrival

/**
 * The rows that land with the newest send: the send, and the day or time line right above it,
 * which only exists because of that send (it opens the day or ends a pause).
 */
internal fun landingRows(newestFirst: List<TimelineItem>): Set<String> {
    val send = newestFirst.firstOrNull() ?: return emptySet()
    val line = newestFirst.getOrNull(1)?.takeIf { it is TimelineItem.DaySeparator || it is TimelineItem.TimeSeparator }
    return setOfNotNull(send.key, line?.key)
}

/**
 * How long a landing or a toggled time line holds the placement animation off: the layout
 * spring has settled to under a pixel by then.
 */
internal const val LIST_SETTLE_MILLIS = 250L
