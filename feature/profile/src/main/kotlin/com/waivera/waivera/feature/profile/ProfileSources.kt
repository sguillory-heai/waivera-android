package com.waivera.waivera.feature.profile

import androidx.compose.runtime.Immutable
import com.waivera.waivera.core.data.repository.PeopleRepository
import com.waivera.waivera.core.data.repository.ProfileRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject

/*
 * The screens' view of core data. Both sources delegate to core:data (requested in
 * docs/requests/feature-profile.md); the feature keeps its own small models so screens and
 * their tests do not depend on DTO-shaped types.
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

/** `ProfileRepository.organization()`: `OrganizationDetails` matched by the session's id, never by name. */
internal class RepositoryOrganizationSource @Inject constructor(private val profiles: ProfileRepository) :
    OrganizationSource {
    override suspend fun current(): Result<OrganizationInfo?> = profiles.organization().map { org ->
        org?.let {
            OrganizationInfo(id = it.id, name = it.name, address = it.address, contactNumber = it.contactNumber)
        }
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

/** `PeopleRepository.details()`: `GetUserDetailsByUserID`, with roles and organization. */
internal class RepositoryPersonDetailsSource @Inject constructor(private val people: PeopleRepository) :
    PersonDetailsSource {
    override suspend fun details(userId: String): Result<PersonDetails> = people.details(userId).map { details ->
        PersonDetails(
            displayName = details.name,
            email = details.email,
            phoneNumber = details.phoneNumber,
            roles = details.roles,
            organization = details.organizationName,
        )
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal interface ProfileSourcesModule {
    @Binds
    fun organizationSource(source: RepositoryOrganizationSource): OrganizationSource

    @Binds
    fun personDetailsSource(source: RepositoryPersonDetailsSource): PersonDetailsSource
}
