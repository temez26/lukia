package io.lukia.core.infrastructure

import java.security.MessageDigest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The server names a book with `MessageDigest`, and an iOS device with
 * [PortableSha256]. If the two ever disagreed, that device's books would never
 * be the server's books, so they are held to each other across every length
 * around a block boundary.
 */
class Sha256AgreementTest {

    @Test
    fun agreesWithTheJdkAtEveryLengthAroundTheBlockBoundaries() {
        val random = Random(42)

        for (length in (0..200) + listOf(1000, 4095, 4096, 4097, 100_000)) {
            val bytes = random.nextBytes(length)
            val expected = MessageDigest.getInstance("SHA-256").digest(bytes).toHex()

            assertEquals(expected, PortableSha256().apply { update(bytes) }.digest().toHex(), "length $length")
        }
    }
}
