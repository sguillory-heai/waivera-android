import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import com.waivera.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType

/**
 * JVM screenshot tests with Roborazzi on Robolectric native graphics.
 * `./gradlew recordRoborazziDebug` writes PNGs to `build/outputs/roborazzi/`,
 * `verifyRoborazziDebug` compares against them. Tests use the matrix helper in
 * `:core:screenshot-testing` so every module captures the same devices, themes and font scales.
 */
abstract class AndroidScreenshotConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            apply(plugin = "io.github.takahirom.roborazzi")

            pluginManager.withPlugin("com.android.library") {
                extensions.configure<LibraryExtension> {
                    testOptions.unitTests.isIncludeAndroidResources = true
                }
            }
            pluginManager.withPlugin("com.android.application") {
                extensions.configure<ApplicationExtension> {
                    testOptions.unitTests.isIncludeAndroidResources = true
                }
            }

            tasks.withType<Test>().configureEach {
                systemProperty("robolectric.graphicsMode", "NATIVE")
                systemProperty("robolectric.pixelCopyRenderMode", "hardware")
                // Robolectric's API 37 runtime reaches FileDescriptor internals through SharedSecrets.
                jvmArgs("--add-exports=java.base/jdk.internal.access=ALL-UNNAMED")
            }

            dependencies {
                if (path != ":core:screenshot-testing") {
                    "testImplementation"(project(":core:screenshot-testing"))
                }
                "testImplementation"(libs.findLibrary("robolectric").get())
                "testImplementation"(libs.findLibrary("roborazzi").get())
                "testImplementation"(libs.findLibrary("roborazzi-compose").get())
                "testImplementation"(libs.findLibrary("androidx-compose-ui-test-junit4").get())
                "debugImplementation"(libs.findLibrary("androidx-compose-ui-test-manifest").get())
            }
        }
    }
}
