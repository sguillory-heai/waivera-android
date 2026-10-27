package com.waivera.waivera.core.network

import com.waivera.waivera.core.network.json.withCamelCaseKeys
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.Json

/**
 * JSON settings for every sprout_api call and hub payload. Unknown fields are ignored so the
 * API can add fields without breaking shipped apps; absent and explicit `null` decode the same
 * (REST omits nulls, the hub emits them). Defaults are encoded because sprout_api applies its own
 * defaults to missing fields (for example a missing push `provider` means "expo"), while
 * `explicitNulls = false` keeps null request fields out of the body.
 */
val WaiveraJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
    coerceInputValues = true
    isLenient = true
}

/**
 * Decodes [text] after normalising PascalCase keys to camelCase, so payloads from
 * `ExceptionMiddleware` and the hub's framework-default serializer decode with the same models
 * as REST (gaps.md G-03).
 */
fun <T> Json.decodeNormalized(deserializer: DeserializationStrategy<T>, text: String): T =
    decodeFromJsonElement(deserializer, parseToJsonElement(text).withCamelCaseKeys())
