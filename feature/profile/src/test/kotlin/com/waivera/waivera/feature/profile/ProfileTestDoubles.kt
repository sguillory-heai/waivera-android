package com.waivera.waivera.feature.profile

import com.waivera.waivera.core.data.model.EmployeeFile
import com.waivera.waivera.core.data.model.EmployeeFilePage
import com.waivera.waivera.core.data.model.EmployeeProfile
import com.waivera.waivera.core.data.model.Organization
import com.waivera.waivera.core.data.model.RealtimeStatus
import com.waivera.waivera.core.data.model.SyncStatus
import com.waivera.waivera.core.data.model.Synced
import com.waivera.waivera.core.data.model.TimeOffSummary
import com.waivera.waivera.core.data.repository.ProfileRepository
import com.waivera.waivera.core.data.repository.RealtimeStatusRepository
import com.waivera.waivera.core.demo.DemoData
import com.waivera.waivera.core.network.error.WaiveraError
import com.waivera.waivera.core.realtime.ConnectionState
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** The demo backend's fixed "now", so celebrations and relative times are stable. */
internal val TestClock: Clock = Clock.fixed(DemoData.NOW, ZoneOffset.UTC)

/** Realtime status the test drives by hand. */
internal class FakeRealtimeStatus(initial: ConnectionState = ConnectionState.Connected, online: Boolean = true) :
    RealtimeStatusRepository {
    val mutable = MutableStateFlow(RealtimeStatus(initial, online))
    var retries = 0
        private set

    override val status: StateFlow<RealtimeStatus> = mutable

    fun set(state: ConnectionState, online: Boolean = true) {
        mutable.value = RealtimeStatus(state, online)
    }

    override fun retryNow() {
        retries++
    }

    override fun setVisibleConversation(conversationId: Long?, threadRootMessageId: Long?) = Unit

    override fun shouldSuppressNotification(conversationId: Long): Boolean = false
}

/**
 * Profile repository with counters and optional gates, so tests can hold a request open
 * (loading) or fail it (error) and count re-issues (retry, refresh, reconnect).
 */
internal class FakeProfileRepository(
    profile: EmployeeProfile? = DemoData.profile(),
    var timeOffResult: Result<TimeOffSummary> = Result.success(DemoData.timeOffSummary()),
    var filesResult: Result<List<EmployeeFile>> = Result.success(DemoData.files()),
) : ProfileRepository {
    private val stored = MutableStateFlow(
        Synced(profile, SyncStatus.Idle, if (profile == null) null else Instant.EPOCH),
    )
    var refreshError: WaiveraError? = null
    var nextProfile: EmployeeProfile? = profile
    var gate: CompletableDeferred<Unit>? = null
    var refreshCalls = 0
    var timeOffCalls = 0
    var fileCalls = 0

    override fun observeMyProfile(): Flow<Synced<EmployeeProfile?>> = stored

    override suspend fun refresh(): Result<Unit> {
        refreshCalls++
        gate?.await()
        val error = refreshError
        if (error != null) {
            stored.value = stored.value.copy(status = SyncStatus.Failed(error))
            return Result.failure(error)
        }
        stored.value = Synced(nextProfile, SyncStatus.Idle, Instant.EPOCH)
        return Result.success(Unit)
    }

    override suspend fun profile(employeeId: String): Result<EmployeeProfile> =
        nextProfile?.let { Result.success(it) } ?: Result.failure(WaiveraError.NotFound())

    override suspend fun timeOff(userId: String): Result<TimeOffSummary> {
        timeOffCalls++
        gate?.await()
        return timeOffResult
    }

    override suspend fun files(userId: String, page: Int): Result<EmployeeFilePage> {
        fileCalls++
        gate?.await()
        return filesResult.map { EmployeeFilePage(it, it.size, page) }
    }

    override suspend fun updateContact(phoneNumber: String?, timeZoneId: String?): Result<Unit> = Result.success(Unit)

    var organizationResult: Result<Organization?> = Result.success(Organization(1, "Waivera Care"))

    override suspend fun organization(): Result<Organization?> = organizationResult
}

/** Organization source with a counter. */
internal class FakeOrganizationSource(
    var result: Result<OrganizationInfo?> = Result.success(OrganizationInfo(1, "Waivera Care")),
) : OrganizationSource {
    var calls = 0

    override suspend fun current(): Result<OrganizationInfo?> {
        calls++
        return result
    }
}

internal fun file(id: Int) = EmployeeFile(id = "file-$id", fileName = "Document $id.pdf", addedAt = DemoData.NOW)
