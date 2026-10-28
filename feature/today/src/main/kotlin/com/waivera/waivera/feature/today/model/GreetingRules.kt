package com.waivera.waivera.feature.today.model

import androidx.compose.runtime.Immutable
import java.time.Instant
import java.time.ZoneId

/** The part of the day the greeting names. */
enum class DayPart { MORNING, AFTERNOON, EVENING }

/** "Good morning, Alex": the part of the day in the employee's zone and their first name, if known. */
@Immutable
data class Greeting(val part: DayPart, val firstName: String?)

/**
 * Today's greeting. The hour comes from the employee's saved zone, never the device's, so a
 * person whose phone is set elsewhere is still greeted for the time where they work. A named
 * zone (not a fixed offset) keeps the boundaries right across daylight-saving changes.
 */
object GreetingRules {
    /** Morning starts at 05:00, afternoon at 12:00 and evening at 17:00; the night stays "evening". */
    const val MORNING_STARTS = 5
    const val AFTERNOON_STARTS = 12
    const val EVENING_STARTS = 17

    fun dayPart(now: Instant, zone: ZoneId): DayPart {
        val hour = now.atZone(zone).hour
        return when {
            hour < MORNING_STARTS -> DayPart.EVENING
            hour < AFTERNOON_STARTS -> DayPart.MORNING
            hour < EVENING_STARTS -> DayPart.AFTERNOON
            else -> DayPart.EVENING
        }
    }

    /**
     * The name to greet by: the first name when the profile has one, else the first word of the
     * display name. Null (a greeting without a name) when neither is usable; never the email.
     */
    fun firstName(firstName: String?, displayName: String?): String? = firstName?.trim()?.takeIf(String::isNotEmpty)
        ?: displayName?.trim()?.split(WHITESPACE)?.firstOrNull()?.takeIf(String::isNotEmpty)

    fun greeting(now: Instant, zone: ZoneId, firstName: String?): Greeting =
        Greeting(part = dayPart(now, zone), firstName = firstName)

    private val WHITESPACE = Regex("\\s+")
}
