package com.waivera.waivera.core.designsystem.text

/**
 * The same text with every hyphen made non-breaking (U+2011), for identifiers such as ticket
 * keys that must never wrap mid-ID ("WAIV-" / "2527"). Visible text only: spoken descriptions
 * keep the plain hyphen.
 */
fun String.withNonBreakingHyphens(): String = replace(HYPHEN, NON_BREAKING_HYPHEN)

/**
 * The same text with a zero-width break point (U+200B) after every hyphen that joins two parts
 * of a word, for names and channel names ("Maria-Fernanda Castellanos-Whitaker",
 * "old-pilot-program-coordination"). With hyphenation off, the platform breaker does not treat
 * "-" inside a word as a place to wrap, so a long hyphenated name was split mid-word instead
 * (UI4-02). The break point is invisible and silent. Visible text only.
 */
fun String.withBreaksAfterHyphens(): String {
    if (HYPHEN !in this) return this
    val out = StringBuilder(length + BREAKS_HEADROOM)
    forEachIndexed { index, char ->
        out.append(char)
        val joinsWord = char == HYPHEN &&
            index > 0 &&
            this[index - 1].isLetterOrDigit() &&
            index + 1 < length &&
            this[index + 1].isLetterOrDigit()
        if (joinsWord) out.append(ZERO_WIDTH_SPACE)
    }
    return out.toString()
}

/**
 * The same file name with its extension glued to the part before it by word joiners (U+2060)
 * around the last dot, so a wrap never leaves ".jpg" or "pdf" alone on a line ("site-photo. /
 * jpg", UI5-11). Visible text only.
 */
fun String.withUnbreakableExtension(): String {
    val dot = lastIndexOf(DOT)
    if (dot <= 0 || dot == length - 1) return this
    return substring(0, dot) + WORD_JOINER + DOT + WORD_JOINER + substring(dot + 1)
}

private const val DOT = '.'
private const val WORD_JOINER = '\u2060'
private const val HYPHEN = '-'
private const val NON_BREAKING_HYPHEN = '‑'
private const val ZERO_WIDTH_SPACE = '\u200B'
private const val BREAKS_HEADROOM = 8
