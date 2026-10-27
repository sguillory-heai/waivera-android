package com.waivera.waivera.feature.auth

import com.waivera.waivera.core.data.model.CurrentUser
import com.waivera.waivera.core.data.model.SessionState
import com.waivera.waivera.core.data.repository.SessionRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Scriptable session for ViewModel tests. Each call is recorded; its result comes from the
 * matching `next*` value, and a `gate` lets a test hold the call in flight.
 */
class FakeSessionRepository(initial: SessionState = SessionState.SignedOut()) : SessionRepository {
    val mutableState = MutableStateFlow(initial)
    override val state: StateFlow<SessionState> = mutableState

    val signIns = mutableListOf<Pair<String, String>>()
    val resets = mutableListOf<String>()
    val passwordChanges = mutableListOf<Pair<String, String>>()
    var refreshes = 0
        private set

    var nextSignIn: Result<Unit> = Result.success(Unit)
    var nextReset: Result<Unit> = Result.success(Unit)
    var nextChange: Result<Unit> = Result.success(Unit)
    var nextRefresh: Result<Unit> = Result.success(Unit)

    /** State published when a call succeeds, mimicking the gate moving on. */
    var stateAfterSignIn: SessionState? = SessionState.SignedIn(USER)
    var stateAfterRefresh: SessionState? = null

    var gate: CompletableDeferred<Unit>? = null

    override suspend fun signIn(email: String, password: String): Result<Unit> {
        signIns += email to password
        gate?.await()
        if (nextSignIn.isSuccess) stateAfterSignIn?.let { mutableState.value = it }
        return nextSignIn
    }

    override suspend fun signOut(): Result<Unit> {
        mutableState.value = SessionState.SignedOut()
        return Result.success(Unit)
    }

    override suspend fun sendPasswordReset(email: String): Result<Unit> {
        resets += email
        gate?.await()
        return nextReset
    }

    override suspend fun refresh(): Result<Unit> {
        refreshes++
        gate?.await()
        if (nextRefresh.isSuccess) stateAfterRefresh?.let { mutableState.value = it }
        return nextRefresh
    }

    override suspend fun changePassword(currentPassword: String, newPassword: String): Result<Unit> {
        passwordChanges += currentPassword to newPassword
        gate?.await()
        return nextChange
    }

    companion object {
        val USER = CurrentUser(userId = "usr_alex_rivera", email = "alex.rivera@example.org")
    }
}
