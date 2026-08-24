package io.lukia.core.infrastructure

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * Bytes on and off the disk, on iOS.
 *
 * Small, and worth having: the converted text of a book is kept through these
 * two, and a size that came back wrong would quietly hand a reader the text of
 * a book they had replaced.
 */
@OptIn(ExperimentalForeignApi::class)
class FileBytesTest {

    private val written = mutableListOf<String>()

    @AfterTest
    fun tidy() {
        written.forEach { NSFileManager.defaultManager.removeItemAtPath(it, null) }
    }

    @Test
    fun whatIsWrittenIsWhatComesBack() {
        val path = temporaryPath()
        val bytes = ByteArray(2_048) { (it * 31).toByte() }

        writeFileBytes(path, bytes)

        assertContentEquals(bytes, readFileBytes(path))
    }

    @Test
    fun writingAgainReplacesWhatWasThere() {
        val path = temporaryPath()

        writeFileBytes(path, ByteArray(4_096) { 1 })
        writeFileBytes(path, byteArrayOf(7, 7, 7))

        assertContentEquals(byteArrayOf(7, 7, 7), readFileBytes(path))
        assertEquals(3L, fileSizeBytes(path))
    }

    @Test
    fun anEmptyFileIsAFileWithNothingInIt() {
        val path = temporaryPath()

        writeFileBytes(path, ByteArray(0))

        assertEquals(0L, fileSizeBytes(path))
        assertContentEquals(ByteArray(0), readFileBytes(path))
    }

    @Test
    fun aFileThatIsNotThereHasNoSize() {
        assertNull(fileSizeBytes(temporaryPath()))
    }

    @Test
    fun aFileThatIsNotThereCannotBeRead() {
        assertFailsWith<IllegalArgumentException> { readFileBytes(temporaryPath()) }
    }

    private fun temporaryPath(): String =
        (NSTemporaryDirectory() + NSUUID().UUIDString()).also { written.add(it) }
}
