import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.sqldelight)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.kover)
}

/**
 * What the tests reach, measured - as a report to read, not a gate to pass.
 *
 * The JVM and Android targets are instrumented, which is Kover's own limit:
 * what `iosTest` and a device prove does not appear here at all. So the number
 * is honest about what those two runs reach and silent about the rest, and
 * treating it as a threshold to defend would mean writing tests for the
 * coverage rather than for the code.
 *
 * The verification below is a floor rather than a target. It reads the layers
 * where a number means something - a use case either handles a case or does not
 * - and leaves out the ones where it does not: a composable's coverage is a
 * function of how many Compose UI suites happened to run on that machine, and
 * the generated database and resource classes were never anybody's to test.
 *
 * Eighty against the eighty-six those layers stand at today. Far enough below to
 * survive a refactor that takes tests out with the code it covered, and near
 * enough to notice a feature arriving with none. A floor set at the current
 * number fails on the next commit; one set at half of it says nothing.
 *
 * Eighty-six rather than the ninety-three the common layers alone reach, because
 * `androidMain` is in the denominator: the secret store, the folder scanner and
 * the page content reader are three of the least-tested files in the project and
 * all three are Android's. That is a finding rather than noise, which is why the
 * number is left aggregated instead of measured per target.
 *
 * `koverVerify` joins `check`, which already runs both of those suites. It is
 * not in the CI jobs: CI runs `jvmCommonTest` rather than `jvmTest` for the
 * reasons in TESTING.md, and a coverage number over part of the suite would be
 * worse than none.
 */
kover {
    reports {
        filters {
            excludes {
                packages(
                    "io.lukia.*.presentation",
                    "io.lukia.*.presentation.*",
                    "io.lukia.shared.presentation.*",
                    "io.lukia.navigation",
                    "io.lukia.di",
                    "io.lukia.core.infrastructure.db",
                    "io.lukia.resources",
                )
                annotatedBy("androidx.compose.runtime.Composable")
            }
        }

        verify {
            rule("The layers a number means something about") {
                bound {
                    minValue = 80
                    coverageUnits = kotlinx.kover.gradle.plugin.dsl.CoverageUnit.LINE
                }
            }
        }
    }
}

// See `compose-stability.conf`: value types the compiler cannot prove immutable,
// so that a grid of book covers can skip the cards that have not changed.
composeCompiler {
    stabilityConfigurationFiles.add(
        rootProject.layout.projectDirectory.file("compose-stability.conf"),
    )
}

/**
 * The generated `Res` class the app reads its words out of.
 *
 * Internal, because a feature's public surface is its screen and its navigation
 * entry - see CODING_STYLE.md. Nothing outside `shared` asks for a string, and a
 * `Res` on the compile classpath of every app module is an invitation to.
 */
compose.resources {
    publicResClass = false
    packageOfResClass = "io.lukia.resources"
}

sqldelight {
    databases {
        create("LukiaDatabase") {
            packageName.set("io.lukia.core.infrastructure.db")
        }
    }
}

/**
 * The app's version, written into common code so a diagnostic report can name it.
 *
 * Generated rather than typed out beside the rest of the source: a constant that
 * has to be kept in step with `gradle.properties` by hand is one that drifts,
 * and a report naming the wrong version is worse than one naming none.
 */
val lukiaVersion = providers.gradleProperty("lukia.version")
val generatedVersionDir = layout.buildDirectory.dir("generated/version")

