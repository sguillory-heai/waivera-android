package com.waivera.waivera.navtour

import com.waivera.waivera.core.data.model.CurrentUser
import com.waivera.waivera.core.data.model.Message
import com.waivera.waivera.core.data.model.Person
import com.waivera.waivera.core.data.model.SessionState
import com.waivera.waivera.core.data.model.currentUser
import com.waivera.waivera.core.data.repository.CalendarRepository
import com.waivera.waivera.core.data.repository.SessionRepository
import com.waivera.waivera.core.demo.DemoBackend
import com.waivera.waivera.core.demo.DemoCalendarRepository
import com.waivera.waivera.core.demo.DemoData
import com.waivera.waivera.core.demo.DemoSessionRepository
import com.waivera.waivera.core.network.error.WaiveraError
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The app's clock for the tour. It starts at the demo world's "now" (Sunday 1 November 2026,
 * 10:30 in Chicago) so Today, the schedule and relative times are the same on every run, and a
 * test can move it (for example into a meeting's join window).
 */
@Singleton
class TourClock @Inject constructor() : Clock() {
    private val now = AtomicReference(DemoData.NOW)

    fun set(instant: Instant) = now.set(instant)

    override fun instant(): Instant = now.get()

    override fun millis(): Long = instant().toEpochMilli()

    override fun getZone(): ZoneId = ZoneOffset.UTC

    override fun withZone(zone: ZoneId): Clock = Clock.fixed(instant(), zone)
}

/**
 * The demo session with the gates a test needs. It behaves like `DemoSessionRepository` (any email
 * with the password "demo" signs in, sign-out works) and can also be put in any [SessionState]
 * the gate knows, so the password-change, bootstrap, onboarding and configuration gates are
 * reachable without a server. Like the live repository, sign-in runs the bootstrap through
 * [SessionState.Initializing].
 */
@Singleton
class TourSessionRepository @Inject constructor(private val backend: DemoBackend) : SessionRepository {
    private val mutableState = MutableStateFlow<SessionState>(SessionState.SignedIn(DemoData.currentUser()))
    override val state: StateFlow<SessionState> = mutableState.asStateFlow()

    private val refreshFailures = ArrayDeque<WaiveraError>()
    private var signInHold: CompletableDeferred<Unit>? = null

    /** Puts the session in [next], as if Firebase and the bootstrap had just reported it. */
    fun force(next: SessionState) {
        backend.user.value = next.currentUser
        mutableState.value = next
    }

    /** The next [refresh] fails with [error]; calls after it succeed. */
    fun failNextRefresh(error: WaiveraError) {
        refreshFailures.addLast(error)
    }

    /** The next successful sign-in waits in [SessionState.Initializing] until the result completes. */
    fun holdNextSignIn(): CompletableDeferred<Unit> = CompletableDeferred<Unit>().also { signInHold = it }

    override suspend fun signIn(email: String, password: String): Result<Unit> {
        if (password != DemoSessionRepository.DEMO_PASSWORD) return Result.failure(WaiveraError.InvalidCredentials())
        mutableState.value = SessionState.Initializing
        signInHold?.let { hold ->
            signInHold = null
            hold.await()
        }
        signedIn(DemoData.currentUser())
        return Result.success(Unit)
    }

    override suspend fun signOut(): Result<Unit> {
        backend.user.value = null
        mutableState.value = SessionState.SignedOut(byUser = true)
        return Result.success(Unit)
    }

    override suspend fun sendPasswordReset(email: String): Result<Unit> = Result.success(Unit)

    /** Bootstrap retry and the onboarding re-check both end signed in, unless a failure is queued. */
    override suspend fun refresh(): Result<Unit> {
        refreshFailures.removeFirstOrNull()?.let { return Result.failure(it) }
        when (val current = mutableState.value) {
            is SessionState.BootstrapFailed -> signedIn(DemoData.currentUser())
            is SessionState.SignedIn ->
                if (current.user.requiresOnboarding) signedIn(current.user.copy(requiresOnboarding = false))
            else -> Unit
        }
        return Result.success(Unit)
    }

    /** A required change unlocks the app; a voluntary one keeps the person signed in. */
    override suspend fun changePassword(currentPassword: String, newPassword: String): Result<Unit> {
        val current = mutableState.value
        if (current is SessionState.PasswordChangeRequired) signedIn(current.user)
        return Result.success(Unit)
    }

    private fun signedIn(user: CurrentUser) {
        backend.user.value = user
        mutableState.value = SessionState.SignedIn(user)
    }
}

/**
 * The demo calendar, except that Sprout meetings get a web lobby URL (the demo has no web base
 * URL), so the join hand-off to the browser can be followed.
 */
@Singleton
class TourCalendarRepository @Inject constructor(private val demo: DemoCalendarRepository) :
    CalendarRepository by demo {
    override suspend fun meetingLobbyUrl(eventId: String): Result<String> = Result.success(lobbyUrlFor(eventId))

    companion object {
        /** Test data only: the reserved `.test` domain never resolves. */
        fun lobbyUrlFor(eventId: String): String = "https://app.example.test/meet/$eventId"
    }
}

/** Extra demo data a path needs, added before launch() so the first frame already shows it. */
object TourSeeds {
    /** How long before the tour's "now" Priya's question arrives. */
    private const val PRIYA_ASKS_SECONDS_AGO = 60L

    /**
     * An unread question from Priya in the DM, newer than my own last message there, so the DM
     * is waiting on me and Today lists it under Needs you (the shared rule in Conversation.isUnreadFor).
     */
    fun priyaAsks(backend: DemoBackend) {
        backend.append(
            Message(
                id = backend.nextMessageId(),
                conversationId = DemoData.DM_PRIYA,
                author = Person(DemoData.PRIYA, "Priya", "Raman", "Priya Raman", "priya.raman@example.com"),
                authorId = DemoData.PRIYA,
                text = PRIYA_ASKS_TEXT,
                createdAt = DemoData.NOW.minusSeconds(PRIYA_ASKS_SECONDS_AGO),
            ),
        )
    }

    const val PRIYA_ASKS_TEXT = "Are you still OK for the 2 PM visit?"
}
