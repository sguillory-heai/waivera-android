package com.waivera.waivera.core.demo

import com.waivera.waivera.core.data.calendar.CalendarMath
import com.waivera.waivera.core.data.model.ActivityFeed
import com.waivera.waivera.core.data.model.AppSettings
import com.waivera.waivera.core.data.model.DateRange
import com.waivera.waivera.core.data.model.EmployeeFilePage
import com.waivera.waivera.core.data.model.EmployeeProfile
import com.waivera.waivera.core.data.model.Milestone
import com.waivera.waivera.core.data.model.MyInvitation
import com.waivera.waivera.core.data.model.NotificationCategory
import com.waivera.waivera.core.data.model.NotificationPreferences
import com.waivera.waivera.core.data.model.Organization
import com.waivera.waivera.core.data.model.Person
import com.waivera.waivera.core.data.model.PersonDetails
import com.waivera.waivera.core.data.model.RealtimeStatus
import com.waivera.waivera.core.data.model.ScheduleEvent
import com.waivera.waivera.core.data.model.ScheduleFeed
import com.waivera.waivera.core.data.model.SessionState
import com.waivera.waivera.core.data.model.Synced
import com.waivera.waivera.core.data.model.ThemePreference
import com.waivera.waivera.core.data.model.TimeOffSummary
import com.waivera.waivera.core.data.repository.ActivityRepository
import com.waivera.waivera.core.data.repository.CalendarRepository
import com.waivera.waivera.core.data.repository.PeopleRepository
import com.waivera.waivera.core.data.repository.ProfileRepository
import com.waivera.waivera.core.data.repository.PushRegistrationRepository
import com.waivera.waivera.core.data.repository.RealtimeStatusRepository
import com.waivera.waivera.core.data.repository.SessionRepository
import com.waivera.waivera.core.data.repository.SettingsRepository
import com.waivera.waivera.core.network.di.ApplicationScope
import com.waivera.waivera.core.network.error.WaiveraError
import com.waivera.waivera.core.realtime.ConnectionState
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/** Signed in as Alex Rivera. Any email with the password "demo" signs back in after sign-out. */
@Singleton
class DemoSessionRepository @Inject constructor(private val backend: DemoBackend) : SessionRepository {
    private val _state = MutableStateFlow<SessionState>(SessionState.SignedIn(DemoData.currentUser()))
    override val state: StateFlow<SessionState> = _state.asStateFlow()

    override suspend fun signIn(email: String, password: String): Result<Unit> {
        if (password != DEMO_PASSWORD) return Result.failure(WaiveraError.InvalidCredentials())
        val user = DemoData.currentUser()
        backend.user.value = user
        _state.value = SessionState.SignedIn(user)
        return Result.success(Unit)
    }

    override suspend fun signOut(): Result<Unit> {
        backend.user.value = null
        _state.value = SessionState.SignedOut()
        return Result.success(Unit)
    }

    override suspend fun sendPasswordReset(email: String): Result<Unit> = Result.success(Unit)

    override suspend fun refresh(): Result<Unit> = backend.refresh()

    override suspend fun changePassword(currentPassword: String, newPassword: String): Result<Unit> =
        Result.success(Unit)

    companion object {
        const val DEMO_PASSWORD = "demo"
    }
}

@Singleton
class DemoActivityRepository @Inject constructor(private val backend: DemoBackend) : ActivityRepository {
    override fun observeActivity(): Flow<Synced<ActivityFeed>> = backend.synced(
        backend.activity.map { items ->
            ActivityFeed(items.sortedByDescending { it.createdAt }, hasMore = false)
        },
    )

    override suspend fun refresh(): Result<Unit> = backend.refresh()

    override suspend fun loadMore(): Result<Boolean> = Result.success(false)

    override suspend fun markRead(activityId: Long): Result<Unit> {
        backend.activity.update { list -> list.map { if (it.id == activityId) it.copy(read = true) else it } }
        return Result.success(Unit)
    }

