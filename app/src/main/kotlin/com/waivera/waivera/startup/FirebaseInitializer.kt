package com.waivera.waivera.startup

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.waivera.waivera.BuildConfig

/**
 * Starts Firebase from BuildConfig values (from `local.properties` or CI secrets), because no
 * `google-services.json` is committed. Without values the app still runs: the session reports
 * missing configuration and sign-in offers demo mode.
 */
object FirebaseInitializer {
    fun initialize(context: Context): Boolean {
        if (FirebaseApp.getApps(context).isNotEmpty()) return true
        val options = options() ?: return false
        FirebaseApp.initializeApp(context, options)
        return true
    }

    /** Null when any required value is missing. */
    fun options(
        apiKey: String = BuildConfig.FIREBASE_API_KEY,
        appId: String = BuildConfig.FIREBASE_APP_ID,
        projectId: String = BuildConfig.FIREBASE_PROJECT_ID,
        senderId: String = BuildConfig.FIREBASE_SENDER_ID,
    ): FirebaseOptions? {
        if (listOf(apiKey, appId, projectId, senderId).any(String::isBlank)) return null
        return FirebaseOptions.Builder()
            .setApiKey(apiKey)
            .setApplicationId(appId)
            .setProjectId(projectId)
            .setGcmSenderId(senderId)
            .build()
    }
}
