package com.waivera.waivera.feature.profile

import androidx.compose.runtime.Immutable
import com.waivera.waivera.core.data.model.currentUser
import com.waivera.waivera.core.data.repository.ProfileRepository
import com.waivera.waivera.core.data.repository.SessionRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject

/*
 * Local adapters for data the core layer does not expose yet. Each one is requested in
 * docs/requests/feature-profile.md; when core adds the method, the adapter becomes a one-line
 * delegation (or is deleted) and the screens do not change.
 */

/** The signed-in person's organization, matched by id only (spec 08 §5). */
@Immutable
internal data class OrganizationInfo(
    val id: Int,
    val name: String?,
    val legalName: String? = null,
    val address: String? = null,
    val contactNumber: String? = null,
)

internal fun interface OrganizationSource {
    /** Null when the session's organization cannot be found ("organization unavailable"). */
    suspend fun current(): Result<OrganizationInfo?>
}

/**
 * Until core exposes `OrganizationDetails` matched by id, this uses the id and name the session
 * already carries. A missing id is "unavailable"; there is never a name-based match.
 */
internal class SessionOrganizationSource @Inject constructor(private val session: SessionRepository) :
    OrganizationSource {
    override suspend fun current(): Result<OrganizationInfo?> {
        val user = session.state.value.currentUser
        val id = user?.organizationId?.takeIf { it > 0 }
        return Result.success(id?.let { OrganizationInfo(id = it, name = user.organizationName) })
    }
}

/** What the person detail screen shows below the name. */
@Immutable
internal data class PersonDetails(
    val displayName: String?,
    val email: String?,
    val phoneNumber: String?,
    val roles: List<String>,
    val organization: String?,
)

internal fun interface PersonDetailsSource {
    suspend fun details(userId: String): Result<PersonDetails>
}

/**
 * Until core exposes `GetUserDetailsByUserID` (roles, organization), this reads the employee
 * profile, which carries the name, email, phone and positions.
 */
internal class ProfilePersonDetailsSource @Inject constructor(private val profiles: ProfileRepository) :
    PersonDetailsSource {
    override suspend fun details(userId: String): Result<PersonDetails> = profiles.profile(userId).map { profile ->
        PersonDetails(
            displayName = profile.displayName
                ?: listOfNotNull(profile.firstName, profile.lastName).joinToString(" ").takeIf(String::isNotBlank),
            email = profile.email,
            phoneNumber = profile.phoneNumber,
            roles = profile.positions,
            organization = null,
        )
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal interface ProfileSourcesModule {
    @Binds
    fun organizationSource(source: SessionOrganizationSource): OrganizationSource

    @Binds
    fun personDetailsSource(source: ProfilePersonDetailsSource): PersonDetailsSource
}