val generateLukiaVersion = tasks.register("generateLukiaVersion") {
    description = "Writes the app version from gradle.properties into common source."

    inputs.property("version", lukiaVersion)
    outputs.dir(generatedVersionDir)

    val version = lukiaVersion
    val target = generatedVersionDir

    doLast {
        val file = target.get().file("io/lukia/LukiaVersion.kt").asFile

        file.parentFile.mkdirs()
        file.writeText(
            """
            package io.lukia

            /** Generated from `lukia.version` in gradle.properties. Do not edit. */
            internal const val LukiaVersion: String = "${version.get()}"
            """.trimIndent() + "\n",
        )
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
            // FileProvider, which is how a book handed to the app is addressed
            // once copied - see AndroidIncomingDocumentStore. Declared rather
            // than taken transitively from documentfile, since it is now used
            // directly.
            implementation(libs.androidx.core)
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
        commonMain {
            kotlin.srcDir(generateLukiaVersion)
        }

        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            // Common-code back/escape handling; ships separately from compose.ui.
            implementation(libs.compose.ui.backhandler)
            implementation(libs.compose.components.resources)
            // The multiplatform `@Preview`, so the design system's components can
            // be previewed from common code rather than only through the Android
            // app that happens to depend on them.
            implementation(libs.compose.uiToolingPreview)
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
            // Reading time is bucketed into the reader's own days, which needs
            // a calendar and a time zone - neither of which the standard
            // library's clock has.
            implementation(libs.kotlinx.datetime)
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
        /**
         * The Android tests that want nothing of a device but a `Context`.
         *
         * A device test costs a booted emulator, so CI has never run one - see
         * TESTING.md. Robolectric answers the Android framework on the host
         * instead, which is enough for a ContentProvider or a file under a cache
         * directory, and is not enough for a CursorWindow's size limit or
         * `BitmapFactory`'s idea of what is not a picture. Those stay next door
         * in `androidDeviceTest`, where a stub would pass them for the wrong
         * reason.
         */
        named("androidHostTest") {
            dependencies {
                implementation(libs.robolectric)
                implementation(libs.androidx.test.ext.junit)
            }
        }

        named("androidDeviceTest") {
            dependencies {
                implementation(libs.androidx.test.runner)
            }
        }
    }
}

/**
 * The fully qualified names of the test classes declared in `src/commonTest`.
 *
 * There is nothing compiled to read this from: the JVM target compiles
 * commonTest and jvmTest into one output, so the source tree is the only thing
 * that still knows which class came from where. A ValueSource rather than a
 * plain scan, so the configuration cache tracks these files and notices a test
 * that has just been written.
 */
abstract class CommonTestClasses : ValueSource<List<String>, CommonTestClasses.Parameters> {

    interface Parameters : ValueSourceParameters {
        val sourceDirectory: DirectoryProperty
    }

    override fun obtain(): List<String> {
        val directory = parameters.sourceDirectory.get().asFile
        if (!directory.isDirectory) return emptyList()

        val packageLine = Regex("""^\s*package\s+([\w.]+)""", RegexOption.MULTILINE)
        val classLine = Regex("""^\s*(?:internal\s+|abstract\s+|open\s+)*class\s+(\w+)""", RegexOption.MULTILINE)

        return directory.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file ->
                val text = file.readText()
                val packageName = packageLine.find(text)?.groupValues?.get(1)
                classLine.findAll(text).map { match ->
                    val className = match.groupValues[1]
                    if (packageName == null) className else "$packageName.$className"
                }
            }
            .toList()
    }
}

private val commonTestClasses = providers.of(CommonTestClasses::class) {
    parameters.sourceDirectory.set(layout.projectDirectory.dir("src/commonTest"))
}

private val jvmTestTask = tasks.named<Test>("jvmTest")

/**
 * The Kotlin sources are an input to `jvmTest`, because two of its tests read
 * them as text rather than through the classpath: `ArchitectureTest` reads the
 * package and import lines of every main source set, and
 * `PlatformModuleParityTest` reads what each platform's DI module binds.
 *
 * Gradle cannot see that from a classpath it does not appear on. Without this,
 * a change to `iosMain` alone leaves both tests reported as up to date - and a
 * check that does not run when the thing it checks changes is not a check.
 */
jvmTestTask.configure {
    inputs.files(fileTree("src") { include("**/*.kt") })
        .withPropertyName("kotlinSourcesReadAsText")
        .withPathSensitivity(PathSensitivity.RELATIVE)
}

/**
 * The common tests alone, which is what CI runs.
 *
 * The platform half of `jvmTest` is 659 tests against 814 common ones, and 30 of
 * the 31 seconds - almost all of it the 40 Compose UI suites, which want a
 * display and are the ones most likely to fail on a CI runner for reasons that
 * have nothing to do with the change under test. They are run locally instead;
 * see TESTING.md.
 *
 * Classpath and class directories come from `jvmTest` rather than being rebuilt
 * here, so the two cannot drift into running the same code differently.
 */
tasks.register<Test>("jvmCommonTest") {
    group = "verification"
    description = "Runs the common tests on the JVM, leaving the platform ones out."

    val jvmTest = jvmTestTask.get()
    testClassesDirs = jvmTest.testClassesDirs
    classpath = jvmTest.classpath

    filter {
        // A pattern that matches nothing means a test silently stopped running.
        isFailOnNoMatchingTests = true
        commonTestClasses.get().forEach { includeTestsMatching(it) }
    }
}
