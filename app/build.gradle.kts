plugins {
    alias(libs.plugins.waivera.android.application)
    alias(libs.plugins.waivera.android.application.compose)
    alias(libs.plugins.waivera.hilt)
}

android {
    namespace = "com.waivera.waivera"

    defaultConfig {
        // Same ID as the Expo app so the native build ships as an update to the existing Play listing.
        applicationId = "com.waivera.waivera"
        versionCode = 1
        versionName = "0.1.0"
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
    implementation(projects.core.designsystem)
    implementation(projects.core.network)
    implementation(projects.core.realtime)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)

    testImplementation(projects.core.testing)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
