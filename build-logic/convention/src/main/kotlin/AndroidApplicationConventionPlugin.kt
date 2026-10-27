import com.android.build.api.dsl.ApplicationExtension
import com.waivera.buildlogic.WaiveraSdk
import com.waivera.buildlogic.configureCodeQuality
import com.waivera.buildlogic.configureKotlinAndroid
import com.waivera.buildlogic.configureLint
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure

abstract class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            apply(plugin = "com.android.application")

            extensions.configure<ApplicationExtension> {
                configureKotlinAndroid(this)
                defaultConfig.targetSdk = WaiveraSdk.TARGET
                testOptions.animationsDisabled = true
                configureLint(lint)
            }
            configureCodeQuality()
        }
    }
}
