package io.lukia.features.import.domain

class CoverImage(val bytes: ByteArray) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CoverImage) return false
        return bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int = bytes.contentHashCode()

    override fun toString(): String = "CoverImage(${bytes.size} bytes)"
}
