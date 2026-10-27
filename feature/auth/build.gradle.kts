plugins {
    alias(libs.plugins.waivera.android.feature)
}

android {
    namespace = "com.waivera.waivera.feature.auth"
}

dependencies {
    implementation(libs.androidx.activity.compose)
}
