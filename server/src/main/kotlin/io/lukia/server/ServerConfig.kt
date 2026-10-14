package io.lukia.server

import java.io.File

/**
 * Everything an operator can set, read from the environment because that is
 * how a container is configured.
 */
data class ServerConfig(
    val name: String,
    val port: Int,
    /** Holds the database and, from slice 3, the book files - the one volume to back up. */
    val dataDirectory: File,
) {
    companion object {
        fun fromEnvironment(environment: Map<String, String> = System.getenv()) = ServerConfig(
            name = environment["LUKIA_SERVER_NAME"]?.takeIf { it.isNotBlank() } ?: "Lukia",
            port = environment["LUKIA_PORT"]?.let(::parsePort) ?: DefaultPort,
            dataDirectory = File(environment["LUKIA_DATA_DIR"]?.takeIf { it.isNotBlank() } ?: "data"),
        )

        /** Refused rather than defaulted: a server quietly listening elsewhere is harder to find than one that won't start. */
        private fun parsePort(value: String): Int = requireNotNull(value.toIntOrNull()?.takeIf { it in 1..MaxPort }) {
            "LUKIA_PORT must be a port number from 1 to $MaxPort, not \"$value\"."
        }

        private const val DefaultPort = 8080
        private const val MaxPort = 65_535
    }
}
