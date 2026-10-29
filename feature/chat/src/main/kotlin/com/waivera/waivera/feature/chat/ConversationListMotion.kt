package com.waivera.waivera.feature.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import com.waivera.waivera.feature.chat.message.ChatSheet
import com.waivera.waivera.feature.chat.message.MessageRowCallbacks
import com.waivera.waivera.feature.chat.message.TimelineItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * How the conversation list moves while a send lands or a time line opens (M2-01, M2-05).
 *
 * [landingKeys] are the rows that arrived with my own send, the bubble and any day or time line
 * inserted above it, which grow to their height instead of appearing whole. While a send lands or
 * a footer toggles, [holdsPlacement] turns the list's placement animation off: the list lays out
 * again on every frame of the growth, so the rows above follow it exactly, whereas a placement
 * spring chased each frame's shift and trailed it, and the growing row drew over its neighbour
 * for about 70 ms. The same hold covers the long-press focus overlay's close
 * ([rememberFocusCloseHold]). [rows] are the row callbacks, with the timestamp toggle starting
 * that hold.
 */
@Immutable
internal class ListMotion(val landingKeys: Set<String>, val holdsPlacement: Boolean, val rows: MessageRowCallbacks)

/**
 * Follows [newestFirst] for sends that land, and wraps [rows] so a timestamp toggle holds
 * placement. [sheet] is the screen's open sheet, so the focus overlay's close holds it too.
 */
@Composable
internal fun rememberListMotion(
    newestFirst: List<TimelineItem>,
    rows: MessageRowCallbacks,
    sheet: ChatSheet? = null,
): ListMotion {
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
    val focusClosing = rememberFocusCloseHold(sheet, newestFirst)
    return ListMotion(
        landingKeys = landingKeys,
        holdsPlacement = landing != null || footerSettling || focusClosing,
        rows = toggling,
    )
}

/**
 * Whether a list under the long-press focus overlay holds its placement animation off (wave 30
 * O4): while the overlay is open, and as it closes until the change it led to has landed. The
 * overlay closes at once, and a reaction then grows its row by the new chip in one frame; with the
 * placement spring on, the rows above chased that growth and the "Today" line drew over the bubble
 * for about three frames. Held, the list lays the rows out where they belong in that same frame.
 *
 * The hold is on in the very composition the overlay closes in, since a fake or a fast cache can
 * deliver the reaction with the close. It then waits for the list's next change (the reaction, an
 * edit, a delete), at most [FOCUS_CLOSE_WAIT_MILLIS] when nothing changes (a scrim tap), and lets
 * the list settle for [LIST_SETTLE_MILLIS] before placement animates again.
 */
@Composable
internal fun rememberFocusCloseHold(sheet: ChatSheet?, items: List<Any>): Boolean {
    val open = sheet as? ChatSheet.Actions
    val last = remember { LastFocus() }
    // A plain holder, written as the overlay shows, so the close is known in its own composition.
    if (open != null) last.sheet = open
    var settled by remember { mutableStateOf<ChatSheet.Actions?>(null) }
    val closing = last.sheet?.takeIf { open == null && it !== settled }
    val latestItems by rememberUpdatedState(items)
    LaunchedEffect(closing) {
        val closed = closing ?: return@LaunchedEffect
        val atClose = latestItems
        withTimeoutOrNull(FOCUS_CLOSE_WAIT_MILLIS) { snapshotFlow { latestItems }.first { it !== atClose } }
        delay(LIST_SETTLE_MILLIS)
        settled = closed
    }
    return open != null || closing != null
}

/** The focus overlay last shown over a list; each opening is a new sheet, compared by identity. */
private class LastFocus {
    var sheet: ChatSheet.Actions? = null
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

/**
 * The longest the focus overlay's close waits for the change it led to: a reaction reaches the
 * list from the cache in well under this, and a close with no change ends its hold here.
 */
internal const val FOCUS_CLOSE_WAIT_MILLIS = 600L
