plugins {
    alias(libs.plugins.waivera.android.library)
    alias(libs.plugins.waivera.hilt)
    alias(libs.plugins.kotlin.serialization)
}

// Firebase values come from a Gradle property or local.properties, never from a committed
// google-services.json. Missing values put the live session into a configuration error.
val localProperties = providers.fileContents(rootProject.layout.projectDirectory.file("local.properties")).asText
fun environmentValue(key: String): String = providers.gradleProperty(key).orNull
    ?: localProperties.orNull?.lineSequence()
        ?.map(String::trim)
        ?.firstOrNull { it.startsWith("$key=") }
        ?.substringAfter('=')
        ?.trim()
    ?: ""

android {
    namespace = "com.waivera.waivera.core.data"

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        buildConfigField("String", "FIREBASE_API_KEY", "\"${environmentValue("waivera.firebase.apiKey")}\"")
        buildConfigField("String", "FIREBASE_APP_ID", "\"${environmentValue("waivera.firebase.appId")}\"")
        buildConfigField("String", "FIREBASE_PROJECT_ID", "\"${environmentValue("waivera.firebase.projectId")}\"")
        buildConfigField("String", "FIREBASE_SENDER_ID", "\"${environmentValue("waivera.firebase.senderId")}\"")
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

dependencies {
    api(projects.core.network)
    api(projects.core.realtime)

    implementation(libs.androidx.datastore)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.work.runtime)
    implementation(libs.coil.core)
    implementation(libs.coil.network.okhttp)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.tink.android)
    ksp(libs.androidx.room.compiler)

    testImplementation(projects.core.testing)
}
