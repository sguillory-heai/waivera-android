plugins {
    alias(libs.plugins.waivera.android.library)
}

android {
    namespace = "com.waivera.waivera.core.realtime"
}

dependencies {
    api(libs.kotlinx.coroutines.core)

    testImplementation(projects.core.testing)
}
