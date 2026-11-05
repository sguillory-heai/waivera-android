@file:UseSerializers(UtcInstantSerializer::class)

package com.waivera.waivera.core.network.dto

import com.waivera.waivera.core.network.json.LenientBooleanSerializer
import com.waivera.waivera.core.network.json.StringOrNumberSerializer
import com.waivera.waivera.core.network.time.LocalDateSerializer
import com.waivera.waivera.core.network.time.UtcInstantSerializer
import java.time.Instant
import java.time.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.json.JsonElement

/** `UserLoginResponseDto`, read from `data` of `GET /api/Authentication/login` (gaps.md G-02). */
@Serializable
data class UserLoginResponseDto(
    @Serializable(with = LenientBooleanSerializer::class) val requiresPasswordChange: Boolean = false,
    @Serializable(with = StringOrNumberSerializer::class) val userId: String? = null,
    @Serializable(with = LenientBooleanSerializer::class) val requiresOnboarding: Boolean = false,
)

/**
 * `dto/Request/ChangePasswordRequest.cs`. [idToken] must come from a sign-in made moments ago:
 * the server checks its `auth_time`, then changes the password in Firebase and stamps
 * `LastChangedPassword` itself.
 */
@Serializable
data class ChangePasswordRequest(val idToken: String, val newPassword: String) {
    override fun toString(): String = "ChangePasswordRequest(redacted)"
}

/** Region entry on [UserWithRoleDto]. */
@Serializable
data class UserRegionDto(val id: Int = 0, val regionName: String? = null)

/** `dto/Response/UserWithRoleDto.cs`. `birthday`/`hireDate` are calendar dates (dates.md §3 caveat). */
@Serializable
data class UserWithRoleDto(
    @Serializable(with = StringOrNumberSerializer::class) val id: String? = null,
    val firstName: String? = null,
    val lastName: String? = null,
    val email: String? = null,
    val displayName: String? = null,
    val avatarURL: String? = null,
    val scId: String? = null,
    val deactivated: Boolean = false,
    val joinDate: Instant? = null,
    @Serializable(with = LocalDateSerializer::class) val hireDate: LocalDate? = null,
    val organizationId: Int = 0,
    val organizationName: String? = null,
    val organization: JsonElement? = null,
    val organizationRole: JsonElement? = null,
    val phoneNumber: String? = null,
    val managerId: String? = null,
    val managerName: String? = null,
    val roles: List<String> = emptyList(),
    val regions: List<UserRegionDto> = emptyList(),
    val teams: JsonElement? = null,
    val waiverFamilyIds: List<Int>? = null,
    val waiverFamilyCode: List<String>? = null,
    val timezone: String? = null,
    val caseloadCapacity: Int? = null,
    val isCaseloadCapacityUnlimited: Boolean = false,
    @Serializable(with = LocalDateSerializer::class) val birthday: LocalDate? = null,
)

/** `dto/Request/UpdateContactAndTimezoneRequest.cs`. */
@Serializable
data class UpdateContactAndTimezoneRequest(val phoneNumber: String? = null, val preferredTimeZone: String? = null)

/** `dto/Response/OrganizationDto.cs` from `OrganizationDetails`. */
@Serializable
data class OrganizationDto(
    val id: Int = 0,
    val name: String? = null,
    val address: String? = null,
    val contactNumber: String? = null,
)

/** `dto/Response/OrganizationResponse.cs` from `GET /api/users/organization` (bare, not enveloped). */
@Serializable
data class OrganizationResponse(
    val id: Int = 0,
    val name: String? = null,
    val abbreviation: String? = null,
    val atRiskWindowDays: Int = 0,
    val defaultPtoDays: Double = 0.0,
    val isSandbox: Boolean = false,
)

/**
 * `GetUserNameResponseVM` from `get-username-list`. That endpoint is not scoped to the caller's
 * organization, so the app does not use it; the directory is [UserInfoResponse].
 */
@Serializable
data class GetUserNameResponseVM(
    @Serializable(with = StringOrNumberSerializer::class) val userId: String? = null,
    val userName: String? = null,
)

/**
 * `GetUserNameResponseDBVM` from `get-combined-user-list`; includes waiver participants, so the app
 * does not use it.
 */
@Serializable
data class GetUserNameResponseDBVM(
    @Serializable(with = StringOrNumberSerializer::class) val userId: String? = null,
    val userName: String? = null,
    val type: String? = null,
)

/** `dto/Request/AuthBootstrapRequestDto.cs`. */
@Serializable
data class AuthBootstrapRequest(val identityTransition: Boolean)

/**
 * `dto/Response/AuthBootstrapResponseDto.cs`, only what gates what the phone offers: roles, the
 * role policies the web checks with `hasPolicyPermissionV2`, and the ABAC actions it checks with
 * `hasPermission`. The server re-checks every action, so these only decide what is shown.
 */
@Serializable
data class AuthBootstrapResponse(
    val roles: AuthBootstrapRoles = AuthBootstrapRoles(),
    val permissions: List<AuthBootstrapAbacPolicy> = emptyList(),
    val rolePolicies: List<OrganizationRolePolicy> = emptyList(),
    val userAddedInRolePolicy: Boolean = true,
)

@Serializable
data class AuthBootstrapRoles(
    val isActiveOrganizationOwner: Boolean = false,
    val employeeRoles: List<AuthBootstrapRole> = emptyList(),
    val organizationRoles: List<AuthBootstrapOrganizationRole> = emptyList(),
)

@Serializable
data class AuthBootstrapRole(val name: String? = null)

@Serializable
data class AuthBootstrapOrganizationRole(val name: String? = null, val systemKey: String? = null)

@Serializable
data class AuthBootstrapAbacPolicy(val action: String? = null, val condition: String? = null)

/** `OrganizationRolePolicyDto`. */
@Serializable
data class OrganizationRolePolicy(
    val policyPermission: PolicyPermissionKey? = null,
    val policyPermissionScope: PolicyPermissionScope? = null,
)

@Serializable
data class PolicyPermissionKey(val permissionKey: String? = null)

@Serializable
data class PolicyPermissionScope(val scope: String? = null)
