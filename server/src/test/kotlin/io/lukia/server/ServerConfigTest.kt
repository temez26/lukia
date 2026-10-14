package io.lukia.server

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ServerConfigTest {

    @Test
    fun `an empty environment runs on the defaults`() {
        assertEquals(ServerConfig("Lukia", 8080, File("data")), ServerConfig.fromEnvironment(emptyMap()))
    }

    @Test
    fun `the environment overrides each default`() {
        val config = ServerConfig.fromEnvironment(
            mapOf("LUKIA_SERVER_NAME" to "Home", "LUKIA_PORT" to "9000", "LUKIA_DATA_DIR" to "/data"),
        )

        assertEquals(ServerConfig("Home", 9000, File("/data")), config)
    }

    @Test
    fun `a port that is not a port refuses to start`() {
        assertFailsWith<IllegalArgumentException> { ServerConfig.fromEnvironment(mapOf("LUKIA_PORT" to "eighty")) }
        assertFailsWith<IllegalArgumentException> { ServerConfig.fromEnvironment(mapOf("LUKIA_PORT" to "70000")) }
    }
}
