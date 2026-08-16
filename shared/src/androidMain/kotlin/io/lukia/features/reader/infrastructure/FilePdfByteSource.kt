package io.lukia.features.reader.infrastructure

import io.lukia.features.reader.infrastructure.pdf.PdfByteSource
import java.io.File
import java.io.RandomAccessFile

/**
 * Reads a PDF's bytes straight off the working copy.
 *
 * Random access rather than a loaded [ByteArray]: a scanned book can be hundreds
 * of megabytes, and the outline reader only ever wants windows of it. Holding
 * the whole file to find a chapter list is exactly the allocation an Android
 * device cannot afford.
 */
internal class FilePdfByteSource(file: File) : PdfByteSource, AutoCloseable {

    private val handle = RandomAccessFile(file, "r")

    override val size: Long = handle.length()

    override fun read(offset: Long, length: Int): ByteArray {
        if (offset < 0 || offset >= size || length <= 0) return ByteArray(0)

        val count = minOf(length.toLong(), size - offset).toInt()
        val buffer = ByteArray(count)

        return try {
            handle.seek(offset)
            handle.readFully(buffer)
            buffer
        } catch (e: java.io.IOException) {
            ByteArray(0)
        }
    }

    override fun close() {
        runCatching { handle.close() }
    }
}
