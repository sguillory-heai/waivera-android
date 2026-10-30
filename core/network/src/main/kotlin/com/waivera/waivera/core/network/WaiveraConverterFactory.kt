package com.waivera.waivera.core.network

import java.lang.reflect.Type
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import retrofit2.Converter
import retrofit2.Retrofit

/**
 * Retrofit converter over kotlinx.serialization that normalises key casing before decoding
 * (see [decodeNormalized]). Request bodies are encoded with [json] as-is (camelCase).
 * Retrofit's built-in converters still handle `Unit`, `ResponseBody` and multipart parts.
 */
class WaiveraConverterFactory(private val json: Json = WaiveraJson) : Converter.Factory() {
    override fun responseBodyConverter(
        type: Type,
        annotations: Array<out Annotation>,
        retrofit: Retrofit,
    ): Converter<ResponseBody, *> {
        val deserializer: KSerializer<Any> = json.serializersModule.serializer(type)
        // A 200 with an empty body (some sprout_api actions return `Ok()`) is "no body" like a
        // 204, so callers see null instead of a decoding failure.
        return Converter<ResponseBody, Any?> { body ->
            body.use {
                val text = it.string()
                if (text.isBlank()) null else json.decodeNormalized(deserializer, text)
            }
        }
    }

    override fun requestBodyConverter(
        type: Type,
        parameterAnnotations: Array<out Annotation>,
        methodAnnotations: Array<out Annotation>,
        retrofit: Retrofit,
    ): Converter<*, RequestBody> {
        val serializer: KSerializer<Any> = json.serializersModule.serializer(type)
        return Converter<Any, RequestBody> { value ->
            json.encodeToString(serializer, value).toRequestBody(JsonMediaType)
        }
    }

    private companion object {
        val JsonMediaType = "application/json; charset=utf-8".toMediaType()
    }
}
