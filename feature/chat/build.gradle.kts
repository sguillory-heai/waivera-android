plugins {
    alias(libs.plugins.waivera.android.feature)
}

android {
    namespace = "com.waivera.waivera.feature.chat"
}

dependencies {
    // Picking an image and saving a file use Activity Result contracts from Compose.
    implementation(libs.androidx.activity.compose)
    // FileProvider for sharing a downloaded attachment with another app.
    implementation(libs.androidx.core.ktx)
    // Images load through the app's authenticated Coil singleton (G-15) and the screen draws the
    // results itself. `libs.coil.compose` is in the catalog for a later move to AsyncImage.
    implementation(libs.coil.core)
    // TipTap documents and ticket payloads arrive as JsonElement.
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}
