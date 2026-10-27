package com.waivera.buildlogic

import com.android.build.api.dsl.Lint

/**
 * Lint warnings fail the build. Dependency-freshness checks are off because
 * Dependabot owns upgrades and those checks make CI depend on the network.
 */
internal fun configureLint(lint: Lint) {
    lint.apply {
        warningsAsErrors = true
        abortOnError = true
        checkDependencies = false
        disable += setOf("GradleDependency", "AndroidGradlePluginVersion", "NewerVersionAvailable")
    }
}
