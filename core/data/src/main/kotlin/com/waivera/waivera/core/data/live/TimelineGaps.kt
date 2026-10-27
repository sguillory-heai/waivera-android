package com.waivera.waivera.core.data.live

import kotlinx.serialization.Serializable

/**
 * Messages with an id strictly between [afterId] and [beforeId] may be missing from the cache.
 * A jump to an old message (push, bookmark, activity, search) loads a context page that is not
 * joined to the cached tail; the hole between them is recorded so paging can fill it.
 */
@Serializable
data class TimelineGap(val afterId: Long, val beforeId: Long)

/**
 * Pure rules for the cached timeline's holes. Server pages are contiguous, so a page that spans
 * `lo..hi` proves nothing is missing in that range. Ids grow with time (the cache already pages
 * and sorts by id).
 */
object TimelineGaps {
    /** Removes the part of every gap that the contiguous range [lo]..[hi] (inclusive) covers. */
    fun cover(gaps: List<TimelineGap>, lo: Long, hi: Long): List<TimelineGap> = gaps.flatMap { gap ->
        if (hi <= gap.afterId || lo >= gap.beforeId) {
            listOf(gap)
        } else {
            listOfNotNull(
                TimelineGap(gap.afterId, lo).takeIf { lo > gap.afterId },
                TimelineGap(hi, gap.beforeId).takeIf { hi < gap.beforeId },
            )
        }
    }

    /** Adds a gap, merged with any it overlaps; an empty range adds nothing. */
    fun add(gaps: List<TimelineGap>, gap: TimelineGap): List<TimelineGap> {
        if (gap.beforeId <= gap.afterId) return gaps
        val (touching, rest) = gaps.partition { it.afterId < gap.beforeId && gap.afterId < it.beforeId }
        val merged = TimelineGap(
            afterId = (touching.map { it.afterId } + gap.afterId).min(),
            beforeId = (touching.map { it.beforeId } + gap.beforeId).max(),
        )
        return (rest + merged).sortedBy { it.afterId }
    }

    /**
     * The lowest id of the newest cached segment that has messages, or null when that segment
     * reaches the bottom of the cache. Only that segment is shown, so the person never scrolls
     * across a hole; paging older fills the hole right below it first.
     */
    fun windowFloor(gaps: List<TimelineGap>, timelineIds: Collection<Long>): Long? {
        val newest = timelineIds.maxOrNull() ?: return null
        return gaps.sortedByDescending { it.beforeId }.firstOrNull { newest >= it.beforeId }?.beforeId
    }

    /** The gap directly above [id], if the segment holding [id] ends in one. */
    fun gapAbove(gaps: List<TimelineGap>, id: Long): TimelineGap? = gaps.firstOrNull { it.afterId == id }
}
