plugins {
    alias(libs.plugins.waivera.android.library)
    alias(libs.plugins.waivera.android.library.compose)
}

android {
    namespace = "com.waivera.waivera.core.screenshottesting"
}

// Test-only helpers: consumed with testImplementation through the waivera.android.screenshot convention.
dependencies {
    api(projects.core.designsystem)
    api(libs.robolectric)
    api(libs.roborazzi)
    api(libs.roborazzi.compose)
    api(libs.androidx.compose.ui.test.junit4)
    // Captures load people's photos through the app's image loader over the demo people (UI4-10).
    implementation(projects.core.demo)
    implementation(libs.coil)
    implementation(libs.okhttp)
}
