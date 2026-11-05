package com.waivera.waivera.feature.auth

import com.waivera.waivera.core.data.model.NotificationCategory
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

class AuthValidationTest {
    @Test
    fun `emails are trimmed and loosely checked`() {
        assertNull(AuthValidation.emailError(" alex.rivera@example.org "))
        assertNull(AuthValidation.emailError("A.Rivera+shift@sub.example.co"))
        assertEquals(R.string.feature_auth_error_email_required, AuthValidation.emailError("   "))
        listOf("alex", "alex@", "@example.org", "alex@example", "alex rivera@example.org").forEach {
            assertEquals(R.string.feature_auth_error_email_invalid, AuthValidation.emailError(it), it)
        }
        assertEquals("alex@example.org", AuthValidation.normalizeEmail("  alex@example.org\n"))
    }

    @Test
    fun `sign-in password only has to be present, spaces count`() {
        assertEquals(R.string.feature_auth_error_password_required, AuthValidation.signInPasswordError(""))
        assertNull(AuthValidation.signInPasswordError(" "))
    }

    @Test
    fun `new password rules mirror the server's PasswordPolicy`() {
        val ok = AuthValidation.passwordChangeErrors("old one", "Morning_walk42", "Morning_walk42")
        assertTrue(ok.isValid)
        assertEquals(12, AuthValidation.MIN_NEW_PASSWORD_LENGTH, "PasswordPolicy.MinimumLength")
        assertTrue(AuthValidation.unmetPasswordRules("Abcdefghi1!x").isEmpty(), "exactly the minimum is allowed")
        assertEquals(setOf(PasswordRule.Length), AuthValidation.unmetPasswordRules("Abcdefgh1!x"))
        assertEquals(setOf(PasswordRule.Uppercase), AuthValidation.unmetPasswordRules("abcdefghi1!x"))
        assertEquals(setOf(PasswordRule.Lowercase), AuthValidation.unmetPasswordRules("ABCDEFGHI1!X"))
        assertEquals(setOf(PasswordRule.Number), AuthValidation.unmetPasswordRules("Abcdefghij!x"))
        assertEquals(setOf(PasswordRule.Symbol), AuthValidation.unmetPasswordRules("Abcdefghij1x"))
        assertEquals(PasswordRule.entries.toSet(), AuthValidation.unmetPasswordRules(""))
        assertEquals(
            R.string.feature_auth_error_new_password_rules,
            AuthValidation.passwordChangeErrors("old", "short", "short").new,
        )
        assertEquals(
            R.string.feature_auth_error_current_password_required,
            AuthValidation.passwordChangeErrors("", "Morning_walk42", "Morning_walk42").current,
        )
    }

    @Test
    fun `only the server's symbols and ASCII letters and digits count`() {
        "^$*.[]{}()?\"!@#%&/\\,><':;|_~".forEach { symbol ->
            assertTrue(PasswordRule.Symbol !in AuthValidation.unmetPasswordRules("Abcdefghij1$symbol"), "$symbol")
        }
        listOf('-', '+', '=', ' ', '`', '€').forEach { other ->
            assertTrue(PasswordRule.Symbol in AuthValidation.unmetPasswordRules("Abcdefghij1$other"), "$other")
        }
        assertTrue(PasswordRule.Uppercase in AuthValidation.unmetPasswordRules("Ébcdefghij1!"), "accented capitals")
        assertTrue(PasswordRule.Number in AuthValidation.unmetPasswordRules("Abcdefghij٣!"), "non-ASCII digits")
    }

    @Test
    fun `the primer explains every notification category once, in order`() {
        assertEquals(NotificationCategory.entries.toList(), PrimerKinds.flatMap { it.categories })
        assertEquals(PrimerKinds.size, PrimerKinds.map { it.title }.toSet().size)
        assertEquals(3, PrimerKinds.size, "messages, schedule and digest")
    }
}
