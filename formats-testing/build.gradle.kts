import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
}

/**
 * Minimal ZIP, EPUB and PDF files written in code, for the tests of every
 * module that reads them. A module of its own because Kotlin Multiplatform has
 * no test fixtures one module's tests can share with another's. Never a
 * dependency of anything that ships.
 */
kotlin {
    iosArm64()
    iosSimulatorArm64()
    jvm()

    android {
        namespace = "io.lukia.formats.testing"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }

    sourceSets {
        jvmMain.dependencies {
            // `openedDocument` opens a fixture through the JVM's real document source.
            implementation(project(":formats"))
        }
    }
}
