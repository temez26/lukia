package io.lukia.core.domain.document

/**
 * A book's artwork, as the bytes it was found as.
 *
 * Compared by content rather than by identity, so the same cover read twice out
 * of the database is the same cover. The hash is worked out once and kept: these
 * are hundreds of kilobytes each and they are used as keys, and re-reading every
 * byte of one to look it up would cost more than the lookup saves.
 */
class CoverImage(val bytes: ByteArray) {

    private val hash: Int by lazy { bytes.contentHashCode() }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CoverImage) return false
        return bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int = hash

    override fun toString(): String = "CoverImage(${bytes.size} bytes)"
}
