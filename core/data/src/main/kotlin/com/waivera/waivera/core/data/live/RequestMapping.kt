package com.waivera.waivera.core.data.live

import com.waivera.waivera.core.data.model.ChangeOptions
import com.waivera.waivera.core.data.model.ChangeReason
import com.waivera.waivera.core.data.model.ChangeRequest
import com.waivera.waivera.core.data.model.DelayPeriod
import com.waivera.waivera.core.data.model.RequestAccess
import com.waivera.waivera.core.data.model.RequestKind
import com.waivera.waivera.core.data.model.RequestStatus
import com.waivera.waivera.core.data.model.TaskFile
import com.waivera.waivera.core.network.dto.AdminRequestResponse
import com.waivera.waivera.core.network.dto.AuthBootstrapResponse
import com.waivera.waivera.core.network.dto.ComplianceEligibilityResponse
import com.waivera.waivera.core.network.dto.ComplianceReasonResponse
import com.waivera.waivera.core.network.dto.TaskAttachmentResponse
import com.waivera.waivera.core.network.dto.TransactionReasonResponse
import com.waivera.waivera.core.network.time.WaiveraTime

/**
 * Turns the server's request shapes into the phone's, and decides what to offer with the same
 * rules the web uses (sprout_web `hasPolicyPermissionV2`, `doesUserHavePermission`, the Admin
 * Requests sidebar rule and the task list's pending-approvals rule).
 */
object RequestMapping {
    const val CREATE_EXTENSION = "admin_request:create:task_extension"
    const val CREATE_DELETION = "admin_request:create:task_deletion"
    private const val EXTERNAL_TRAINING_VIEW = "admin_request:view:external_training"
    private const val EXTERNAL_TRAINING_APPROVE = "admin_request:approve:external_training"
    private const val OFFICE_MANAGER = "Office Manager"

    /** The web's role order for the task list's pending-approvals default. */
    private val RankedRoles = listOf("ADMIN", "OWNER", "PROGRAM MANAGER", "SUPERVISOR", "SC", "ASSESSOR")

    fun request(dto: AdminRequestResponse): ChangeRequest {
        val kind = RequestKind.of(dto.transactionType)
        return ChangeRequest(
            id = dto.id,
            kind = kind,
            typeName = dto.transactionType?.trim().orEmpty(),
            status = RequestStatus.of(dto.status),
            requesterId = dto.requesterId,
            requesterName = dto.requesterName?.trim()?.takeIf(String::isNotEmpty).orEmpty(),
            approverName = dto.approverName?.trim()?.takeIf(String::isNotEmpty),
            requestedAt = WaiveraTime.parseInstant(dto.requestTime),
            decidedAt = WaiveraTime.parseInstant(dto.completionTime),
            taskId = dto.taskId,
            taskTitle = dto.taskTitle?.trim()?.takeIf(String::isNotEmpty),
            // A closure keeps its picked reason (or comment) in the reason text; the other kinds
            // keep the picked code name apart and the requester's words in the reason text.
            reason = when (kind) {
                RequestKind.MoreTime -> dto.extensionReason
                RequestKind.Delay -> dto.delayReason
                else -> null
            }?.trim()?.takeIf(String::isNotEmpty),
            explanation = dto.transactionReasonText?.trim()?.takeIf(String::isNotEmpty),
            currentDue = WaiveraTime.parseLocalDate(dto.extensionOriginalDueDate),
            requestedDue = WaiveraTime.parseLocalDate(dto.extensionApprovedDueDate ?: dto.extensionRequestedDueDate),
            delayStart = WaiveraTime.parseLocalDate(dto.delayStartDate),
            delayEnd = WaiveraTime.parseLocalDate(dto.delayEndDate),
            isTrainingTask = dto.isTrainingTask,
        )
    }

