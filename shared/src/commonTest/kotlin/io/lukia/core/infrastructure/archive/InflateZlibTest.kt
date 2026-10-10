package io.lukia.core.infrastructure.archive

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * A zlib stream decoded under a ceiling, which is how a PDF's streams are read:
 * the file does not say how large one decodes to.
 *
 * The buffer is sized for the stream and widened as it fills, so what is held
 * here is that widening loses nothing - a stream far larger than its first
 * guess still comes back whole, and the ceiling is still a ceiling.
 */
class InflateZlibTest {

    @Test
    fun aStreamManyTimesItsOwnSizeComesBackWhole() {
        val inflated = assertNotNull(inflateZlib(THREE_HUNDRED_THOUSAND_AS, maxOutputSize = 1 shl 25))

        assertEquals(DECODED_SIZE, inflated.size)
        assertTrue(inflated.all { it == 'a'.code.toByte() })
    }

    @Test
    fun aStreamLargerThanTheCeilingStopsAtTheCeiling() {
        val inflated = assertNotNull(inflateZlib(THREE_HUNDRED_THOUSAND_AS, maxOutputSize = 100_000))

        assertEquals(100_000, inflated.size)
    }

    @Test
    fun aCeilingSmallerThanTheFirstGuessIsStillTheCeiling() {
        val inflated = assertNotNull(inflateZlib(THREE_HUNDRED_THOUSAND_AS, maxOutputSize = 1_000))

        assertEquals(1_000, inflated.size)
    }

    private companion object {

        const val DECODED_SIZE = 300_000

        /**
         * `zlib.compress(b"a" * 300000, 9)`: 314 bytes for three hundred
         * thousand, which is nearly a thousand times its own size.
         */
        val THREE_HUNDRED_THOUSAND_AS: ByteArray =
            "78daedc1010d000000c2a0acef5fc21e0e28".hexToByteArray() +
                ByteArray(290) +
                "f831fa9821e5".hexToByteArray()
    }
}
