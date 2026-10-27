plugins {
    alias(libs.plugins.waivera.android.feature)
}

android {
    namespace = "com.waivera.waivera.feature.settings"
}

dependencies {
    // NotificationManagerCompat: app-level permission and per-channel importance for the settings screen.
    implementation(libs.androidx.core.ktx)
}
