package com.waivera.waivera.core.data.live

import com.waivera.waivera.core.data.model.LeaveBlock
import com.waivera.waivera.core.data.model.LeavePreview
import com.waivera.waivera.core.data.model.LeaveSendProblem
import com.waivera.waivera.core.data.model.LeaveType
import com.waivera.waivera.core.network.dto.LeaveBalanceResponse
import com.waivera.waivera.core.network.dto.LeavePreviewResponse
import com.waivera.waivera.core.network.dto.LeaveTypeResponse
import com.waivera.waivera.core.network.error.WaiveraError

/** What a person asking for time off sees: leave types with balances, the server's check, and refusals. */
object LeaveMapping {
    /** The name from the type list, the balance from the person's balances; types are kept in the server's order. */
    fun leaveTypes(types: List<LeaveTypeResponse>, balances: List<LeaveBalanceResponse>?): List<LeaveType> = types
        .filter { it.isActive && !it.isDeleted }
        .mapNotNull { type ->
            val name = type.requestType?.trim()?.takeIf(String::isNotEmpty) ?: return@mapNotNull null
            val balance = balances?.firstOrNull { it.id == type.id }
            LeaveType(
                id = type.id,
                name = name,
                tracksBalance = balance?.tracksBalance ?: false,
                availableMinutes = balance?.takeIf { it.tracksBalance }?.availableMinutes,
            )
        }

    fun preview(dto: LeavePreviewResponse) = LeavePreview(
        requestedMinutes = dto.requestedMinutes,
        tracksBalance = dto.tracksBalance,
        beforeMinutes = dto.beforeMinutes,
        afterMinutes = dto.afterMinutes,
        // sprout_web: no requestable hours blocks the send even when the server forgets a code.
        block = if (dto.isRequestable && dto.requestedMinutes > 0) {
            null
        } else {
            when (dto.blockingCode) {
                "LEAVE_ON_WEEKEND" -> LeaveBlock.Weekend
                "LEAVE_ON_HOLIDAY" -> LeaveBlock.Holiday
                "LEAVE_OVERLAPS_EXISTING" -> LeaveBlock.Overlap
                else -> LeaveBlock.Other
            }
        },
        impact = dto.intelligence?.let(ApprovalMapping::impact),
    )

    /** sprout_api `AddOrUpdateTimeOffAsync` and `ReserveAsync` refusals the person can act on. */
    fun sendProblem(error: WaiveraError): LeaveSendProblem? = when {
        error is WaiveraError.Conflict -> LeaveSendProblem.Holiday
        error !is WaiveraError.Validation -> null
        else -> when (error.errorCode) {
            "INSUFFICIENT_LEAVE_BALANCE" -> LeaveSendProblem.NotEnoughBalance
            "LEAVE_NOT_ALLOWED_DURING_PROBATION" -> LeaveSendProblem.Probation
            "LEAVE_ON_WEEKEND" -> LeaveSendProblem.Weekend
            "LEAVE_HAS_NO_WORKDAYS" -> LeaveSendProblem.NoWorkdays
            // The overlap refusal carries no code; it is the one 400 a filled-in form still gets.
            null -> LeaveSendProblem.Overlap
            else -> LeaveSendProblem.Other
        }
    }
}
