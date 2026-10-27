package com.waivera.waivera.feature.calendar.data

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.ZoneId

/**
 * The device's zone. Used only to decide whether to say which zone the schedule is shown in;
 * nothing is ever placed or formatted in it.
 */
fun interface DeviceZone {
    fun current(): ZoneId
}

@Module
@InstallIn(SingletonComponent::class)
object CalendarFeatureModule {
    @Provides
    fun deviceZone(): DeviceZone = DeviceZone { ZoneId.systemDefault() }
}
