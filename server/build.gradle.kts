plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.sqldelight)
    application
}

application {
    mainClass = "io.lukia.server.MainKt"
}

dependencies {
    implementation(project(":api"))

    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.server.contentNegotiation)
    implementation(libs.ktor.server.statusPages)
    implementation(libs.ktor.server.callLogging)
    implementation(libs.ktor.serialization.json)
    implementation(libs.sqldelight.jvmDriver)
    implementation(libs.logback.classic)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.ktor.server.testHost)
    testImplementation(libs.ktor.client.contentNegotiation)
}

sqldelight {
    databases {
        create("ServerDatabase") {
            packageName.set("io.lukia.server.db")
        }
    }
}

/**
 * The server's version, from the same `lukia.version` the app reads, so that a
 * server and an app built from one commit report the same number. Written as
 * source for the reason `:shared` does it - see `generateLukiaVersion` there.
 */
val lukiaVersion = providers.gradleProperty("lukia.version")
val generatedVersionDir = layout.buildDirectory.dir("generated/version")

val generateServerVersion = tasks.register("generateServerVersion") {
    description = "Writes the server version from gradle.properties into source."

    inputs.property("version", lukiaVersion)
    outputs.dir(generatedVersionDir)

    val version = lukiaVersion
    val target = generatedVersionDir

    doLast {
        val file = target.get().file("io/lukia/server/ServerVersion.kt").asFile

        file.parentFile.mkdirs()
        file.writeText(
            """
            package io.lukia.server

            /** Generated from `lukia.version` in gradle.properties. Do not edit. */
            internal const val ServerVersion: String = "${version.get()}"
            """.trimIndent() + "\n",
        )
    }
}

kotlin {
    sourceSets.main {
        kotlin.srcDir(generateServerVersion)
    }
}
