plugins {
    alias(libs.plugins.waivera.android.library)
}

android {
    namespace = "com.waivera.waivera.core.testing"
}

dependencies {
    api(projects.core.demo)
    api(libs.junit)
    api(libs.kotlin.test)
    api(libs.kotlinx.coroutines.test)
    api(libs.okhttp.mockwebserver)
    api(libs.turbine)
}
