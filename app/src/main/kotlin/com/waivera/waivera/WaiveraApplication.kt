package com.waivera.waivera

import android.app.Application
import com.waivera.waivera.core.data.repository.DataLayerStarter
import com.waivera.waivera.core.notifications.channel.NotificationChannelInstaller
import com.waivera.waivera.startup.FirebaseInitializer
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.components.SingletonComponent

@HiltAndroidApp
class WaiveraApplication : Application() {
    override fun onCreate() {
        // Firebase must exist before Hilt builds anything that touches Auth or Messaging.
        FirebaseInitializer.initialize(this)
        super.onCreate()
        val startup = EntryPointAccessors.fromApplication(this, StartupEntryPoint::class.java)
        startup.channels().install()
        startup.dataLayer().start()
    }

    /** An entry point instead of field injection, so nothing injectable is `lateinit` (AGENTS.md). */
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface StartupEntryPoint {
        fun channels(): NotificationChannelInstaller

        fun dataLayer(): DataLayerStarter
    }
}
