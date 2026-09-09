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

/**
 * Who the Mac build is signed by, when anybody is.
 *
 * Absent for everyone but the release workflow, and the build stays usable
 * without it - exactly as `ANDROID_KEYSTORE_PATH` leaves a local
 * `assembleRelease` unsigned rather than failing on a secret the person running
 * it was never meant to have. See BUILD.md for what each variable holds, and for
 * what an unsigned DMG can and cannot do.
 */
val macSigningIdentity = providers.environmentVariable("MACOS_SIGNING_IDENTITY")

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

            // What the installers offer to open. With `main`'s arguments - how
            // every desktop hands an application the document it was asked to
            // open - this is what lands a double-clicked book on Lukia's shelf.
            //
            // The same five formats the Android manifest and iOS Info.plist
            // list, on the same terms: formats Lukia reads, not formats it owns,
            // so nothing here asks to become the system default. Each desktop
            // adds Lukia to the list and leaves the reader's choice where it was.
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

                // An unsigned DMG opens on the machine that built it and no
                // other: Gatekeeper refuses anything downloaded without a
                // Developer ID signature, and from Sequoia the reader cannot
                // right-click past it. Signing is what makes the Mac build
                // distributable at all, rather than a hardening step.
                //
                // Off unless the environment says otherwise, so a local
                // `packageDmg` still works without a certificate - the same
                // arrangement as the Android keystore.
                signing {
                    sign.set(macSigningIdentity.isPresent)
                    identity.set(macSigningIdentity)
                    // A CI runner imports the certificate into a keychain of its
                    // own and names it here; a developer's own login keychain is
                    // found without being asked for.
                    keychain.set(providers.environmentVariable("MACOS_SIGNING_KEYCHAIN"))
                }

                // Signing lets the DMG open; notarization stops macOS warning
                // about it first. Only `notarizeDmg` asks Apple's service, so
                // leaving these unset costs `packageDmg` nothing.
                //
                // Notarization needs the hardened runtime, which needs a JVM
                // allowed to write executable memory. The Compose plugin's
                // default-entitlements.plist already grants exactly that -
                // allow-jit, allow-unsigned-executable-memory and
                // disable-library-validation - so no entitlements file is added.
                notarization {
                    appleID.set(providers.environmentVariable("MACOS_NOTARIZATION_APPLE_ID"))
                    password.set(providers.environmentVariable("MACOS_NOTARIZATION_PASSWORD"))
                    teamID.set(providers.environmentVariable("MACOS_NOTARIZATION_TEAM_ID"))
                }
            }

            windows {
                iconFile.set(icons.resolve("lukia.ico"))

                // What Windows knows one Lukia by across every version. Without
                // it the plugin passes no --win-upgrade-uuid, jpackage invents
                // one per build, and each MSI installs beside the last instead
                // of replacing it - so a reader who updates twice has three
                // Lukias in Add or Remove Programs.
                //
                // **This must never change for the life of the app.** Changing
                // it does not upgrade anybody; it makes every installed copy a
                // different product from this one, permanently.
                upgradeUuid = "DE25ED1A-8B48-420B-B720-5CB5C2E27FEA"
            }

            linux {
                iconFile.set(icons.resolve("lukia.png"))
                // dpkg refuses a package name with a capital in it, so the deb
                // keeps the lowercase id while the app itself is still Lukia.
                packageName = "lukia"
            }
        }
    }
}
