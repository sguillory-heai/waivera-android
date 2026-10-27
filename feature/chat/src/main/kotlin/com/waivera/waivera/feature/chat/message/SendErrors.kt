package com.waivera.waivera.feature.chat.message

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
     * For an outbox row that turned FAILED: the outbox does not expose its error yet
     * (docs/requests/conversation.md), so the banner infers the likeliest cause.
     */
    fun forFailedSend(isOnline: Boolean, canWrite: Boolean?, isArchived: Boolean): SendErrorKind = when {
        isArchived -> SendErrorKind.Archived
        canWrite == false -> SendErrorKind.ReadOnly
        !isOnline -> SendErrorKind.Offline
        else -> SendErrorKind.Server
    }
}

/** Why the composer is replaced by the lock notice (03 §18, 04 §7). */
enum class ReadOnlyReason { Automated, ReadOnlyChannel, ArchivedChannel }
