package com.waivera.waivera.core.designsystem.text

/**
 * The same text with every hyphen made non-breaking (U+2011), for identifiers such as ticket
 * keys that must never wrap mid-ID ("WAIV-" / "2527"). Visible text only: spoken descriptions
 * keep the plain hyphen.
 */
fun String.withNonBreakingHyphens(): String = replace(HYPHEN, NON_BREAKING_HYPHEN)

private const val HYPHEN = '-'
private const val NON_BREAKING_HYPHEN = '‑'