    /** sprout_web `getEligibleDelayPeriod`: a missing or reversed period fails closed. */
    fun options(dto: ComplianceEligibilityResponse): ChangeOptions {
        val start = WaiveraTime.parseLocalDate(dto.delayStartDate)
        val end = WaiveraTime.parseLocalDate(dto.delayEndDate)
        val open = dto.canRequestDelay && !dto.hasPendingRequest && !dto.hasQueuedApprovedDelay
        val delay = if (open && start != null && end?.isAfter(start) == true) {
            DelayPeriod(start, end, dto.isDelayRenewal)
        } else {
            null
        }
        return ChangeOptions(
            taskId = dto.taskId,
            currentDue = WaiveraTime.parseLocalDate(dto.currentDueDate),
            canAskMoreTime = dto.canRequestExtension && !dto.hasPendingRequest,
            delay = delay,
            hasPendingRequest = dto.hasPendingRequest,
        )
    }

    fun reasons(items: List<ComplianceReasonResponse>, forDelay: Boolean): List<ChangeReason> = items
        .filter { it.isActive && !it.isHiddenFromUserSelection && !it.name.isNullOrBlank() }
        .filter { if (forDelay) it.canBeUsedForDelay else it.canBeUsedForExtension }
        .map {
            ChangeReason(
                id = it.id,
                name = it.name.orEmpty().trim(),
                description = it.userFacingDescription?.trim()?.takeIf(String::isNotEmpty),
                requiresEvidence = it.requiresEvidence,
            )
        }

    fun closureReasons(items: List<TransactionReasonResponse>): List<ChangeReason> = items
        .filter { !it.reasonText.isNullOrBlank() }
        .map {
            val text = it.reasonText.orEmpty().trim()
            ChangeReason(
                id = it.id.toLong(),
                name = text,
                description = null,
                requiresEvidence = false,
                // The web's closure modal asks for a comment on any reason containing "other".
                needsComment = text.contains("other", ignoreCase = true),
            )
        }

    /** sprout_web `RequestDelayModal`: only real, present files can be cited. */
    fun files(items: List<TaskAttachmentResponse>): List<TaskFile> = items
        .filter { it.id > 0 && !it.file?.id.isNullOrBlank() && !it.employeeFileDeleted }
        .map { TaskFile(it.id, it.file?.fileName?.trim()?.takeIf(String::isNotEmpty) ?: "File ${it.id}") }

    fun access(dto: AuthBootstrapResponse): RequestAccess {
        val employeeRoles = dto.roles.employeeRoles.mapNotNull { it.name?.trim()?.uppercase() }
        val isOwnerOrAdmin = dto.roles.isActiveOrganizationOwner ||
            dto.roles.organizationRoles.any { it.systemKey == "ORG_OWNER" } ||
            employeeRoles.any { it == "OWNER" || it == "ADMIN" }
        val officeManager = dto.roles.organizationRoles.any { it.name == OFFICE_MANAGER }
        val externalTraining = hasAbac(dto, EXTERNAL_TRAINING_VIEW) || hasAbac(dto, EXTERNAL_TRAINING_APPROVE)
        val highest = RankedRoles.firstOrNull { it in employeeRoles }
        return RequestAccess(
            canAskMoreTime = hasPolicy(dto, CREATE_EXTENSION),
            canAskClosure = hasPolicy(dto, CREATE_DELETION),
            showsRequests = isOwnerOrAdmin || !officeManager || externalTraining,
            includePendingApprovals = "SC" !in employeeRoles && highest != null && highest != "PROGRAM MANAGER",
            loaded = true,
        )
    }

    /** sprout_web `hasPolicyPermissionV2` with no scope asked; legacy accounts without policies pass. */
    fun hasPolicy(dto: AuthBootstrapResponse, key: String): Boolean {
        val explicit = dto.rolePolicies.any { it.policyPermission?.permissionKey == key }
        val legacy = dto.rolePolicies.isEmpty() && !dto.userAddedInRolePolicy
        return explicit || legacy
    }

    /** sprout_web `doesUserHavePermission` without a resource: wildcards match, conditions must be empty. */
    private fun hasAbac(dto: AuthBootstrapResponse, action: String): Boolean = dto.permissions.any { policy ->
        val granted = policy.action?.trim().orEmpty()
        val matches = when {
            granted == "*" -> true
            granted.endsWith(":*") -> action.startsWith(granted.removeSuffix("*"))
            else -> granted.equals(action, ignoreCase = true)
        }
        val condition = policy.condition?.trim().orEmpty()
        matches && (condition.isEmpty() || condition == "{}" || condition == "null")
    }
}