    override suspend fun markAllRead(): Result<Unit> {
        backend.activity.update { list -> list.map { it.copy(read = true) } }
        return Result.success(Unit)
    }
}

@Singleton
class DemoPeopleRepository @Inject constructor(private val backend: DemoBackend) : PeopleRepository {
    private val avatar by lazy { DemoData.avatarBytes() }

    override fun observeDirectory(): Flow<Synced<List<Person>>> = backend.synced(backend.people)

    override fun observePerson(userId: String): Flow<Person?> = backend.people.map { list ->
        list.firstOrNull {
            it.id ==
                userId
        }
    }

    override suspend fun refresh(): Result<Unit> = backend.refresh()

    /** Only Priya has a photo, so initials fallbacks are visible too. */
    override suspend fun avatar(userId: String): ByteArray? = avatar.takeIf { userId == DemoData.PRIYA }

    override suspend fun details(userId: String): Result<PersonDetails> {
        val person = backend.people.value.firstOrNull { it.id == userId }
            ?: return Result.failure(WaiveraError.NotFound())
        return Result.success(
            PersonDetails(
                userId = person.id,
                firstName = person.firstName,
                lastName = person.lastName,
                displayName = person.displayName,
                email = person.email,
                roles = listOf("Direct Support Professional"),
                organizationName = DemoData.currentUser().organizationName,
            ),
        )
    }
}

@Singleton
class DemoCalendarRepository @Inject constructor(private val backend: DemoBackend) : CalendarRepository {
    override val zone: Flow<ZoneId> = MutableStateFlow(DemoData.ZONE)

    override fun observeRange(range: DateRange): Flow<Synced<ScheduleFeed>> = backend.synced(
        backend.events.map { events ->
            ScheduleFeed(
                range = range,
                zoneId = DemoData.ZONE.id,
                events = events.filter { e -> range.days().any { CalendarMath.occursOn(e, it, DemoData.ZONE) } },
                timeOff = DemoData.timeOff().filter { t ->
                    range.days().any { CalendarMath.occursOn(t, it, DemoData.ZONE) }
                },
                holidays = DemoData.holidays().filter { h -> range.days().any { CalendarMath.occursOn(h, it) } },
            )
        },
    )

    override suspend fun refresh(range: DateRange): Result<Unit> = backend.refresh()

    override fun observeMilestones(): Flow<Synced<List<Milestone>>> = backend.synced(backend.milestones)

    override suspend fun refreshMilestones(): Result<Unit> = backend.refresh()

    override fun observeEvent(eventId: String): Flow<Synced<ScheduleEvent?>> =
        backend.synced(backend.events.map { events -> events.firstOrNull { it.id == eventId } })

    override suspend fun refreshEvent(eventId: String): Result<Unit> = backend.refresh()

    override suspend fun respondToInvitation(eventId: String, accept: Boolean): Result<Unit> {
        backend.events.update { list ->
            list.map {
                if (it.id ==
                    eventId
                ) {
                    it.copy(myInvitation = if (accept) MyInvitation.ACCEPTED else MyInvitation.DECLINED)
                } else {
                    it
                }
            }
        }
        return Result.success(Unit)
    }

    override suspend fun meetingLobbyUrl(eventId: String): Result<String> =
        Result.failure(WaiveraError.Configuration(listOf("waivera.webBaseUrl")))
}

@Singleton
class DemoProfileRepository @Inject constructor(private val backend: DemoBackend) : ProfileRepository {
    private val profile = MutableStateFlow<EmployeeProfile?>(DemoData.profile())

    override fun observeMyProfile(): Flow<Synced<EmployeeProfile?>> = backend.synced(profile)

    override suspend fun refresh(): Result<Unit> = backend.refresh()

