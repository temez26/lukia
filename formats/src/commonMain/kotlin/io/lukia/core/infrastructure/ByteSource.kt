package io.lukia.core.infrastructure

/**
 * Random access to a document's bytes.
 *
 * An interface rather than a [ByteArray] because a book can be hundreds of
 * megabytes and what is wanted out of it is rarely more than a few kilobytes: a
 * PDF's outline, a ZIP's central directory, one cover image out of an EPUB. The
 * whole file loaded to find any of those is the largest allocation this app ever
 * makes, and it makes it for every book somebody imports.
 *
 * Not tied to any one format. It began as the PDF reader's own and became this
 * when the import needed the same thing of an EPUB - both want a window of a
 * file, and neither wants the file.
 */
interface ByteSource {

    val size: Long

    /**
     * Reads up to [length] bytes from [offset], returning fewer at the end of
     * the file and an empty array past it. Never throws for a range outside the
     * file - a malformed offset is expected input here, not an error.
     */
    fun read(offset: Long, length: Int): ByteArray
}

/** For a document already in memory, and for tests. */
class ByteArraySource(private val bytes: ByteArray) : ByteSource {

    override val size: Long = bytes.size.toLong()

    override fun read(offset: Long, length: Int): ByteArray {
        if (offset < 0 || offset >= bytes.size || length <= 0) return ByteArray(0)

        val start = offset.toInt()
        val end = minOf(bytes.size.toLong(), offset + length).toInt()

        return bytes.copyOfRange(start, end)
    }
}
