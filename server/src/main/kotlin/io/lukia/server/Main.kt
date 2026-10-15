package io.lukia.server

import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.lukia.server.db.openServerDatabase

fun main() {
    val config = ServerConfig.fromEnvironment()
    val database = openServerDatabase(config.dataDirectory)

    embeddedServer(CIO, port = config.port) {
        lukiaServer(config, database)
    }.start(wait = true)
}
