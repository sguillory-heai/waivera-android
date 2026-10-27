package com.waivera.waivera.feature.auth

import androidx.annotation.StringRes

/**
 * Client-side checks that run before anything is sent. They only catch obvious mistakes; Firebase
 * and the server stay the authority (a weak password is still rejected by Firebase's policy).
 */
internal object AuthValidation {
    /**
     * New passwords need at least this many characters. Firebase's own floor is 6; 8 matches
     * common organisation policy and is checked again by Firebase's password policy.
     */
    const val MIN_NEW_PASSWORD_LENGTH = 8

    // One @, something on both sides, and a dot in the domain. Deliberately loose: the server
    // decides what a real address is, this only catches typos such as a missing @.
    private val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

    /** The email exactly as it is sent: trimmed, case kept (Firebase compares case-insensitively). */
    fun normalizeEmail(raw: String): String = raw.trim()

    @StringRes
    fun emailError(raw: String): Int? {
        val email = normalizeEmail(raw)
        return when {
            email.isEmpty() -> R.string.feature_auth_error_email_required
            !EMAIL.matches(email) -> R.string.feature_auth_error_email_invalid
            else -> null
        }
    }

    /** The sign-in password is never trimmed: spaces can be part of it. */
    @StringRes
    fun signInPasswordError(password: String): Int? =
        if (password.isEmpty()) R.string.feature_auth_error_password_required else null

    data class PasswordChangeErrors(
        @StringRes val current: Int? = null,
        @StringRes val new: Int? = null,
        @StringRes val confirm: Int? = null,
    ) {
        val isValid: Boolean get() = current == null && new == null && confirm == null
    }

    fun passwordChangeErrors(current: String, new: String, confirm: String): PasswordChangeErrors =
        PasswordChangeErrors(
            current = if (current.isEmpty()) R.string.feature_auth_error_current_password_required else null,
            new = when {
                new.length < MIN_NEW_PASSWORD_LENGTH -> R.string.feature_auth_error_new_password_short
                current.isNotEmpty() && new == current -> R.string.feature_auth_error_new_password_same
                else -> null
            },
            confirm = if (confirm != new) R.string.feature_auth_error_confirm_mismatch else null,
        )
}
