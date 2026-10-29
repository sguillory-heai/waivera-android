package com.waivera.waivera.feature.chat.message

import android.view.View
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.toSize

/**
 * Where the pressed bubble was on screen when its long press fired, so the focus overlay can lift
 * that bubble where it is. One per chat screen: the bubble writes it in its long-press handler,
 * just before it asks for the actions, and the overlay reads it once as it opens. It holds plain
 * values, not state, because nothing redraws from it.
 */
@Stable
class MessageFocusAnchors {
    private var lastMessageId: Long? = null
    private var lastBounds: Rect? = null

    /** Records [bounds] (screen pixels) as where [messageId] was pressed; null forgets it. */
    fun record(messageId: Long, bounds: Rect?) {
        lastMessageId = messageId.takeIf { bounds != null }
        lastBounds = bounds
    }

    /** The pressed bubble's screen bounds, if [messageId] is the one last pressed. */
    fun boundsOf(messageId: Long): Rect? = lastBounds.takeIf { lastMessageId == messageId }
}

/** The screen's [MessageFocusAnchors]; null where no overlay can open (previews, read-only lists). */
val LocalMessageFocusAnchors = staticCompositionLocalOf<MessageFocusAnchors?> { null }

/**
 * A node's live coordinates, kept from its last placement without state, so a press can ask where
 * the node is at that moment. Unclipped: a long bubble scrolled half under the top bar still
 * reports its whole height, so its lifted copy opens at its start.
 */
internal class AnchorCoordinates {
    var coordinates: LayoutCoordinates? = null

    /** The node's bounds in screen pixels, or null when it is not laid out. */
    fun screenBounds(view: View): Rect? {
        val node = coordinates?.takeIf { it.isAttached } ?: return null
        val origin = IntArray(2)
        view.getLocationOnScreen(origin)
        return Rect(node.positionInRoot(), node.size.toSize()).translate(origin[0].toFloat(), origin[1].toFloat())
    }
}
