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
            // One source for all three builds - see `lukia.version` in gradle.properties.
            packageVersion = providers.gradleProperty("lukia.version").get()

            // jlink keeps only the modules it can prove are used, and nothing in the bytecode
            // points at these: the SQLite JDBC driver reaches java.sql through DriverManager,
            // and the others are loaded reflectively. Without them the installed app dies on
            // launch with a JVM error, while `run` - which uses the full JDK - stays happy.
            modules("java.instrument", "java.management", "java.sql", "jdk.unsupported")

            // What the installers offer to open. Together with `main`'s
            // arguments - which is how every desktop hands an application the
            // document it was asked to open - this is what makes double-clicking
            // a book in a file manager land it on Lukia's shelf.
            //
            // The same five formats the Android manifest and the iOS Info.plist
            // list, and offered on the same terms: these are formats Lukia
            // reads, not formats it owns, so nothing here asks to become the
            // system default. Each desktop adds Lukia to the list of
            // applications that can open one and leaves the reader's own choice
            // where it was.
            fileAssociation("application/pdf", "pdf", "PDF document")
            fileAssociation("application/epub+zip", "epub", "EPUB book")
            fileAssociation(
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "docx",
                "Word document",
            )
            fileAssociation("text/plain", "txt", "Plain text")
            fileAssociation("text/markdown", "md", "Markdown text")

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
