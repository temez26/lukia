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

    implementation(libs.compose.uiToolingPreview)
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
            packageName = "io.lukia"
            packageVersion = "1.0.0"

            val icons = project.file("src/main/resources/icons")
            macOS { iconFile.set(icons.resolve("lukia.icns")) }
            windows { iconFile.set(icons.resolve("lukia.ico")) }
            linux { iconFile.set(icons.resolve("lukia.png")) }
        }
    }
}