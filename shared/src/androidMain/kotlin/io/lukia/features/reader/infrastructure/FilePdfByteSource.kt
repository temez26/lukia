package io.lukia.features.reader.infrastructure

import io.lukia.core.infrastructure.ByteSource
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile

/**
 * Reads a PDF's bytes straight off the working copy.
 *
 * Random access rather than a loaded [ByteArray]: a scanned book can be hundreds
 * of megabytes, and the outline reader only ever wants windows of it. Holding
 * the whole file to find a chapter list is exactly the allocation an Android
 * device cannot afford.
 */
internal class FilePdfByteSource(file: File) : ByteSource, AutoCloseable {

    private val handle = RandomAccessFile(file, "r")

    override val size: Long = handle.length()

    override fun read(offset: Long, length: Int): ByteArray {
        if (offset < 0 || offset >= size || length <= 0) return ByteArray(0)

        val count = minOf(length.toLong(), size - offset).toInt()
        val buffer = ByteArray(count)

        // No bytes rather than a failure, because every caller here is looking
        // for something optional inside the file - an outline, a page's text -
        // and answers "not found" to a window it could not read. A book whose
        // working copy has gone away still opens; it opens without its contents
        // list, which is the failure the reader can actually do something about.
        return try {
            handle.seek(offset)
            handle.readFully(buffer)
            buffer
        } catch (ignored: IOException) {
            ByteArray(0)
        }
    }

    override fun close() {
        runCatching { handle.close() }
    }
}
