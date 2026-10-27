plugins {
    alias(libs.plugins.waivera.android.library)
    alias(libs.plugins.waivera.hilt)
    alias(libs.plugins.kotlin.serialization)
}

// Environment values come from a Gradle property (-P, ~/.gradle/gradle.properties or CI)
// or from the git-ignored local.properties. They are never committed.
val localProperties = providers.fileContents(rootProject.layout.projectDirectory.file("local.properties")).asText
fun environmentValue(key: String): String = providers.gradleProperty(key).orNull
    ?: localProperties.orNull?.lineSequence()
        ?.map(String::trim)
        ?.firstOrNull { it.startsWith("$key=") }
        ?.substringAfter('=')
        ?.trim()
    ?: ""

android {
    namespace = "com.waivera.waivera.core.network"

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        buildConfigField("String", "API_BASE_URL", "\"${environmentValue("waivera.apiBaseUrl")}\"")
        buildConfigField("String", "WEB_BASE_URL", "\"${environmentValue("waivera.webBaseUrl")}\"")
    }
}

dependencies {
    api(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.serialization.json)
    api(libs.okhttp)
    api(libs.retrofit)

    testImplementation(projects.core.testing)
    testImplementation(libs.okhttp.mockwebserver)
}
