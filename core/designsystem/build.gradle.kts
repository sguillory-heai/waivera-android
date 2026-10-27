plugins {
    alias(libs.plugins.waivera.android.library)
    alias(libs.plugins.waivera.android.library.compose)
}

android {
    namespace = "com.waivera.waivera.core.designsystem"
}

dependencies {
    api(libs.androidx.compose.foundation)
    api(libs.androidx.compose.material3)
    api(libs.androidx.compose.ui)
}
