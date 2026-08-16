package io.lukia.core.infrastructure

/**
 * Names the on-disk working copy of a document.
 *
 * A library location is an absolute path, a content:// URI or a file:// URL,
 * none of which survive being used as a file name. The name is derived from a
 * hash of the location instead, which also keeps it stable: reopening a book
 * finds the copy made last time rather than writing a second one.
 *
 * Shared by every platform workspace so that a copy written by one lookup is
 * always found by the next.
 */
internal object WorkingCopyNaming {

    /**
     * The part of the name that identifies the document. Discarding matches on
     * this rather than the full file name, so a copy can be deleted knowing
     * only the location it came from.
     */
    fun prefix(location: String): String = "doc-${hash(location)}"

    fun fileName(location: String, extension: String): String {
        val suffix = extension.filter { it.isLetterOrDigit() }.lowercase().take(MAX_EXTENSION_LENGTH)
        val prefix = prefix(location)

        return if (suffix.isEmpty()) prefix else "$prefix.$suffix"
    }

    /**
     * FNV-1a. A non-cryptographic hash is the right tool here - this is a name,
     * not a security boundary - but [String.hashCode] is only 32 bits, which is
     * a coin flip away from a collision across a library of any size.
     */
    private fun hash(value: String): String {
        var hash = FNV_OFFSET_BASIS

        value.encodeToByteArray().forEach { byte ->
            hash = hash xor byte.toUByte().toULong()
            hash *= FNV_PRIME
        }

        return hash.toHexString()
    }

    private fun ULong.toHexString(): String {
        val digits = CharArray(HEX_DIGITS)
        var remaining = this

        for (index in HEX_DIGITS - 1 downTo 0) {
            digits[index] = HEX_ALPHABET[(remaining and 0xFuL).toInt()]
            remaining = remaining shr 4
        }

        return digits.concatToString()
    }

    private const val FNV_OFFSET_BASIS = 0xcbf29ce484222325uL
    private const val FNV_PRIME = 0x100000001b3uL
    private const val HEX_ALPHABET = "0123456789abcdef"
    private const val HEX_DIGITS = 16
    private const val MAX_EXTENSION_LENGTH = 8
}
