package io.lukia.features.reader.infrastructure.text

import kotlin.test.Test
import kotlin.test.assertEquals

class TextDecodingTest {

    @Test
    fun readsUtf8() {
        assertEquals("café — ok", decodeText("café — ok".encodeToByteArray()))
    }

    /** A mark at the front is an encoding declaration, not the first character. */
    @Test
    fun stripsAUtf8ByteOrderMark() {
        val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) +
            "text".encodeToByteArray()

        assertEquals("text", decodeText(bytes))
    }

    @Test
    fun readsUtf16InBothByteOrders() {
        val big = byteArrayOf(0xFE.toByte(), 0xFF.toByte(), 0x00, 0x68, 0x00, 0x69)
        val little = byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0x68, 0x00, 0x69, 0x00)

        assertEquals("hi", decodeText(big))
        assertEquals("hi", decodeText(little))
    }

    /**
     * The bytes Windows-1252 disagrees with everything else about are exactly
     * the ones that fill ordinary prose - curly quotes, dashes, ellipses - so a
     * text file written on Windows and read as UTF-8 is visibly wrong.
     */
    @Test
    fun fallsBackToWindows1252ForBytesThatAreNotUtf8() {
        // "It's - a dash..." with a curly apostrophe, an em dash and an ellipsis.
        val bytes = byteArrayOf(
            'I'.code.toByte(), 't'.code.toByte(), 0x92.toByte(), 's'.code.toByte(),
            ' '.code.toByte(), 0x97.toByte(), ' '.code.toByte(),
            'a'.code.toByte(), 0x85.toByte(),
        )

        assertEquals("It’s — a…", decodeText(bytes))
    }

    @Test
    fun anEmptyFileIsAnEmptyString() {
        assertEquals("", decodeText(ByteArray(0)))
    }
}
