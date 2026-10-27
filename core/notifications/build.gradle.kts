plugins {
    alias(libs.plugins.waivera.android.library)
    alias(libs.plugins.waivera.hilt)
}

android {
    namespace = "com.waivera.waivera.core.notifications"
    // Robolectric tests read channel names and notification strings from resources.
    testOptions.unitTests.isIncludeAndroidResources = true
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.play.services)
    // Parses the debug push broadcast payload; also used by tests to read fixture JSON.
    implementation(libs.kotlinx.serialization.json)

    testImplementation(projects.core.testing)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
}
