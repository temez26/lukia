import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
}

/**
 * Reading what a book file says about itself - its format, title, authors and
 * cover - without a UI, so the app's import and the sync server read a file the
 * same way. Split out of `:shared` for the server; see
 * docs/specs/sync-server.md.
 *
 * Holds the container readers (ZIP, EPUB, PDF objects, XML) the reader also
 * builds on, and the two small types those readers report through:
 * `Diagnostics` and the document types they produce.
 */
kotlin {
    iosArm64()
    iosSimulatorArm64()
    jvm()

    android {
        namespace = "io.lukia.formats"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
        withHostTest {}
    }

    sourceSets {
        jvmMain.dependencies {
            // Draws a PDF's first page as its cover - see JvmDocumentSource.
            implementation(libs.pdfbox)
        }
        commonTest.dependencies {
            implementation(project(":formats-testing"))
            implementation(libs.kotlin.test)
        }
    }
}
