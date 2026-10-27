import com.android.build.api.dsl.LibraryExtension
import com.waivera.buildlogic.WaiveraSdk
import com.waivera.buildlogic.configureCodeQuality
import com.waivera.buildlogic.configureKotlinAndroid
import com.waivera.buildlogic.configureLint
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure

abstract class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            apply(plugin = "com.android.library")

            extensions.configure<LibraryExtension> {
                configureKotlinAndroid(this)
                testOptions.targetSdk = WaiveraSdk.TARGET
                lint.targetSdk = WaiveraSdk.TARGET
                configureLint(lint)
                defaultConfig.testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                testOptions.animationsDisabled = true
                // Resources in :core:designsystem must start with "core_designsystem_".
                resourcePrefix = path.split("""\W""".toRegex()).drop(1).distinct()
                    .joinToString(separator = "_").lowercase() + "_"
            }
            configureCodeQuality()
        }
    }
}
