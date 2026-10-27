package com.waivera.waivera.core.data.model

import com.waivera.waivera.core.network.error.WaiveraError
import java.time.ZoneId
import kotlinx.serialization.Serializable

/** The signed-in employee. [userId] is the backend id and the only "is this me" key. */
@Serializable
data class CurrentUser(
    val userId: String,
    val email: String? = null,
    val firstName: String? = null,
    val lastName: String? = null,
    val displayName: String? = null,
    val avatarUrl: String? = null,
    /** Raw saved IANA zone; use [zone]. */
    val timeZoneId: String? = null,
    val organizationId: Int = 0,
    val organizationName: String? = null,
    val roles: List<String> = emptyList(),
    val phoneNumber: String? = null,
    val requiresOnboarding: Boolean = false,
) {
    /** The employee's saved zone, or the organisation default (dates.md §2). Never the device zone. */
    val zone: ZoneId get() = com.waivera.waivera.core.network.time.WaiveraTime.employeeZone(timeZoneId)
}

sealed interface SessionState {
    /** Firebase is restoring its persisted user, or the backend bootstrap is running. */
    data object Initializing : SessionState

    /** [reason] explains why a session ended (for example the account is not provisioned). */
    data class SignedOut(val reason: WaiveraError? = null) : SessionState

    /** Firebase values are missing from the build; the app should offer demo mode. */
    data class ConfigurationMissing(val missingKeys: List<String>) : SessionState

    /** Firebase says signed in, but the backend bootstrap failed and there is no cached session. */
    data class BootstrapFailed(val error: WaiveraError) : SessionState

    /** Signed in but the backend requires a new password before anything else. */
    data class PasswordChangeRequired(val user: CurrentUser) : SessionState

    /** [isOffline] is true when the session was restored from cache because the network failed. */
    data class SignedIn(val user: CurrentUser, val isOffline: Boolean = false) : SessionState
}

val SessionState.currentUserId: String?
    get() = when (this) {
        is SessionState.SignedIn -> user.userId
        is SessionState.PasswordChangeRequired -> user.userId
        else -> null
    }

val SessionState.currentUser: CurrentUser?
    get() = when (this) {
        is SessionState.SignedIn -> user
        is SessionState.PasswordChangeRequired -> user
        else -> null
    }
