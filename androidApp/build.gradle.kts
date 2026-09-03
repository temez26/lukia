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

android {
    namespace = "io.lukia"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "io.lukia"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        // One source for all three builds - see `lukia.version` in gradle.properties.
        versionName = providers.gradleProperty("lukia.version").get()
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
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
