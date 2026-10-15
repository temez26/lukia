package io.lukia.core.infrastructure.pdf

// The lexical bytes PDF syntax is built from. Named once here rather than
// written as literals at each use, so that the classification below and the
// parser cannot drift apart about what a delimiter is.

const val NUL: Byte = 0x00
const val TAB: Byte = 0x09
const val LINE_FEED: Byte = 0x0A
const val FORM_FEED: Byte = 0x0C
const val CARRIAGE_RETURN: Byte = 0x0D
const val SPACE: Byte = 0x20
const val HASH: Byte = 0x23
const val PERCENT: Byte = 0x25
const val OPEN_PAREN: Byte = 0x28
const val CLOSE_PAREN: Byte = 0x29
const val PLUS: Byte = 0x2B
const val MINUS: Byte = 0x2D
const val PERIOD: Byte = 0x2E
const val SLASH: Byte = 0x2F
const val ZERO: Byte = 0x30
const val NINE: Byte = 0x39
const val LESS_THAN: Byte = 0x3C
const val GREATER_THAN: Byte = 0x3E
const val UPPER_A: Byte = 0x41
const val UPPER_F: Byte = 0x46
const val OPEN_BRACKET: Byte = 0x5B
const val BACKSLASH: Byte = 0x5C
const val CLOSE_BRACKET: Byte = 0x5D
const val LOWER_A: Byte = 0x61
const val LOWER_F: Byte = 0x66
const val OPEN_BRACE: Byte = 0x7B
const val CLOSE_BRACE: Byte = 0x7D

fun isWhitespace(byte: Byte): Boolean =
    byte == NUL ||
        byte == TAB ||
        byte == LINE_FEED ||
        byte == FORM_FEED ||
        byte == CARRIAGE_RETURN ||
        byte == SPACE

fun isDelimiter(byte: Byte): Boolean =
    byte == OPEN_PAREN ||
        byte == CLOSE_PAREN ||
        byte == LESS_THAN ||
        byte == GREATER_THAN ||
        byte == OPEN_BRACKET ||
        byte == CLOSE_BRACKET ||
        byte == OPEN_BRACE ||
        byte == CLOSE_BRACE ||
        byte == SLASH ||
        byte == PERCENT

fun hexDigit(byte: Byte): Int? = when (byte) {
    in ZERO..NINE -> byte - ZERO
    in UPPER_A..UPPER_F -> byte - UPPER_A + 10
    in LOWER_A..LOWER_F -> byte - LOWER_A + 10
    else -> null
}

fun hexValue(high: Byte, low: Byte): Int? {
    val highDigit = hexDigit(high) ?: return null
    val lowDigit = hexDigit(low) ?: return null
    return (highDigit shl 4) or lowDigit
}

/** Index of the first occurrence of [sequence] at or after [from], or null. */
fun ByteArray.indexOfSequence(sequence: ByteArray, from: Int): Int? {
    if (sequence.isEmpty() || sequence.size > size) return null

    outer@ for (start in from..size - sequence.size) {
        for (index in sequence.indices) {
            if (this[start + index] != sequence[index]) continue@outer
        }
        return start
    }

    return null
}

inline fun ByteArray.forEachSequence(sequence: ByteArray, action: (Int) -> Unit) {
    var from = 0

    while (true) {
        val index = indexOfSequence(sequence, from) ?: return
        action(index)
        from = index + 1
    }
}
