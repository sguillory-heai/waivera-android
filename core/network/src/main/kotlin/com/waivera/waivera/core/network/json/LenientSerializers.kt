package com.waivera.waivera.core.network.json

import com.waivera.waivera.core.network.WaiveraJson
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Accepts `true`/`false`, `"true"`/`"false"` in any case, and numbers (non-zero is true).
 * The Expo client coerces the login gate flags this way, and the native apps must not lose the
 * password-expiry gate over a representation change.
 */
object LenientBooleanSerializer : KSerializer<Boolean> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("waivera.LenientBoolean", PrimitiveKind.BOOLEAN)

    override fun deserialize(decoder: Decoder): Boolean {
        val json = decoder as? JsonDecoder ?: return decoder.decodeBoolean()
        val primitive = json.decodeJsonElement() as? JsonPrimitive ?: return false
        return primitive.booleanOrNull
            ?: primitive.content.trim().equals("true", ignoreCase = true).takeIf { primitive.isString }
            ?: ((primitive.doubleOrNull ?: 0.0) != 0.0)
    }

    override fun serialize(encoder: Encoder, value: Boolean) = encoder.encodeBoolean(value)
}

/** Ids that the server types as strings but older payloads sent as numbers (and vice versa). */
object StringOrNumberSerializer : KSerializer<String> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("waivera.StringOrNumber", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): String {
        val json = decoder as? JsonDecoder ?: return decoder.decodeString()
        val primitive = json.decodeJsonElement() as? JsonPrimitive
            ?: throw SerializationException("Expected a string or number id")
        return primitive.longOrNull?.toString() ?: primitive.content
    }

    override fun serialize(encoder: Encoder, value: String) = encoder.encodeString(value)
}

/**
 * A rich-text document that most endpoints send as a JSON object but `GET /api/chat/all`
 * sends as a JSON string holding the object (gaps.md G-05). Both decode to the object.
 * Unparsable strings decode to null rather than failing the whole conversation list.
 */
object JsonDocSerializer : KSerializer<JsonElement?> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("waivera.JsonDoc", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): JsonElement? {
        val json = decoder as? JsonDecoder ?: return null
        val element = json.decodeJsonElement()
        val decoded = if (element is JsonPrimitive && element.isString) {
            runCatching { WaiveraJson.parseToJsonElement(element.content) }.getOrNull()
        } else {
            element
        }
        return decoded?.takeUnless { it is JsonNull }
    }

    override fun serialize(encoder: Encoder, value: JsonElement?) {
        val json = encoder as? JsonEncoder ?: throw SerializationException("JSON only")
        json.encodeJsonElement(value ?: JsonNull)
    }
}

/**
 * Contract enums arrive as their C# member name over REST and as an integer over the hub
 * (gaps.md G-03). Unknown values map to [unknown] instead of failing the payload.
 */
abstract class LenientEnumSerializer<E : Enum<E>>(
    serialName: String,
    private val values: Array<E>,
    private val unknown: E,
    private val wireName: (E) -> String,
    private val code: (E) -> Int? = { null },
) : KSerializer<E> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor(serialName, PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): E {
        val json = decoder as? JsonDecoder ?: return fromName(decoder.decodeString())
        val primitive = json.decodeJsonElement() as? JsonPrimitive ?: return unknown
        if (primitive is JsonNull) return unknown
        val number = if (primitive.isString) null else primitive.longOrNull
        return if (number != null) {
            values.firstOrNull { code(it)?.toLong() == number } ?: unknown
        } else {
            fromName(primitive.content)
        }
    }

    override fun serialize(encoder: Encoder, value: E) = encoder.encodeString(wireName(value))

    private fun fromName(raw: String): E {
        val trimmed = raw.trim()
        return values.firstOrNull { it != unknown && wireName(it).equals(trimmed, ignoreCase = true) } ?: unknown
    }
}
