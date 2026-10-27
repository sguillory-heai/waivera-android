package com.waivera.buildlogic

import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure

/**
 * ktlint for formatting and detekt for code smells on every Kotlin module.
 * Both run in CI through `./gradlew ktlintCheck detekt`.
 */
internal fun Project.configureCodeQuality() {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    apply(plugin = "io.gitlab.arturbosch.detekt")

    configure<DetektExtension> {
        buildUponDefaultConfig = true
        config.setFrom(rootProject.file("config/detekt/detekt.yml"))
    }

    // detekt 1.23 is compiled against Kotlin 2.0 and fails if Gradle upgrades its
    // Kotlin dependencies to the project's version. Pin them for detekt only.
    configurations.matching { it.name == "detekt" }.configureEach {
        resolutionStrategy.eachDependency {
            if (requested.group == "org.jetbrains.kotlin") {
                useVersion("2.0.21")
            }
        }
    }
}
