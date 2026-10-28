package com.waivera.waivera.core.data.repository

import com.waivera.waivera.core.data.model.HandledObservation
import com.waivera.waivera.core.data.model.HandledTally
import kotlinx.coroutines.flow.Flow

/** Today's handled count, kept on this device ([HandledTally]). */
interface HandledTodayRepository {
    /** The saved tally; null before the first observation. */
    val tally: Flow<HandledTally?>

    /** Advances the saved tally by [observation] and returns it. */
    suspend fun record(observation: HandledObservation): HandledTally
}
