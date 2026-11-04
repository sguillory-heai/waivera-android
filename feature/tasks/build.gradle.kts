plugins {
    alias(libs.plugins.waivera.android.feature)
}

android {
    namespace = "com.waivera.waivera.feature.tasks"
}

dependencies {
    // Word (.docx) to HTML for the read-only file viewer, the same converter the web uses
    // (mammoth.js); pure Java with no transitive dependencies.
    implementation(libs.mammoth)
    // Saving to a picked location, taking photos and picking files use Activity Result contracts.
    implementation(libs.androidx.activity.compose)
    // FileProvider for sharing a task file with another app, and for the camera's output file.
    implementation(libs.androidx.core.ktx)
}
