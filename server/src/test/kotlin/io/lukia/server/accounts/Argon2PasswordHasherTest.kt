package io.lukia.server.accounts

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class Argon2PasswordHasherTest {

    private val hasher = Argon2PasswordHasher()

    @Test
    fun `a password verifies against its own hash and no other password does`() {
        val hash = hasher.hash("correct horse battery")

        assertTrue(hasher.verify("correct horse battery", hash))
        assertFalse(hasher.verify("correct horse battery!", hash))
    }

    @Test
    fun `the same password hashes differently each time`() {
        assertNotEquals(hasher.hash("correct horse battery"), hasher.hash("correct horse battery"))
    }

    @Test
    fun `a hash made at another cost still verifies`() {
        val cheap = Argon2PasswordHasher(memoryKiB = 8, iterations = 1).hash("correct horse battery")

        assertTrue(hasher.verify("correct horse battery", cheap))
    }

    @Test
    fun `writes the standard format`() {
        assertTrue(hasher.hash("x").startsWith("\$argon2id\$v=19\$m=19456,t=2,p=1\$"))
    }

    @Test
    fun `a hash it cannot read fails to verify rather than throwing`() {
        assertFalse(hasher.verify("anything", "not a hash"))
        assertFalse(hasher.verify("anything", "\$argon2i\$v=19\$m=8,t=1,p=1\$AAAA\$AAAA"))
    }
}
