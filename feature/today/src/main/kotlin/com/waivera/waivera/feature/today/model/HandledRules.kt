package com.waivera.waivera.feature.today.model

import com.waivera.waivera.core.data.model.HandledObservation
import com.waivera.waivera.core.data.model.HandledToday
import java.time.LocalDate

/** How Today feeds the on-device handled tally and reads the hero's ring back from it. */
internal object HandledRules {
    /**
     * What needs the person, for the tally: only when Needs you, the schedule (invitations) and
     * the activity feed (mentions) have all loaded without failing, which the caller passes as
     * [complete]. Every open item counts, including those collapsed out of view.
     */
    fun observation(
        context: TodayContext,
        today: LocalDate,
        openItems: List<NeedsYouItem>,
        complete: Boolean,
    ): HandledObservation? {
        val userId = context.myUserId ?: return null
        if (!complete) return null
        val keys = openItems.mapTo(linkedSetOf()) { it.key }
        return HandledObservation(
            userId = userId,
            day = today,
            open = keys,
            undoable = keys.filterTo(linkedSetOf(), ScheduleRules::isInvitationKey),
        )
    }

    /**
     * The ring as it will be once [observation] is saved, worked out here so it never flickers a
     * step behind. Without a complete observation it keeps today's saved count, if any.
     */
    fun progress(
        context: TodayContext,
        today: LocalDate,
        needsYou: NeedsYouSection,
        observation: HandledObservation?,
    ): TodayProgress? {
        if (observation != null) {
            val tally = HandledToday.advance(context.handled, observation)
            return TodayProgress(handled = tally.handled, open = tally.open.size)
        }
        if (!needsYou.isReady) return null
        val saved = context.handled?.takeIf { it.userId == context.myUserId && it.day == today }
        return TodayProgress(handled = saved?.handled ?: 0, open = needsYou.totalCount)
    }
}
