package com.waivera.waivera.core.network.time

import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * The one timestamp parser for the whole app (dates.md §3, PLAN "Parity fixes"). Every server
 * instant goes through [parseInstant]: values with `Z` or an offset are used as-is, and
 * sprout_api's zone-less UTC wall-clock values (`"2026-10-20T14:32:11.482913"`, 0–7 fractional
 * digits) are read as UTC. Date-only values never become instants.
 */
object WaiveraTime {
    /** The documented fallback when an employee has no valid saved zone (dates.md §2). */
    val FallbackZone: ZoneId = ZoneId.of("America/Chicago")

    private val compactOffset = Regex("""([+-]\d{2})(\d{2})$""")

    /** Parses a server instant; null when the value is blank or not a timestamp. */
    fun parseInstant(raw: String?): Instant? {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty()) return null
        return parseWithOffset(value) ?: parseBareUtc(value)
    }

    /** Parses a `YYYY-MM-DD` value, or the date part of a `YYYY-MM-DDT00:00:00` value. */
    fun parseLocalDate(raw: String?): LocalDate? {
        val value = raw?.trim().orEmpty()
        if (value.length < DATE_LENGTH) return null
        return try {
            LocalDate.parse(value.substring(0, DATE_LENGTH))
        } catch (_: DateTimeParseException) {
            null
        }
    }

    /** Resolves an IANA id; null for blanks, fixed offsets and unknown ids. */
    fun zoneOrNull(id: String?): ZoneId? {
        val value = id?.trim().orEmpty()
        if (value.isEmpty() || !value.contains('/')) return null
        return try {
            ZoneId.of(value)
        } catch (_: DateTimeException) {
            null
        }
    }

    /** The employee's saved zone, or [FallbackZone]; never the device zone. */
    fun employeeZone(id: String?): ZoneId = zoneOrNull(id) ?: FallbackZone

    /** Query-string form for `startTime`/`endTime` parameters: UTC with `Z`. */
    fun formatQueryInstant(instant: Instant): String = DateTimeFormatter.ISO_INSTANT.format(instant)

    private fun parseWithOffset(value: String): Instant? {
        val hasZone = value.endsWith('Z', ignoreCase = true) ||
            OFFSET_SUFFIX.containsMatchIn(value) ||
            compactOffset.containsMatchIn(value)
        if (!hasZone) return null
        val normalized = value.replace(compactOffset) { "${it.groupValues[1]}:${it.groupValues[2]}" }
            .let { if (it.endsWith('z')) it.dropLast(1) + "Z" else it }
        return try {
            OffsetDateTime.parse(normalized).toInstant()
        } catch (_: DateTimeParseException) {
            null
        }
    }

    private fun parseBareUtc(value: String): Instant? = try {
        LocalDateTime.parse(value).toInstant(ZoneOffset.UTC)
    } catch (_: DateTimeParseException) {
        parseLocalDate(value)?.takeIf { value.length == DATE_LENGTH }?.atStartOfDay()?.toInstant(ZoneOffset.UTC)
    }

    private const val DATE_LENGTH = 10
    private val OFFSET_SUFFIX = Regex("""T.*[+-]\d{2}:\d{2}$""")
}

/** Server instant, zone-less or not. Encodes as ISO-8601 UTC with `Z`. */
object UtcInstantSerializer : KSerializer<Instant> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("waivera.UtcInstant", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): Instant {
        val raw = decoder.decodeString()
        return WaiveraTime.parseInstant(raw) ?: throw SerializationException("Not a timestamp")
    }

    override fun serialize(encoder: Encoder, value: Instant) =
        encoder.encodeString(WaiveraTime.formatQueryInstant(value))
}

/** `DateOnly` values (`"2026-10-20"`), and DateTime-typed calendar dates such as birthdays. */
object LocalDateSerializer : KSerializer<LocalDate> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("waivera.LocalDate", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): LocalDate {
        val raw = decoder.decodeString()
        return WaiveraTime.parseLocalDate(raw) ?: throw SerializationException("Not a date")
    }

    override fun serialize(encoder: Encoder, value: LocalDate) = encoder.encodeString(value.toString())
}

/** IANA zone ids stored in local caches. */
object ZoneIdSerializer : KSerializer<ZoneId> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("waivera.ZoneId", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): ZoneId = WaiveraTime.employeeZone(decoder.decodeString())

    override fun serialize(encoder: Encoder, value: ZoneId) = encoder.encodeString(value.id)
}
