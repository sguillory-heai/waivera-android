package com.waivera.waivera.core.network.dto

import kotlinx.serialization.Serializable

/**
 * `Utilities/ApiResponse.cs`. `data` is absent when null. `success` is computed from the status
 * code, but `GET /api/chat/privatechats/{id}/conversation` returns 200 with no data on failure,
 * so callers must treat missing data as a failure (gaps.md G-12).
 */
@Serializable
data class ApiResponse<T>(
    val data: T? = null,
    val statusCode: Int = 0,
    val message: String? = null,
    val errorCode: String? = null,
    val success: Boolean = false,
)

/** `Utilities/PagedResponse.cs`. */
@Serializable
data class PagedResponse<T>(
    val data: List<T> = emptyList(),
    val totalRecords: Int = 0,
    val pageNumber: Int = 0,
    val pageSize: Int = 0,
)
