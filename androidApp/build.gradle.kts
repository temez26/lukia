import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.baselineProfile)
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}
dependencies {
    implementation(project(":shared"))

    // Ktor is built against Java 8 APIs that Android only gained in API 26.
    // Desugaring back-fills them, which is what lets the app keep its minimum
    // of API 23 while translating over the network.
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    implementation(libs.androidx.activity.compose)

    // Only for taking the system splash screen out of the way of the app's
    // own opening - see MainActivity.
    implementation(libs.androidx.splashscreen)

    implementation(libs.compose.uiToolingPreview)
    debugImplementation(libs.compose.uiTooling)

    // Puts the generated profile in place on the devices whose installer does
    // not do it for us - see the :baselineprofile module.
    implementation(libs.androidx.profileinstaller)

    baselineProfile(project(":baselineprofile"))
}

/**
 * What the generated profiles are used for here, as opposed to how they are
 * made - see the :baselineprofile module for that.
 *
 * The baseline profile is installed and compiled without any of this. The
 * startup one does nothing unless this is asked for: it is read by R8, which
 * puts the classes a launch touches into the primary dex file in the order it
 * touches them, so opening reads a run of pages rather than reaching across
 * every dex file. R8 already runs on the release build.
 */
baselineProfile {
    dexLayoutOptimization = true
}

/**
 * Where the release keystore is, when there is one.
 *
 * Absent for everyone but the release workflow, and the build stays usable
 * without it: a local `assembleRelease` produces an unsigned APK rather than
 * failing on a secret the person running it was never meant to have.
 */
val keystorePath = providers.environmentVariable("ANDROID_KEYSTORE_PATH")

android {
    namespace = "io.lukia"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "io.lukia"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        // Both from gradle.properties, which is the one place either changes.
        versionCode = providers.gradleProperty("lukia.versionCode").get().toInt()
        versionName = providers.gradleProperty("lukia.version").get()
    }

    signingConfigs {
        create("release") {
            storeFile = keystorePath.orNull?.let(::file)
            storePassword = providers.environmentVariable("ANDROID_KEYSTORE_PASSWORD").orNull
            keyAlias = providers.environmentVariable("ANDROID_KEY_ALIAS").orNull
            keyPassword = providers.environmentVariable("ANDROID_KEY_PASSWORD").orNull
        }
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release").takeIf { keystorePath.isPresent }
            isMinifyEnabled = true
            // pdfbox-android reads its fonts, CMaps and glyph lists out of assets
            // at runtime - see PDFBoxResourceLoader.init in LukiaApplication - and
            // resource shrinking cannot see a lookup by name.
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        isCoreLibraryDesugaringEnabled = true
    }
    buildFeatures {
        compose = true
    }
}
