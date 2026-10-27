package com.waivera.waivera.feature.chat.message

import androidx.compose.runtime.Immutable
import com.waivera.waivera.core.data.model.SendState
import com.waivera.waivera.core.network.error.WaiveraError

/** The "Not sent" banner copy (03 §11), chosen from typed errors instead of message sniffing. */
enum class SendErrorKind { Archived, ReadOnly, Offline, Session, Server }

object SendErrors {
    fun from(error: WaiveraError): SendErrorKind = when (error) {
        is WaiveraError.ChannelArchived -> SendErrorKind.Archived
        is WaiveraError.ChannelReadOnly, is WaiveraError.Forbidden -> SendErrorKind.ReadOnly
        is WaiveraError.Network, is WaiveraError.Timeout, is WaiveraError.RealtimeUnavailable -> SendErrorKind.Offline
        is WaiveraError.SessionExpired, is WaiveraError.NotSignedIn -> SendErrorKind.Session
        else -> SendErrorKind.Server
    }

    /**
     * For an outbox row that turned FAILED: the reason the outbox recorded (`Message.sendError`)
     * when there is one, else the likeliest cause from the connection and the permissions.
     */
    fun forFailedSend(
        error: WaiveraError?,
        isOnline: Boolean,
        canWrite: Boolean?,
        isArchived: Boolean,
    ): SendErrorKind = when {
        error != null -> from(error)
        isArchived -> SendErrorKind.Archived
        canWrite == false -> SendErrorKind.ReadOnly
        !isOnline -> SendErrorKind.Offline
        else -> SendErrorKind.Server
    }
}

/** Why the composer is replaced by the lock notice (03 §18, 04 §7). */
enum class ReadOnlyReason { Automated, ReadOnlyChannel, ArchivedChannel }

/**
 * How a screen signals a send that failed (UI review UI2-02): one signal, never three. The failed
 * row's inline "Failed · Retry" is the signal while it is on screen; a banner shows only when
 * there is no row to point at ([Banner], the send was refused before it reached the outbox) or
 * the row is scrolled out of view ([Offscreen], "1 message not sent · Show").
 */
@Immutable
sealed interface SendErrorUi {
    data object None : SendErrorUi

    data class Banner(val kind: SendErrorKind) : SendErrorUi

    /** [index] is the list position of the newest failed row, for "Show". */
    data class Offscreen(val count: Int, val index: Int) : SendErrorUi

    companion object {
        /**
         * [failedIndices] are the list positions of failed rows, newest first; [visibleIndices]
         * the positions laid out now (empty before the first layout, which shows nothing yet).
         * While the connection banner already says the app is offline, a refused send's Offline
         * banner would repeat it, so the restored draft and that banner are the signal (UI3-07).
         */
        fun of(
            sendError: SendErrorKind?,
            failedIndices: List<Int>,
            visibleIndices: List<Int>,
            connectionBannerShowing: Boolean = false,
        ): SendErrorUi = when {
            sendError == null -> None
            failedIndices.isEmpty() && sendError == SendErrorKind.Offline && connectionBannerShowing -> None
            failedIndices.isEmpty() -> Banner(sendError)
            visibleIndices.isEmpty() -> None
            failedIndices.any { it in visibleIndices } -> None
            else -> Offscreen(count = failedIndices.size, index = failedIndices.first())
        }

        /** True for my own row whose send failed for good. */
        fun isFailedRow(item: TimelineItem): Boolean =
            item is TimelineItem.MessageRow && item.message.sendState == SendState.FAILED
    }
}