    @Suppress("ReturnCount")
    override suspend fun profile(employeeId: String): Result<EmployeeProfile> {
        val mine = profile.value
        if (mine != null && employeeId == mine.employeeId) return Result.success(mine)
        val person =
            backend.people.value.firstOrNull { it.id == employeeId } ?: return Result.failure(WaiveraError.NotFound())
        return Result.success(
            EmployeeProfile(
                person.id,
                person.firstName,
                person.lastName,
                person.displayName,
                person.email,
                timeZoneId = DemoData.ZONE.id,
            ),
        )
    }

    override suspend fun timeOff(userId: String): Result<TimeOffSummary> = Result.success(DemoData.timeOffSummary())

    override suspend fun files(userId: String, page: Int): Result<EmployeeFilePage> {
        val all = DemoData.files()
        return Result.success(EmployeeFilePage(all, all.size, page))
    }

    override suspend fun organization(): Result<Organization?> {
        val user = DemoData.currentUser()
        val id = user.organizationId.takeIf { it > 0 } ?: return Result.success(null)
        return Result.success(
            Organization(id, user.organizationName, "900 Example Ave, Springfield", "+1-555-0190"),
        )
    }

    override suspend fun updateContact(phoneNumber: String?, timeZoneId: String?): Result<Unit> {
        profile.update {
            it?.copy(phoneNumber = phoneNumber ?: it.phoneNumber, timeZoneId = timeZoneId ?: it.timeZoneId)
        }
        return Result.success(Unit)
    }
}

@Singleton
class DemoSettingsRepository @Inject constructor(private val backend: DemoBackend) : SettingsRepository {
    override val settings: Flow<AppSettings> = backend.settings

    override suspend fun setTheme(theme: ThemePreference) = backend.settings.update { it.copy(theme = theme) }

    override suspend fun setHapticsEnabled(enabled: Boolean) = backend.settings.update {
        it.copy(hapticsEnabled = enabled)
    }

    override suspend fun setNotificationsEnabled(enabled: Boolean) = notifications { it.copy(enabled = enabled) }

    override suspend fun setCategoryEnabled(category: NotificationCategory, enabled: Boolean) =
        notifications { it.copy(categories = it.categories + (category to enabled)) }

    override suspend fun setSound(enabled: Boolean) = notifications { it.copy(sound = enabled) }

    override suspend fun setVibration(enabled: Boolean) = notifications { it.copy(vibration = enabled) }

    override suspend fun setPreviewContent(enabled: Boolean) = notifications { it.copy(previewContent = enabled) }

    private fun notifications(transform: (NotificationPreferences) -> NotificationPreferences) =
        backend.settings.update { it.copy(notifications = transform(it.notifications)) }
}

/** Records registrations instead of calling the server. */
@Singleton
class DemoPushRegistrationRepository @Inject constructor() : PushRegistrationRepository {
    val registrations = mutableListOf<Pair<String, NotificationPreferences>>()
    var unregistered = 0
        private set

    override suspend fun register(token: String, preferences: NotificationPreferences): Result<Unit> {
        registrations += token to preferences
        return Result.success(Unit)
    }

    override suspend fun unregister(): Result<Unit> {
        unregistered++
        return Result.success(Unit)
    }

    override suspend fun onNewToken(token: String) = Unit
}

@Singleton
class DemoRealtimeStatusRepository @Inject constructor(
    private val backend: DemoBackend,
    private val realtime: DemoRealtimeClient,
    @ApplicationScope scope: CoroutineScope,
) : RealtimeStatusRepository {
    override val status: StateFlow<RealtimeStatus> = realtime.state
        .map { RealtimeStatus(it, isOnline = true) }
        .stateIn(scope, SharingStarted.Eagerly, RealtimeStatus(realtime.state.value, isOnline = true))

    override fun retryNow() = realtime.retryNow()

    override fun setVisibleConversation(conversationId: Long?) {
        backend.visibleConversation.value = conversationId
    }

    override fun shouldSuppressNotification(conversationId: Long): Boolean =
        realtime.state.value == ConnectionState.Connected && backend.visibleConversation.value == conversationId
}
