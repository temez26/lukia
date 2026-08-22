package io.lukia.core.infrastructure.archive

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Inflation on iOS, against streams compressed somewhere else.
 *
 * Kotlin/Native has no `java.util.zip`, so this half of reading an EPUB is
 * Apple's own zlib reached through cinterop - a hand-built `z_stream` and a
 * window size that says "no header". Round-tripping it against itself would
 * prove only that it agrees with itself, so the fixtures below were compressed
 * by a different implementation and are checked against the text that went in.
 */
class RawInflateTest {

    @Test
    fun inflatesAStreamAnotherImplementationCompressed() {
        val inflated = inflateRaw(RAW_DEFLATE, COMPRESSED_TEXT.length)

        assertEquals(COMPRESSED_TEXT, inflated.decodeToString())
    }

    /**
     * A ZIP entry states its decoded size, but a PDF stream does not - so the
     * size is a ceiling as often as it is a promise, and a stream that ends
     * before it comes back at the length it really was.
     */
    @Test
    fun aStreamThatEndsEarlyComesBackAtTheLengthItReallyWas() {
        val inflated = inflateRaw(RAW_DEFLATE, COMPRESSED_TEXT.length + ROOM_TO_SPARE)

        assertEquals(COMPRESSED_TEXT.length, inflated.size)
        assertEquals(COMPRESSED_TEXT, inflated.decodeToString())
    }

    @Test
    fun nothingIsAskedForAndNothingComesBack() {
        assertContentEquals(ByteArray(0), inflateRaw(RAW_DEFLATE, 0))
    }

    @Test
    fun somethingThatIsNotADeflateStreamInflatesToNothing() {
        val rubbish = Random(0).nextBytes(RUBBISH_BYTES)

        assertTrue(inflateRaw(rubbish, COMPRESSED_TEXT.length).isEmpty())
    }

    /** The wrapped form, which is what a PDF's FlateDecode filter produces. */
    @Test
    fun inflatesAZlibStreamThroughItsHeader() {
        val inflated = inflateZlib(ZLIB_STREAM, COMPRESSED_TEXT.length + ROOM_TO_SPARE)

        assertEquals(COMPRESSED_TEXT, inflated?.decodeToString())
    }

    @Test
    fun somethingThatIsNotAStreamAtAllIsRefused() {
        assertNull(inflateZlib("not a stream".encodeToByteArray(), MAX_OUTPUT))
    }

    private companion object {
        val COMPRESSED_TEXT = "Lukia reads books. ".repeat(12)

        /** `Lukia reads books. ` twelve times, deflated with no header. */
        val RAW_DEFLATE = byteArrayOf(
            -13, 41, -51, -50, 76, 84, 40, 74, 77, 76, 41, 86, 72, -54, -49,
            -49, 46, -42, 83, -16, 25, 110, 66, 0,
        )

        /** The same text, deflated inside a zlib header and checksum. */
        val ZLIB_STREAM = byteArrayOf(
            120, -38, -13, 41, -51, -50, 76, 84, 40, 74, 77, 76, 41, 86, 72,
            -54, -49, -49, 46, -42, 83, -16, 25, 110, 66, 0, 12, -58, 80, 77,
        )

        const val ROOM_TO_SPARE = 512
        const val RUBBISH_BYTES = 64
        const val MAX_OUTPUT = 4096
    }
}
