plugins {
    alias(libs.plugins.waivera.android.application)
    alias(libs.plugins.waivera.android.application.compose)
    alias(libs.plugins.waivera.hilt)
    // Robolectric + Roborazzi for the navigation path tour (app/src/testDebug, docs/testing/navigation-paths.md).
    alias(libs.plugins.waivera.android.screenshot)
    alias(libs.plugins.kotlin.serialization)
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
    // Firebase values (waivera.firebase.*) are read by core:data into FirebaseConfig, which the
    // app's FirebaseInitializer uses; nothing comes from a committed google-services.json.
    implementation(projects.core.data)
    // Demo repositories and DemoSwitchModule exist only in debug; release includes LiveRepositoriesModule.
    debugImplementation(projects.core.demo)
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
    // Custom Tabs for web links, provided to every screen as LocalUriHandler.
    implementation(libs.androidx.browser)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.navigationevent.compose)
    // SingletonImageLoader.setSafe installs core:data's authenticated loader (G-15).
    implementation(libs.coil)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.common)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(projects.core.testing)
    testImplementation(libs.kotlinx.coroutines.test)
    // The tour builds an in-memory WaiveraDatabase; :core:data keeps Room as an implementation detail.
    testImplementation(libs.androidx.room.runtime)
    // The navigation path tour runs the real Hilt graph with test modules swapped in (@TestInstallIn).
    // Same version as Hilt itself; declared here rather than in the catalog because only these tests use it.
    testImplementation("com.google.dagger:hilt-android-testing:${libs.versions.hilt.get()}")
    kspTest(libs.hilt.compiler)
    kspTest(libs.kotlin.metadata)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
