plugins {
    alias(libs.plugins.waivera.android.library)
    alias(libs.plugins.waivera.android.library.compose)
    alias(libs.plugins.waivera.android.screenshot)
}

android {
    namespace = "com.waivera.waivera.core.designsystem"
}

dependencies {
    api(libs.androidx.compose.foundation)
    api(libs.androidx.compose.material3)
    api(libs.androidx.compose.ui)
    // WindowCompat: light status bar icons while a plum hero is on screen.
    implementation(libs.androidx.core.ktx)
    implementation(libs.coil.compose)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.kotlinx.coroutines.test)
    // The shared long-name fixtures (LongFixtures), so layout tests use the same names as features.
    testImplementation(projects.core.testing)
}
