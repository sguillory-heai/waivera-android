package com.waivera.waivera.core.network.error

import androidx.annotation.StringRes
import com.waivera.waivera.core.network.R

/**
 * Every failure the UI can show. Each case carries client-owned copy in [messageRes]; server
 * text is never shown (gaps.md G-12, both AGENTS.md files). [httpStatus] and [errorCode] are for
 * branching and diagnostics only.
 */
sealed class WaiveraError(
    @StringRes val messageRes: Int,
    val httpStatus: Int? = null,
    val errorCode: String? = null,
    cause: Throwable? = null,
) : Exception(null, cause) {
    /** True when retrying the same request later can succeed without user action. */
    open val isTransient: Boolean get() = false

    /** No Firebase user, so the request was never sent. */
    class NotSignedIn : WaiveraError(R.string.core_network_error_not_signed_in, HTTP_UNAUTHORIZED)

    /** A 401 that survived one forced token refresh, or a revoked session. */
    class SessionExpired(errorCode: String? = null) :
        WaiveraError(R.string.core_network_error_session_expired, HTTP_UNAUTHORIZED, errorCode)

    /** A Firebase user with no Waivera record (bodyless 403 from the token middleware, or 404 on login). */
    class AccountNotProvisioned(httpStatus: Int? = null) :
        WaiveraError(R.string.core_network_error_account_not_provisioned, httpStatus)

    class Forbidden(errorCode: String? = null) :
        WaiveraError(R.string.core_network_error_forbidden, HTTP_FORBIDDEN, errorCode)

    class NotFound : WaiveraError(R.string.core_network_error_not_found, HTTP_NOT_FOUND)

    class Conflict : WaiveraError(R.string.core_network_error_conflict, HTTP_CONFLICT)

    /** A 400 the user can fix by changing input. */
    class Validation(errorCode: String? = null) :
        WaiveraError(R.string.core_network_error_validation, HTTP_BAD_REQUEST, errorCode)

    class ChannelArchived(httpStatus: Int? = HTTP_FORBIDDEN) :
        WaiveraError(R.string.core_network_error_channel_archived, httpStatus)

    class ChannelReadOnly(httpStatus: Int? = HTTP_FORBIDDEN) :
        WaiveraError(R.string.core_network_error_channel_read_only, httpStatus)

    /** The uploaded attachment was not claimed in time; upload it again. */
    class AttachmentExpired : WaiveraError(R.string.core_network_error_attachment_expired, HTTP_BAD_REQUEST)

    /** 423: blocked by security scanning (gaps.md G-15). */
    class AttachmentRejected : WaiveraError(R.string.core_network_error_attachment_rejected, HTTP_LOCKED)

    /** Scan still running; do not fetch yet. */
    class AttachmentScanPending : WaiveraError(R.string.core_network_error_attachment_pending)

    class AttachmentTooLarge : WaiveraError(R.string.core_network_error_attachment_too_large, HTTP_PAYLOAD_TOO_LARGE)

    class RateLimited : WaiveraError(R.string.core_network_error_rate_limited, HTTP_TOO_MANY_REQUESTS) {
        override val isTransient: Boolean get() = true
    }

    class Server(httpStatus: Int) : WaiveraError(R.string.core_network_error_server, httpStatus) {
        override val isTransient: Boolean get() = true
    }

    class Network(cause: Throwable? = null) : WaiveraError(R.string.core_network_error_network, cause = cause) {
        override val isTransient: Boolean get() = true
    }

    class Timeout(cause: Throwable? = null) : WaiveraError(R.string.core_network_error_timeout, cause = cause) {
        override val isTransient: Boolean get() = true
    }

    /** Required build configuration (API base URL, Firebase values) is missing. */
    class Configuration(val missingKeys: List<String>) : WaiveraError(R.string.core_network_error_configuration)

    /** The action needs a live hub connection (reactions, gaps.md G-01). */
    class RealtimeUnavailable : WaiveraError(R.string.core_network_error_realtime_unavailable) {
        override val isTransient: Boolean get() = true
    }

    class InvalidCredentials : WaiveraError(R.string.core_network_error_invalid_credentials)

    class AccountDisabled : WaiveraError(R.string.core_network_error_account_disabled)

    class TooManyAttempts : WaiveraError(R.string.core_network_error_too_many_attempts)

    class WeakPassword : WaiveraError(R.string.core_network_error_weak_password)

    /** Firebase needs the current password again before a sensitive change. */
    class RecentLoginRequired : WaiveraError(R.string.core_network_error_recent_login_required)

    /** Anything else, including undecodable payloads. Never carries server text. */
    class Unexpected(httpStatus: Int? = null, cause: Throwable? = null) :
        WaiveraError(R.string.core_network_error_unexpected, httpStatus, cause = cause)

    companion object {
        const val HTTP_BAD_REQUEST = 400
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_FORBIDDEN = 403
        const val HTTP_NOT_FOUND = 404
        const val HTTP_CONFLICT = 409
        const val HTTP_PAYLOAD_TOO_LARGE = 413
        const val HTTP_LOCKED = 423
        const val HTTP_TOO_MANY_REQUESTS = 429
        const val HTTP_SERVER_ERROR = 500

        /** Wraps any throwable; a [WaiveraError] passes through unchanged. */
        fun from(throwable: Throwable): WaiveraError = when (throwable) {
            is WaiveraError -> throwable
            is NotSignedInException -> NotSignedIn()
            is java.net.SocketTimeoutException -> Timeout(throwable)
            is java.io.IOException -> Network(throwable)
            else -> Unexpected(cause = throwable)
        }
    }
}

/**
 * Thrown by [com.waivera.waivera.core.network.AuthTokenInterceptor] when there is no signed-in
 * user. An IOException so OkHttp reports it through the normal failure path.
 */
class NotSignedInException : java.io.IOException("No signed-in user")
