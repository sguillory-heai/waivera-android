import com.waivera.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.dependencies

/**
 * Everything a `feature:*` module needs: a Compose library with Hilt, the design system, the
 * data layer's repository interfaces and models (`:core:data`), lifecycle-aware collection,
 * Hilt ViewModels and screenshot tests. Features never depend
 * on each other or on `:app`; navigation arrives as lambdas from the app's nav graph.
 */
abstract class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            apply(plugin = "waivera.android.library")
            apply(plugin = "waivera.android.library.compose")
            apply(plugin = "waivera.hilt")
            apply(plugin = "waivera.android.screenshot")

            dependencies {
                "implementation"(project(":core:data"))
                "implementation"(project(":core:designsystem"))
                "implementation"(libs.findLibrary("androidx-lifecycle-runtime-compose").get())
                "implementation"(libs.findLibrary("androidx-lifecycle-viewmodel-compose").get())
                "implementation"(libs.findLibrary("androidx-hilt-navigation-compose").get())

                "testImplementation"(project(":core:testing"))
            }
        }
    }
}
