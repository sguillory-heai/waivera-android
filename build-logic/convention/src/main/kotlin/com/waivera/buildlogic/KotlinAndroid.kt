package com.waivera.buildlogic

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.kotlin.dsl.assign
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

internal object WaiveraSdk {
    const val COMPILE = 37
    const val TARGET = 37
    const val MIN = 26
}

internal fun Project.configureKotlinAndroid(commonExtension: CommonExtension) {
    commonExtension.apply {
        compileSdk = WaiveraSdk.COMPILE
        defaultConfig.apply {
            minSdk = WaiveraSdk.MIN
        }
        compileOptions.apply {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
            // java.time on API 26+ is native, but desugaring keeps newer APIs available.
            isCoreLibraryDesugaringEnabled = true
        }
    }

    configure<KotlinAndroidProjectExtension> {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_17
            allWarningsAsErrors = providers.gradleProperty("warningsAsErrors").map(String::toBoolean).orElse(false)
        }
    }

    dependencies {
        "coreLibraryDesugaring"(libs.findLibrary("android-desugarJdkLibs").get())
        "testImplementation"(libs.findLibrary("junit").get())
        "testImplementation"(libs.findLibrary("kotlin-test").get())
    }
}
