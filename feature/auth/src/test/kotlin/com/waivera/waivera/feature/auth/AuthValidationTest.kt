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
    fun `new password rules`() {
        val ok = AuthValidation.passwordChangeErrors("old one", "a longer one", "a longer one")
        assertTrue(ok.isValid)
        val min = "x".repeat(AuthValidation.MIN_NEW_PASSWORD_LENGTH)
        assertTrue(AuthValidation.passwordChangeErrors("old", min, min).isValid, "exactly the minimum is allowed")
        val short = "x".repeat(AuthValidation.MIN_NEW_PASSWORD_LENGTH - 1)
        assertEquals(
            R.string.feature_auth_error_new_password_short,
            AuthValidation.passwordChangeErrors("old", short, short).new,
        )
        assertEquals(
            R.string.feature_auth_error_current_password_required,
            AuthValidation.passwordChangeErrors("", "a longer one", "a longer one").current,
        )
    }

    @Test
    fun `the primer explains every notification category once, in order`() {
        assertEquals(NotificationCategory.entries.toList(), PrimerKinds.flatMap { it.categories })
        assertEquals(PrimerKinds.size, PrimerKinds.map { it.title }.toSet().size)
        assertEquals(3, PrimerKinds.size, "messages, schedule and digest")
    }
}
