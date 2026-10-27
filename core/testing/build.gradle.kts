plugins {
    alias(libs.plugins.waivera.android.library)
}

android {
    namespace = "com.waivera.waivera.core.testing"
}

dependencies {
    api(libs.junit)
    api(libs.kotlin.test)
    api(libs.kotlinx.coroutines.test)
    api(libs.turbine)
}
