plugins {
    alias(libs.plugins.waivera.android.library)
    alias(libs.plugins.waivera.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.waivera.waivera.core.demo"

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        // -Pwaivera.demoMode=true forces demo data in debug builds; see docs/architecture/data-layer.md.
        val demoMode = providers.gradleProperty("waivera.demoMode").orNull?.toBoolean() ?: false
        buildConfigField("boolean", "DEMO_MODE", demoMode.toString())
    }
}

dependencies {
    api(projects.core.data)

    testImplementation(projects.core.testing)
}
