package io.lukia.features.reader.infrastructure.pdf

/**
 * Random access to a PDF's bytes.
 *
 * An interface rather than a [ByteArray] because a book can be hundreds of
 * megabytes and the outline is a few kilobytes of it: the reader scans in
 * windows and then reads only the objects it needs, instead of holding the whole
 * file in memory to find its chapter list.
 */
internal interface PdfByteSource {

    val size: Long

    /**
     * Reads up to [length] bytes from [offset], returning fewer at the end of
     * the file and an empty array past it. Never throws for a range outside the
     * file - a malformed offset is expected input here, not an error.
     */
    fun read(offset: Long, length: Int): ByteArray
}

/** For a PDF already in memory, and for tests. */
internal class ByteArrayPdfSource(private val bytes: ByteArray) : PdfByteSource {

    override val size: Long = bytes.size.toLong()

    override fun read(offset: Long, length: Int): ByteArray {
        if (offset < 0 || offset >= bytes.size || length <= 0) return ByteArray(0)

        val start = offset.toInt()
        val end = minOf(bytes.size.toLong(), offset + length).toInt()

        return bytes.copyOfRange(start, end)
    }
}
