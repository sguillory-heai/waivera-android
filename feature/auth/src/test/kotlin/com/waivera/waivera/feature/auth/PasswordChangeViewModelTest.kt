package com.waivera.waivera.feature.auth

import app.cash.turbine.test
import com.waivera.waivera.core.data.model.SessionState
import com.waivera.waivera.core.network.R as NetworkR
import com.waivera.waivera.core.network.error.WaiveraError
import com.waivera.waivera.core.testing.MainDispatcherRule
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class PasswordChangeViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val session = FakeSessionRepository(SessionState.PasswordChangeRequired(FakeSessionRepository.USER))

    private fun PasswordChangeViewModel.fill(
        current: String = "old password",
        new: String = "a much longer phrase",
        confirm: String = new,
    ) {
        onCurrentChange(current)
        onNewChange(new)
        onConfirmChange(confirm)
    }

    @Test
    fun `empty - nothing to submit until every field has text`() {
        val vm = PasswordChangeViewModel(session)
        assertFalse(vm.state.value.canSubmit)
        vm.onCurrentChange("old")
        vm.onNewChange("new")
        assertFalse(vm.state.value.canSubmit)
        vm.onConfirmChange("new")
        assertTrue(vm.state.value.canSubmit)
    }

    @Test
    fun `validation blocks short, unchanged and mismatched passwords`() = runTest {
        val vm = PasswordChangeViewModel(session)
        vm.effects.test {
            vm.fill(current = "same-password", new = "short", confirm = "other")
            vm.submit()
            assertEquals(AuthEffect.Error, awaitItem())
        }
        assertEquals(R.string.feature_auth_error_new_password_short, vm.state.value.newError)
        assertEquals(R.string.feature_auth_error_confirm_mismatch, vm.state.value.confirmError)
        vm.fill(current = "same-password", new = "same-password")
        vm.submit()
        assertEquals(R.string.feature_auth_error_new_password_same, vm.state.value.newError)
        assertTrue(session.passwordChanges.isEmpty())
    }

    @Test
    fun `loading - saving locks the form and ignores a second tap`() {
        val gate = CompletableDeferred<Unit>()
        session.gate = gate
        val vm = PasswordChangeViewModel(session)
        vm.fill()
        vm.submit()
        assertEquals(PasswordChangePhase.Saving, vm.state.value.phase)
        assertFalse(vm.state.value.fieldsEnabled)
        vm.submit()
        assertEquals(1, session.passwordChanges.size)
        gate.complete(Unit)
        assertEquals(PasswordChangePhase.Done, vm.state.value.phase)
    }

    @Test
    fun `success sends both passwords once and wipes them from state`() = runTest {
        val vm = PasswordChangeViewModel(session)
        vm.effects.test {
            vm.fill()
            vm.submit()
            assertEquals(AuthEffect.Success, awaitItem())
        }
        assertEquals(listOf("old password" to "a much longer phrase"), session.passwordChanges)
        val state = vm.state.value
        assertEquals("", state.current)
        assertEquals("", state.new)
        assertEquals("", state.confirm)
    }

    @Test
    fun `wrong current password points at that field and clears it`() {
        session.nextChange = Result.failure(WaiveraError.InvalidCredentials())
        val vm = PasswordChangeViewModel(session)
        vm.fill()
        vm.submit()
        val state = vm.state.value
        assertEquals(R.string.feature_auth_error_current_password_wrong, state.currentError)
        assertEquals("", state.current)
        assertEquals("a much longer phrase", state.new)
        assertNull(state.errorMessage)
        assertEquals(PasswordChangePhase.Editing, state.phase)
    }

    @Test
    fun `weak password from Firebase shows under the new password`() {
        session.nextChange = Result.failure(WaiveraError.WeakPassword())
        val vm = PasswordChangeViewModel(session)
        vm.fill()
        vm.submit()
        assertEquals(NetworkR.string.core_network_error_weak_password, vm.state.value.newError)
    }

    @Test
    fun `error - server failure shows a banner, never server text`() {
        session.nextChange = Result.failure(RuntimeException("An error occurred during login."))
        val vm = PasswordChangeViewModel(session)
        vm.fill()
        vm.submit()
        assertEquals(NetworkR.string.core_network_error_unexpected, vm.state.value.errorMessage)
        vm.dismissMessage()
        assertNull(vm.state.value.errorMessage)
    }

    @Test
    fun `reconnect - an offline failure keeps the form so the same tap works once online`() {
        session.nextChange = Result.failure(WaiveraError.Network())
        val vm = PasswordChangeViewModel(session)
        vm.fill()
        vm.submit()
        assertEquals(NetworkR.string.core_network_error_network, vm.state.value.errorMessage)
        assertTrue(vm.state.value.canSubmit)
        session.nextChange = Result.success(Unit)
        vm.submit()
        assertEquals(PasswordChangePhase.Done, vm.state.value.phase)
        assertEquals(2, session.passwordChanges.size)
    }

    @Test
    fun `state toString never prints a password`() {
        val vm = PasswordChangeViewModel(session)
        vm.fill(current = "old-secret-1", new = "new-secret-22")
        val text = vm.state.value.toString()
        assertFalse(text.contains("old-secret-1"))
        assertFalse(text.contains("new-secret-22"))
    }
}

class PasswordChangeSignOutTest {
    @get:Rule
    val main = MainDispatcherRule()

    @Test
    fun `signing out from the gate forgets what was typed`() {
        val session = FakeSessionRepository(SessionState.PasswordChangeRequired(FakeSessionRepository.USER))
        val vm = PasswordChangeViewModel(session)
        vm.onCurrentChange("old password")
        vm.onNewChange("half typed")
        session.mutableState.value = SessionState.SignedOut()
        assertEquals(PasswordChangeUiState(), vm.state.value)
    }
}
