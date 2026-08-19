import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.sqldelight)
    alias(libs.plugins.kotlinSerialization)
}

// See `compose-stability.conf`: value types the compiler cannot prove immutable,
// so that a grid of book covers can skip the cards that have not changed.
composeCompiler {
    stabilityConfigurationFiles.add(
        rootProject.layout.projectDirectory.file("compose-stability.conf"),
    )
}

sqldelight {
    databases {
        create("LukiaDatabase") {
            packageName.set("io.lukia.core.infrastructure.db")
        }
    }
}

kotlin {
    listOf(
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }

    jvm()

    android {
        namespace = "io.lukia.shared"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
        androidResources {
            enable = true
        }
        withHostTest {
            isIncludeAndroidResources = true
        }
        withDeviceTestBuilder {
            sourceSetTreeName = "test"
        }.configure {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
    }

    sourceSets {
        androidMain.dependencies {
            implementation(libs.androidx.documentfile)
            implementation(libs.sqldelight.androidDriver)
            implementation(libs.ktor.client.okhttp)
            // Android's own PdfRenderer draws pages and offers no way to read
            // the text on them below API 35, which is most devices this app
            // supports. This is the same library the desktop reads PDFs with,
            // repackaged for Android.
            implementation(libs.pdfbox.android)
        }
        jvmMain.dependencies {
            implementation(libs.pdfbox)
            implementation(libs.sqldelight.jvmDriver)
            implementation(libs.ktor.client.okhttp)
        }
        iosMain.dependencies {
            implementation(libs.sqldelight.nativeDriver)
            implementation(libs.ktor.client.darwin)
        }
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            // Common-code back/escape handling; ships separately from compose.ui.
            implementation(libs.compose.ui.backhandler)
            implementation(libs.compose.components.resources)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.androidx.navigation.compose)
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.sqldelight.runtime)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.contentNegotiation)
            implementation(libs.ktor.serialization.json)
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            // Lets the provider be tested against the wire format it actually
            // sends and receives, without a network or an account.
            implementation(libs.ktor.client.mock)
        }
        jvmTest.dependencies {
            // The same driver the desktop app uses, pointed at an in-memory
            // database - so what is stored is tested through real SQL rather
            // than through a stand-in for it.
            implementation(libs.sqldelight.jvmDriver)
            // Turning a rendered page into an ImageBitmap goes through Skiko,
            // whose native library only reaches the classpath via the desktop
            // bundle - which the app gets from :desktopApp and tests otherwise
            // do not. `currentOs` resolves it for whichever host is building.
            implementation(compose.desktop.currentOs)
            // Lets the reader's chrome be asserted on as a composed tree - the
            // page indicator and the settings panel are behaviour, not just
            // layout, and nothing else here can see them.
            @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
            implementation(compose.uiTest)
        }
        named("androidDeviceTest") {
            dependencies {
                implementation(libs.androidx.test.runner)
            }
        }
    }
}
