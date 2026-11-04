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
}
