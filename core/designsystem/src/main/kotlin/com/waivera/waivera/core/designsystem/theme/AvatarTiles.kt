package com.waivera.waivera.core.designsystem.theme

/**
 * The fallback avatar for someone without a photo: their initials in white on one of six
 * on-brand gradients, picked from the name so a person keeps the same colours everywhere (in
 * the app and on chat notifications), with a soft sheen across the top and a faint inner edge.
 * Plain ARGB values so code outside Compose (notification bitmaps) draws the same tile.
 */
object AvatarTiles {
    /** Gradient start (top left) and end (bottom right) of each tile colour. */
    val Pairs: List<Pair<Int, Int>> = listOf(
        0xFF7E57C2.toInt() to 0xFFA640DA.toInt(),
        0xFFB4407E.toInt() to 0xFFFF6FBC.toInt(),
        0xFF1F8F7E.toInt() to 0xFF3CC2A6.toInt(),
        0xFF4F5BD5.toInt() to 0xFF8A6CF0.toInt(),
        0xFFC2643A.toInt() to 0xFFF0A04B.toInt(),
        0xFF6A3FA0.toInt() to 0xFFC04FD0.toInt(),
    )

    /** White over the top of the tile. */
    const val SHEEN_ALPHA = 0.16f

    /** The inner edge, white. */
    const val EDGE_ALPHA = 0.24f

    /** Edge width as a share of the side. */
    const val EDGE_FRACTION = 0.016f

    /** Initials' soft shadow, deep plum, with its blur and drop as shares of the side. */
    const val SHADOW_COLOR = 0x5A280A3C
    const val SHADOW_BLUR = 0.03f
    const val SHADOW_DROP = 0.012f

    /** The same name always lands on the same colours; case and outer spaces don't matter. */
    fun pairFor(name: String): Pair<Int, Int> {
        val key = name.trim().lowercase()
        var hash = 0
        key.forEach { hash = hash * HASH_MULTIPLIER + it.code }
        return Pairs[Math.floorMod(hash, Pairs.size)]
    }

    private const val HASH_MULTIPLIER = 31
}
