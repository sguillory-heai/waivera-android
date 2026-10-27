package com.waivera.waivera.core.network.json

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Returns a copy whose object keys start with a lower-case letter, matching
 * System.Text.Json's camelCase policy (`OwnerUserID` becomes `ownerUserID`). A key that already
 * exists in camelCase wins over its PascalCase twin. Applied to every decoded payload because the
 * hub's casing is a framework default rather than a pinned setting (gaps.md G-03).
 */
fun JsonElement.withCamelCaseKeys(): JsonElement = when (this) {
    is JsonObject -> {
        val normalized = LinkedHashMap<String, JsonElement>(size)
        forEach { (key, value) -> normalized.putIfAbsent(key.camelCased(), value.withCamelCaseKeys()) }
        forEach { (key, value) -> if (key == key.camelCased()) normalized[key] = value.withCamelCaseKeys() }
        JsonObject(normalized)
    }
    is JsonArray -> JsonArray(map { it.withCamelCaseKeys() })
    else -> this
}

private fun String.camelCased(): String =
    if (isNotEmpty() && this[0].isUpperCase()) this[0].lowercaseChar() + substring(1) else this
