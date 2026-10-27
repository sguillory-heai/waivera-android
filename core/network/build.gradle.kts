plugins {
    alias(libs.plugins.waivera.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.waivera.waivera.core.network"
}

dependencies {
    api(libs.kotlinx.serialization.json)
    api(libs.okhttp)
    api(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)

    testImplementation(projects.core.testing)
    testImplementation(libs.okhttp.mockwebserver)
}
