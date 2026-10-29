package com.waivera.waivera.feature.profile

import androidx.compose.runtime.Composable
import com.waivera.waivera.core.designsystem.theme.isLargeFontScale

/**
 * How many lines a name takes on the profile and person heroes: two, three at large font. A
 * longer name ends in "…", so the hero leaves room for what follows it (wave 29 N4).
 */
@Composable
internal fun heroNameMaxLines(): Int = if (isLargeFontScale()) HERO_NAME_LINES_LARGE_FONT else HERO_NAME_LINES

private const val HERO_NAME_LINES = 2
private const val HERO_NAME_LINES_LARGE_FONT = 3

/** Test tag for the person page's hero name. */
internal const val PERSON_HERO_NAME_TAG = "person_hero_name"

/** Test tag for your own profile's hero name. */
internal const val PROFILE_HERO_NAME_TAG = "profile_hero_name"
