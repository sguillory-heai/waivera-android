package com.waivera.waivera.core.network.error

import com.waivera.waivera.core.network.dto.ApiResponse
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.serialization.SerializationException
import retrofit2.Response

/**
 * Runs a Retrofit call and returns its body, or throws a [WaiveraError]. Every repository goes
 * through this (or [apiCallOrNull]/[apiCallUnit]) so error mapping happens in one place.
 */
suspend fun <T : Any> apiCall(block: suspend () -> Response<T>): T =
    apiCallOrNull(block) ?: throw WaiveraError.Unexpected()

/** Like [apiCall], but a successful response without a body (204, empty 200) returns null. */
suspend fun <T : Any> apiCallOrNull(block: suspend () -> Response<T>): T? {
    val response = execute(block)
    if (!response.isSuccessful) throw response.toWaiveraError()
    return response.body()
}

/** For endpoints whose success has no meaningful body. */
suspend fun apiCallUnit(block: suspend () -> Response<Unit>) {
    val response = execute(block)
    if (!response.isSuccessful) throw response.toWaiveraError()
}

/** The `data` of an enveloped response; a missing `data` is a failure (gaps.md G-12). */
fun <T : Any> ApiResponse<T>.requireData(): T = data ?: throw WaiveraError.Unexpected(statusCode)

/** Turns a failed Retrofit response into a [WaiveraError] without leaking the body. */
fun Response<*>.toWaiveraError(): WaiveraError {
    val body = runCatching { errorBody()?.use { it.string() } }.getOrNull()
    return ErrorMapper.fromHttp(code(), body)
}

private suspend fun <T> execute(block: suspend () -> Response<T>): Response<T> = try {
    block()
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (error: WaiveraError) {
    throw error
} catch (error: IOException) {
    throw WaiveraError.from(error)
} catch (error: SerializationException) {
    throw WaiveraError.Unexpected(cause = error)
} catch (error: IllegalArgumentException) {
    // kotlinx.serialization reports some malformed payloads as IllegalArgumentException.
    throw WaiveraError.Unexpected(cause = error)
}

/**
 * `runCatching` for suspend code that keeps coroutine cancellation working and maps failures to
 * [WaiveraError].
 */
@Suppress("TooGenericExceptionCaught")
suspend fun <T> catchingWaivera(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (error: Throwable) {
    Result.failure(WaiveraError.from(error))
}

/** The [WaiveraError] of a failed [Result], or null on success. */
fun Result<*>.waiveraErrorOrNull(): WaiveraError? = exceptionOrNull()?.let(WaiveraError::from)
