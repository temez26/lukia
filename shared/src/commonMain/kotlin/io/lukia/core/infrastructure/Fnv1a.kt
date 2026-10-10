package io.lukia.core.infrastructure

/**
 * FNV-1a, as a lowercase 16-digit hex string.
 *
 * A non-cryptographic hash is right here - these are file names, not a security
 * boundary - but [String.hashCode] is only 32 bits, a coin flip away from a
 * collision across a library of any size.
 *
 * Shared so the two things naming files by hash - a working copy and the app's
 * own copy of a handed-over document - cannot hash the same bytes differently.
 */
internal fun fnv1aHex(bytes: ByteArray): String {
    var hash = FNV_OFFSET_BASIS

    bytes.forEach { byte ->
        hash = hash xor byte.toUByte().toULong()
        hash *= FNV_PRIME
    }

    return hash.toHexString()
}

internal fun fnv1aHex(value: String): String = fnv1aHex(value.encodeToByteArray())

private fun ULong.toHexString(): String {
    val digits = CharArray(HEX_DIGITS)
    var remaining = this

    for (index in HEX_DIGITS - 1 downTo 0) {
        digits[index] = HEX_ALPHABET[(remaining and LOW_NIBBLE).toInt()]
        remaining = remaining shr BITS_PER_HEX_DIGIT
    }

    return digits.concatToString()
}

private const val FNV_OFFSET_BASIS = 0xcbf29ce484222325uL
private const val FNV_PRIME = 0x100000001b3uL

/** One hex digit's worth of bits, as a mask and as a width. */
private const val LOW_NIBBLE = 0xFuL
private const val BITS_PER_HEX_DIGIT = 4

private const val HEX_ALPHABET = "0123456789abcdef"
private const val HEX_DIGITS = 16
