package com.waivera.waivera.startup

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.waivera.waivera.core.data.auth.FirebaseConfig

/**
 * The only place the default FirebaseApp is created. Values are the `waivera.firebase.*` keys
 * that `:core:data` reads into [FirebaseConfig] (from `local.properties` or CI secrets), because no
 * `google-services.json` is committed. Without them the app still runs: the session reports
 * missing configuration and debug builds fall back to demo data.
 */
object FirebaseInitializer {
    fun initialize(context: Context): Boolean {
        if (FirebaseApp.getApps(context).isNotEmpty()) return true
        val options = options() ?: return false
        FirebaseApp.initializeApp(context, options)
        return true
    }

    /** Null when a required value is missing. The sender id is optional (push only). */
    fun options(config: FirebaseConfig = FirebaseConfig.fromBuildConfig()): FirebaseOptions? {
        if (config.missingKeys.isNotEmpty()) return null
        return FirebaseOptions.Builder()
            .setApiKey(config.apiKey)
            .setApplicationId(config.appId)
            .setProjectId(config.projectId)
            .apply { if (config.senderId.isNotBlank()) setGcmSenderId(config.senderId) }
            .build()
    }
}
