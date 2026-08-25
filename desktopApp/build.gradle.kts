import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

dependencies {
    implementation(project(":shared"))

    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutinesSwing)

    implementation(libs.compose.components.resources)
    implementation(libs.koin.core)
    implementation(libs.koin.compose)
    implementation(libs.koin.compose.viewmodel)
}

compose.desktop {
    application {
        mainClass = "io.lukia.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            // What the installed app is called: the name in the Start menu, the
            // Applications folder and the window list. The reverse-DNS id belongs
            // to the JVM package, not to the thing a reader launches.
            packageName = "Lukia"
            packageVersion = "1.0.0"

            // jlink keeps only the modules it can prove are used, and nothing in the bytecode
            // points at these: the SQLite JDBC driver reaches java.sql through DriverManager,
            // and the others are loaded reflectively. Without them the installed app dies on
            // launch with a JVM error, while `run` - which uses the full JDK - stays happy.
            modules("java.instrument", "java.management", "java.sql", "jdk.unsupported")

            val icons = project.file("src/main/resources/icons")

            macOS {
                iconFile.set(icons.resolve("lukia.icns"))
                // Defaults to packageName, which is now a display name rather
                // than an identifier - and macOS wants reverse-DNS here.
                bundleID = "io.lukia"
            }

            windows { iconFile.set(icons.resolve("lukia.ico")) }

            linux {
                iconFile.set(icons.resolve("lukia.png"))
                // dpkg refuses a package name with a capital in it, so the deb
                // keeps the lowercase id while the app itself is still Lukia.
                packageName = "lukia"
            }
        }
    }
}
