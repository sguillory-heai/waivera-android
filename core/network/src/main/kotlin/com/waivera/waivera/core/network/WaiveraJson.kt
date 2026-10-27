package com.waivera.waivera.core.network

import kotlinx.serialization.json.Json

/**
 * JSON settings for every sprout_api call. Unknown fields are ignored so the API
 * can add fields without breaking shipped apps. Defaults are encoded because
 * sprout_api applies its own defaults to missing fields (for example a missing
 * push `provider` means "expo").
 */
val WaiveraJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
    coerceInputValues = true
}
