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

/**
 * A file name split before the last part of its stem, for showing on two lines: the head runs
 * up to and including the last "-", "_", "." or space before the extension, and the tail is the
 * rest ("north-roster-" and "wk43.pdf"). The tail is what tells one file from the next and says
 * what kind it is, so it is the part that must never be cut (UI6-06). A name with no such break
 * has an empty head.
 */
fun String.fileNameHeadAndTail(): Pair<String, String> {
    val dot = lastIndexOf(DOT).takeIf { it > 0 } ?: length
    val split = substring(0, dot).indexOfLast { it in FILE_NAME_BREAKS }
    return if (split <= 0) "" to this else substring(0, split + 1) to substring(split + 1)
}

/**
 * Where an email address or handle may wrap besides after its "@" ("alex.rivera@" /
 * "example.org"). Each extra set of break points is turned on only when the part it splits is
 * wider than the line ([addressBreaksFor]).
 */
data class AddressBreaks(
    /** After a dot in the domain, for a domain wider than the line. */
    val domainDots: Boolean = false,
    /**
     * After a ".", "-", "_" or "+" in the local part, for a local part wider than the line.
     * Without them the platform would cut the local part at an arbitrary letter.
     */
    val localPart: Boolean = false,
)

/**
 * The same email address or handle with its break points set for display (UI6-02). It stays
 * whole when it fits and otherwise wraps only where [breaks] allows, so no line starts with "."
 * or "-". Every other punctuation mark is held to its neighbours by word joiners (U+2060):
 * the platform breaker would otherwise split at a hyphen or, inside a long token, before a dot
 * ("alexandria.rivera" / "-montgomery@example" / ".com"). Visible text only: spoken text keeps
 * the plain address. With the default [breaks] it wraps after the "@" only.
 */
fun String.asAddressDisplay(breaks: AddressBreaks = AddressBreaks()): String {
    val at = lastIndexOf(AT)
    val out = StringBuilder(length * 2)
    forEachIndexed { index, char ->
        val last = index == length - 1
        val breakAfter = !last && when {
            index == at -> true
            at in 0 until index -> char == DOT && breaks.domainDots
            else -> char in LOCAL_PART_BREAKS && breaks.localPart
        }
        val guarded = !char.isLetterOrDigit()
        if (guarded && out.isNotEmpty() && out.last().isLetterOrDigit()) out.append(WORD_JOINER)
        out.append(char)
        when {
            breakAfter -> out.append(ZERO_WIDTH_SPACE)
            guarded && !last -> out.append(WORD_JOINER)
        }
    }
    return out.toString()
}

/**
 * The fewest break points that let an address fit [maxWidth], from the one-line widths of its
 * local part with the "@" ([localWidth]) and of its domain ([domainWidth]). A handle without an
 * "@" has a [domainWidth] of 0.
 */
internal fun addressBreaksFor(localWidth: Int, domainWidth: Int, maxWidth: Int): AddressBreaks =
    AddressBreaks(domainDots = domainWidth > maxWidth, localPart = localWidth > maxWidth)

/** The local part with its "@", and the domain, as [addressBreaksFor] measures them. */
internal fun String.addressParts(): Pair<String, String> {
    val at = lastIndexOf(AT)
    return if (at < 0) this to "" else substring(0, at + 1) to substring(at + 1)
}

private const val AT = '@'
private const val LOCAL_PART_BREAKS = ".-_+"
private const val FILE_NAME_BREAKS = "-_. "
private const val DOT = '.'
private const val WORD_JOINER = '\u2060'
private const val HYPHEN = '-'
private const val NON_BREAKING_HYPHEN = '‑'
private const val ZERO_WIDTH_SPACE = '\u200B'
private const val BREAKS_HEADROOM = 8
