package com.waivera.waivera.feature.auth

import androidx.annotation.StringRes

/** One rule of sprout_api's `PasswordPolicy`, with the checklist line that names it. */
internal enum class PasswordRule(@StringRes val label: Int) {
    Length(R.string.feature_auth_password_rule_length),
    Uppercase(R.string.feature_auth_password_rule_uppercase),
    Lowercase(R.string.feature_auth_password_rule_lowercase),
    Number(R.string.feature_auth_password_rule_number),
    Symbol(R.string.feature_auth_password_rule_symbol),
}

/**
 * Client-side checks that run before anything is sent. The new-password rules mirror
 * sprout_api's `Utilities/PasswordPolicy.cs` (and the web's `passwordPolicy.ts`) exactly, so a
 * password the form accepts is never refused by `change-password`; the server stays the authority.
 */
internal object AuthValidation {
    /** `PasswordPolicy.MinimumLength`. */
    const val MIN_NEW_PASSWORD_LENGTH = 12

    /** `PasswordPolicy.SpecialCharacters`; nothing else counts as a symbol (not `-`, `+` or a space). */
    const val PASSWORD_SYMBOLS = "^$*.[]{}()?\"!@#%&/\\,><':;|_~"

    /** The rules [password] does not meet yet. Letters and digits are ASCII only, like the server's. */
    fun unmetPasswordRules(password: String): Set<PasswordRule> = buildSet {
        if (password.length < MIN_NEW_PASSWORD_LENGTH) add(PasswordRule.Length)
        if (password.none { it in 'A'..'Z' }) add(PasswordRule.Uppercase)
        if (password.none { it in 'a'..'z' }) add(PasswordRule.Lowercase)
        if (password.none { it in '0'..'9' }) add(PasswordRule.Number)
        if (password.none { it in PASSWORD_SYMBOLS }) add(PasswordRule.Symbol)
    }

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
                unmetPasswordRules(new).isNotEmpty() -> R.string.feature_auth_error_new_password_rules
                current.isNotEmpty() && new == current -> R.string.feature_auth_error_new_password_same
                else -> null
            },
            confirm = if (confirm != new) R.string.feature_auth_error_confirm_mismatch else null,
        )
}
