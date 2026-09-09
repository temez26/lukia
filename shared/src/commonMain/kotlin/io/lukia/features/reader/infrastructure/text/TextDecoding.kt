package io.lukia.features.reader.infrastructure.text

import io.lukia.core.domain.diagnostics.DiagnosticArea
import io.lukia.core.domain.diagnostics.Diagnostics
import io.lukia.core.domain.diagnostics.warn

/**
 * Bytes as text, decoded the way the file says or - failing that - the way it
 * most likely was.
 *
 * UTF-8 first, because everything made this century is UTF-8 and the encoding is
 * self-checking: bytes that are not valid UTF-8 overwhelmingly are not UTF-8. A
 * file that fails is read as Windows-1252, which is what an unlabelled plain
 * text file written on Windows actually is - and the choice is visible, since
 * the bytes it disagrees with Latin-1 about are exactly the curly quotes, dashes
 * and ellipses that fill ordinary prose.
 *
 * Certainty is impossible: nothing in a `.txt` says what it is. Guessing wrong
 * turns punctuation into noise; refusing to guess turns the whole file into it.
 *
 * Line endings are settled here too, so no reader above has to ask which machine
 * a file came from. Each was undoing it separately - three chances to forget,
 * and the one that forgot showed a book whose every paragraph ended in a
 * character nobody can see.
 */
internal fun decodeText(
    bytes: ByteArray,
    diagnostics: Diagnostics = Diagnostics.None,
): String = decoded(bytes, diagnostics).withUnixLineEndings()

private fun String.withUnixLineEndings(): String =
    replace("\r\n", "\n").replace('\r', '\n')

private fun decoded(bytes: ByteArray, diagnostics: Diagnostics): String {
    utf16(bytes)?.let { return it }

    val withoutBom = if (bytes.startsWithUtf8Bom()) bytes.copyOfRange(3, bytes.size) else bytes

    return runCatching { withoutBom.decodeToString(throwOnInvalidSequence = true) }
        .getOrElse {
            // The one decision in this file that is a guess, and the one thing
            // a reader can see went wrong without being able to say why: a book
            // full of replacement characters looks like a broken app rather
            // than like a file that never said what encoding it was.
            diagnostics.warn(
                area = DiagnosticArea.Reading,
                event = "text-decode-fallback",
                detail = "windows-1252, ${bytes.size} bytes",
                cause = it,
            )

            withoutBom.decodeWindows1252()
        }
}

private fun ByteArray.startsWithUtf8Bom(): Boolean =
    size >= 3 && this[0] == 0xEF.toByte() && this[1] == 0xBB.toByte() && this[2] == 0xBF.toByte()

/**
 * UTF-16, but only when the file opens with a byte order mark saying so.
 *
 * Never guessed at without one. UTF-16 text is half zero bytes, so mistaking
 * UTF-8 for it would be unmistakable - but the reverse is just as true, and a
 * mark is the only honest evidence either way.
 */
private fun utf16(bytes: ByteArray): String? {
    if (bytes.size < 2) return null

    val first = bytes[0].toInt() and 0xFF
    val second = bytes[1].toInt() and 0xFF

    val bigEndian = when {
        first == 0xFE && second == 0xFF -> true
        first == 0xFF && second == 0xFE -> false
        else -> return null
    }

    val chars = CharArray((bytes.size - 2) / 2)

    chars.indices.forEach { index ->
        val high = bytes[2 + index * 2].toInt() and 0xFF
        val low = bytes[3 + index * 2].toInt() and 0xFF

        chars[index] = (if (bigEndian) (high shl 8) or low else (low shl 8) or high).toChar()
    }

    return chars.concatToString()
}

/**
 * Windows-1252 is Latin-1 except for the range 0x80-0x9F, where Latin-1 has
 * unprintable control codes and Windows-1252 has the punctuation a word
 * processor produces - which is precisely why a file that is one gets read as
 * the other and comes out full of replacement characters.
 *
 * Written as escapes rather than the characters themselves: the table is correct
 * only if it is exactly thirty-two entries with each in its own place, which the
 * eye cannot check on a line of curly quotes. The five positions Windows-1252
 * leaves undefined keep the byte's own code point, as Latin-1 would.
 */
private val Windows1252HighRange: CharArray = intArrayOf(
    0x20AC, 0x0081, 0x201A, 0x0192, 0x201E, 0x2026, 0x2020, 0x2021, // 0x80-0x87
    0x02C6, 0x2030, 0x0160, 0x2039, 0x0152, 0x008D, 0x017D, 0x008F, // 0x88-0x8F
    0x0090, 0x2018, 0x2019, 0x201C, 0x201D, 0x2022, 0x2013, 0x2014, // 0x90-0x97
    0x02DC, 0x2122, 0x0161, 0x203A, 0x0153, 0x009D, 0x017E, 0x0178, // 0x98-0x9F
).map { it.toChar() }.toCharArray()

private fun ByteArray.decodeWindows1252(): String {
    val chars = CharArray(size)

    indices.forEach { index ->
        val byte = this[index].toInt() and 0xFF

        chars[index] = if (byte in 0x80..0x9F) Windows1252HighRange[byte - 0x80] else byte.toChar()
    }

    return chars.concatToString()
}
