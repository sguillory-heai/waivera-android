package com.waivera.waivera.core.network.error

import com.waivera.waivera.core.network.WaiveraJson
import com.waivera.waivera.core.network.json.withCamelCaseKeys
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * A non-2xx body in any of the six shapes sprout_api produces (gaps.md G-12): empty, a bare JSON
 * string, plain text, camelCase `ApiResponse`, PascalCase `ApiResponse` from `ExceptionMiddleware`,
 * and `{ success, message }`. [message] is used only to classify the error, never shown.
 */
data class ApiErrorBody(val message: String?, val errorCode: String?) {
    companion object {
        private val MessageKeys = listOf("message", "error", "title")

        fun parse(body: String?): ApiErrorBody {
            val text = body?.trim().orEmpty()
            if (text.isEmpty()) return ApiErrorBody(null, null)
            val element = runCatching { WaiveraJson.parseToJsonElement(text) }.getOrNull()
            return when (element) {
                is JsonObject -> fromObject(element.withCamelCaseKeys() as JsonObject)
                is JsonPrimitive -> ApiErrorBody(element.contentOrNull, null)
                else -> ApiErrorBody(text, null)
            }
        }

        private fun fromObject(json: JsonObject): ApiErrorBody {
            val message = MessageKeys.firstNotNullOfOrNull { key -> (json[key] as? JsonPrimitive)?.contentOrNull }
            val errorCode = (json["errorCode"] as? JsonPrimitive)?.contentOrNull
            return ApiErrorBody(message, errorCode)
        }
    }
}
