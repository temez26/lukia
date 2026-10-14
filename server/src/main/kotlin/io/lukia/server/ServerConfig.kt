package io.lukia.server

import java.io.File

/**
 * Everything an operator can set, read from the environment because that is
 * how a container is configured.
 */
data class ServerConfig(
    val name: String,
    val port: Int,
    /** Holds the database and the book files - the one volume to back up. */
    val dataDirectory: File,
    /** The largest book an upload may be. A scanned PDF is the usual reason to raise it. */
    val maxUploadBytes: Long = DefaultMaxUploadBytes,
) {
    companion object {
        fun fromEnvironment(environment: Map<String, String> = System.getenv()) = ServerConfig(
            name = environment["LUKIA_SERVER_NAME"]?.takeIf { it.isNotBlank() } ?: "Lukia",
            port = environment["LUKIA_PORT"]?.let(::parsePort) ?: DefaultPort,
            dataDirectory = File(environment["LUKIA_DATA_DIR"]?.takeIf { it.isNotBlank() } ?: "data"),
            maxUploadBytes = (environment["LUKIA_MAX_UPLOAD_MB"]?.let(::parseMegabytes) ?: DefaultMaxUploadMegabytes) *
                BytesPerMegabyte,
        )

        /** Refused rather than defaulted: a server quietly listening elsewhere is harder to find than one that won't start. */
        private fun parsePort(value: String): Int = requireNotNull(value.toIntOrNull()?.takeIf { it in 1..MaxPort }) {
            "LUKIA_PORT must be a port number from 1 to $MaxPort, not \"$value\"."
        }

        private fun parseMegabytes(value: String): Long = requireNotNull(value.toLongOrNull()?.takeIf { it > 0 }) {
            "LUKIA_MAX_UPLOAD_MB must be a whole number of megabytes, not \"$value\"."
        }

        private const val DefaultPort = 8080
        private const val MaxPort = 65_535
        private const val DefaultMaxUploadMegabytes = 200L
        private const val BytesPerMegabyte = 1024L * 1024L
        const val DefaultMaxUploadBytes = DefaultMaxUploadMegabytes * BytesPerMegabyte
    }
}
