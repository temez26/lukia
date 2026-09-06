package io.lukia.core.infrastructure

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Bytes on and off the disk, on the desktop - and the same rules the iOS test of
 * this name pins.
 *
 * Written because the two had drifted on the one thing that matters here. What
 * goes through these is the converted text of a book, which is a container that
 * means nothing until it is complete, and iOS wrote it through a scratch file
 * while the JVM and Android wrote straight over the old one. A write cut short
 * there left a truncated file under the name the reader's library points at.
 */
class FileBytesTest {

    private val directory: File = createTempDirectory("lukia-file-bytes").toFile()

    @AfterTest
    fun tidy() = directory.deleteRecursively().let { }

    @Test
    fun `what is written is what comes back`() {
        val path = pathIn("book.txt")
        val bytes = ByteArray(2_048) { (it * 31).toByte() }

        writeFileBytes(path, bytes)

        assertContentEquals(bytes, readFileBytes(path))
    }

    @Test
    fun `writing again replaces what was there`() {
        val path = pathIn("book.txt")

        writeFileBytes(path, ByteArray(4_096) { 1 })
        writeFileBytes(path, byteArrayOf(7, 7, 7))

        assertContentEquals(byteArrayOf(7, 7, 7), readFileBytes(path))
        assertEquals(3L, fileSizeBytes(path))
    }

    @Test
    fun `an empty file is a file with nothing in it`() {
        val path = pathIn("book.txt")

        writeFileBytes(path, ByteArray(0))

        assertEquals(0L, fileSizeBytes(path))
        assertContentEquals(ByteArray(0), readFileBytes(path))
    }

    /**
     * The scratch file the write goes through is the whole point of it, and a
     * reader who went looking would find it beside their books - so it is gone
     * by the time the write is finished.
     */
    @Test
    fun `nothing is left beside the file it wrote`() {
        writeFileBytes(pathIn("book.txt"), ByteArray(64))

        assertContentEquals(listOf("book.txt"), directory.list()?.sorted())
    }

    @Test
    fun `a file that is not there has no size`() {
        assertEquals(null, fileSizeBytes(pathIn("missing.txt")))
    }

    @Test
    fun `a file that is not there cannot be read`() {
        assertTrue(runCatching { readFileBytes(pathIn("missing.txt")) }.isFailure)
    }

    private fun pathIn(name: String): String = File(directory, name).absolutePath
}
