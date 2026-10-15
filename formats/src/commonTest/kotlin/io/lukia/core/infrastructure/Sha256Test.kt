package io.lukia.core.infrastructure

import kotlin.test.Test
import kotlin.test.assertEquals

class Sha256Test {

    @Test
    fun theStandardTestVectors() {
        // FIPS 180-4's examples, and the empty message.
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", hash(""))
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", hash("abc"))
        assertEquals(
            "248d6a61d20638b8e5c026930c3e6039a33ce45964ff2167f6ecedd419db06c1",
            hash("abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq"),
        )
    }

    @Test
    fun aMillionCharactersFedAPieceAtATime() {
        val hash = Sha256()
        val piece = ByteArray(1000) { 'a'.code.toByte() }
        repeat(1000) { hash.update(piece) }

        assertEquals("cdc76e5c9914fb9281a1c7e284d73e67f1809a48a497200e046d39ccc7112cd0", hash.digest().toHex())
    }

    @Test
    fun aSourceIsHashedTheSameWhateverWindowItIsReadIn() {
        val bytes = ByteArray(10_000) { (it * 31).toByte() }
        val whole = Sha256().apply { update(bytes) }.digest().toHex()

        for (window in listOf(1, 63, 64, 65, 4096, 1 shl 20)) {
            assertEquals(whole, ByteArraySource(bytes).sha256Hex(window), "window $window")
        }
    }

    private fun hash(text: String) = Sha256().apply { update(text.encodeToByteArray()) }.digest().toHex()
}
