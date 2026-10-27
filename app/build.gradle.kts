plugins {
    alias(libs.plugins.waivera.android.application)
    alias(libs.plugins.waivera.android.application.compose)
    alias(libs.plugins.waivera.hilt)
    alias(libs.plugins.kotlin.serialization)
}

// Firebase values come from a Gradle property (CI secrets) or local.properties, never from a
// committed google-services.json. Missing values build a working app that offers demo mode.
val localProperties = providers.fileContents(rootProject.layout.projectDirectory.file("local.properties")).asText

fun environmentValue(key: String): String = providers.gradleProperty(key).orNull
    ?: localProperties.orNull?.lineSequence()
        ?.map(String::trim)
        ?.firstOrNull { it.startsWith("$key=") }
        ?.substringAfter('=')
        ?.trim()
    ?: ""

android {
    namespace = "com.waivera.waivera"

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        // Same ID as the Expo app so the native build ships as an update to the existing Play listing.
        applicationId = "com.waivera.waivera"
        versionCode = 1
        versionName = "0.1.0"

        buildConfigField("String", "FIREBASE_API_KEY", "\"${environmentValue("waivera.firebase.apiKey")}\"")
        buildConfigField("String", "FIREBASE_APP_ID", "\"${environmentValue("waivera.firebase.appId")}\"")
        buildConfigField("String", "FIREBASE_PROJECT_ID", "\"${environmentValue("waivera.firebase.projectId")}\"")
        buildConfigField("String", "FIREBASE_SENDER_ID", "\"${environmentValue("waivera.firebase.senderId")}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}

dependencies {
    implementation(projects.core.data)
    implementation(projects.core.demo)
    implementation(projects.core.designsystem)
    implementation(projects.core.network)
    implementation(projects.core.notifications)
    implementation(projects.core.realtime)
    implementation(projects.feature.auth)
    implementation(projects.feature.calendar)
    implementation(projects.feature.chat)
    implementation(projects.feature.profile)
    implementation(projects.feature.settings)
    implementation(projects.feature.today)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.common)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(projects.core.testing)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
