package com.waivera.waivera.core.data.live

import com.waivera.waivera.core.data.model.EmployeeFile
import com.waivera.waivera.core.data.model.EmployeeFilePage
import com.waivera.waivera.core.data.model.EmployeeProfile
import com.waivera.waivera.core.data.model.Person
import com.waivera.waivera.core.data.model.Synced
import com.waivera.waivera.core.data.model.TimeOffBalance
import com.waivera.waivera.core.data.model.TimeOffRequest
import com.waivera.waivera.core.data.model.TimeOffSummary
import com.waivera.waivera.core.data.model.currentUserId
import com.waivera.waivera.core.data.repository.ProfileRepository
import com.waivera.waivera.core.data.repository.SessionRepository
import com.waivera.waivera.core.network.api.AuthenticationApi
import com.waivera.waivera.core.network.api.EmployeeApi
import com.waivera.waivera.core.network.api.TimeOffApi
import com.waivera.waivera.core.network.dto.GetEmployeeProfileDto
import com.waivera.waivera.core.network.dto.UpdateContactAndTimezoneRequest
import com.waivera.waivera.core.network.error.WaiveraError
import com.waivera.waivera.core.network.error.apiCall
import com.waivera.waivera.core.network.error.catchingWaivera
import com.waivera.waivera.core.network.error.requireData
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class LiveProfileRepository @Inject constructor(
    private val employeeApi: EmployeeApi,
    private val timeOffApi: TimeOffApi,
    private val authApi: AuthenticationApi,
    private val cache: ChatCache,
    private val sync: SyncTracker,
    private val session: SessionRepository,
) : ProfileRepository {
    override fun observeMyProfile(): Flow<Synced<EmployeeProfile?>> =
        sync.synced(KEY, cache.observeDocument(KEY, EmployeeProfile.serializer()).map { it })

    override suspend fun refresh(): Result<Unit> = sync.track(KEY) {
        val me = session.state.value.currentUserId ?: throw WaiveraError.NotSignedIn()
        cache.putDocument(KEY, EmployeeProfile.serializer(), fetch(me))
    }

    override suspend fun profile(employeeId: String): Result<EmployeeProfile> = catchingWaivera { fetch(employeeId) }

    private suspend fun fetch(employeeId: String): EmployeeProfile =
        map(apiCall { employeeApi.getProfile(employeeId) }.requireData(), employeeId)

    override suspend fun timeOff(userId: String): Result<TimeOffSummary> = catchingWaivera {
        val rows = apiCall { timeOffApi.getTimeOffForProfile(userId) }.requireData()
        TimeOffSummary(
            requests = rows.map {
                TimeOffRequest(
                    it.id, it.timeOffStartDate, it.timeOffEndDate, it.typeOfLeave, it.requestType, it.comment,
                    it.isApproved, it.isRejected, it.isCancellationPending,
                )
            },
            // Balance figures repeat on every row; the first row carries them.
            balance = rows.firstOrNull()?.let {
                TimeOffBalance(
                    it.remainingLeaveDays,
                    it.remainingLeaveHours,
                    it.usedLeaveDays,
                    it.usedLeaveHours,
                    it.allocatedLeaveCount,
                    it.pendingTimeOffCount,
                )
            },
        )
    }

    override suspend fun files(userId: String, page: Int): Result<EmployeeFilePage> = catchingWaivera {
        val dto = apiCall { employeeApi.getEmployeeFiles(userId, page, EmployeeFilePage.PAGE_SIZE) }.requireData()
        EmployeeFilePage(
            items = dto.items.mapNotNull { f ->
                f.id?.let {
                    EmployeeFile(
                        it,
                        f.fileName,
                        f.documentCategoryName,
                        f.dateAdded,
                        f.mimeType,
                        f.size,
                        f.uploadedByName,
                    )
                }
            },
            totalRecords = dto.totalRecords,
            pageNumber = page,
        )
    }

    override suspend fun updateContact(phoneNumber: String?, timeZoneId: String?): Result<Unit> = catchingWaivera {
        val me = session.state.value.currentUserId ?: throw WaiveraError.NotSignedIn()
        apiCall { authApi.updateContactAndTimezone(me, UpdateContactAndTimezoneRequest(phoneNumber, timeZoneId)) }
        // The saved zone drives every schedule; reload the session so it takes effect at once.
        session.refresh()
        refresh()
        Unit
    }

    private fun map(dto: GetEmployeeProfileDto, fallbackId: String) = EmployeeProfile(
        employeeId = dto.employeeId ?: fallbackId,
        firstName = dto.firstName,
        lastName = dto.lastName,
        displayName = dto.displayName,
        email = dto.email,
        avatarUrl = dto.avatarURL,
        phoneNumber = dto.phoneNumber,
        address = dto.address,
        hireDate = dto.hireDate,
        birthday = dto.birthday,
        joinedAt = dto.joinDate,
        positions = dto.positions,
        managers = dto.reporters.mapNotNull { r ->
            r.employeeId?.let { Person(it, displayName = r.name, avatarUrl = r.avatar) }
        },
        regions = dto.regions.mapNotNull { it.name },
        agencies = dto.agencyNames,
        timeZoneId = dto.timezone,
    )

    companion object {
        const val KEY = "profile:me"
    }
}
