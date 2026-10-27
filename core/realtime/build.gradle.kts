plugins {
    alias(libs.plugins.waivera.android.library)
    alias(libs.plugins.waivera.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.waivera.waivera.core.realtime"
}

dependencies {
    api(projects.core.network)
    api(libs.kotlinx.coroutines.core)

    // The SignalR Java client declares Gson and OkHttp as runtime-only; we use Gson types directly.
    implementation(libs.gson)
    implementation(libs.kotlinx.coroutines.rx3)
    implementation(libs.rxjava)
    implementation(libs.signalr)

    testImplementation(projects.core.testing)
}
