package com.waivera.waivera.feature.today

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * The current time for Today. [ticks] emits now and then every 30 seconds, so relative labels
 * ("in 25 min", "5m") stay true while the screen is open instead of freezing (02 §4 fix).
 */
interface NowSource {
    fun now(): Instant

    val ticks: Flow<Instant>
}

class ClockNowSource @Inject constructor(private val clock: Clock) : NowSource {
    override fun now(): Instant = clock.instant()

    override val ticks: Flow<Instant> = flow {
        while (true) {
            emit(clock.instant())
            delay(TICK_MILLIS)
        }
    }

    private companion object {
        const val TICK_MILLIS = 30_000L
    }
}

@Module
@InstallIn(ViewModelComponent::class)
internal abstract class TodayModule {
    @Binds
    abstract fun nowSource(source: ClockNowSource): NowSource
}
