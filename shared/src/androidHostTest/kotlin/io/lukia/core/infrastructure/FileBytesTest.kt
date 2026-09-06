package io.lukia.core.infrastructure

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Bytes on and off the disk, on Android - the same rules the desktop and iOS
 * tests of this name pin.
 *
 * The platform this was missing on is the one it was written for. What goes
 * through these is the converted text of a book, which is a container that means
 * nothing until it is complete, and Android is the implementation that has to
 * put a scratch file into place by hand: `java.nio.file` arrived at API 26 and
 * desugaring does not back-fill it, so the desktop's atomic move is not
 * available here. A rule that has to be kept by hand is exactly the one worth a
 * test.
 *
 * On the host rather than on a device: nothing below reaches further into
 * Android than a cache directory, and a test that needs an emulator is a test
 * that does not run.
 */
@RunWith(RobolectricTestRunner::class)
class FileBytesTest {

    /** Where the app really writes a book's converted text - see AndroidDocumentWorkspace. */
    private val directory: File =
        File(ApplicationProvider.getApplicationContext<Context>().cacheDir, "file-bytes")
            .apply { mkdirs() }

    @AfterTest
    fun tidy() {
        directory.deleteRecursively()
    }

    @Test
    fun whatIsWrittenIsWhatComesBack() {
        val path = pathIn("book.txt")
        val bytes = ByteArray(2_048) { (it * 31).toByte() }

        writeFileBytes(path, bytes)

        assertContentEquals(bytes, readFileBytes(path))
    }

    @Test
    fun writingAgainReplacesWhatWasThere() {
        val path = pathIn("book.txt")

        writeFileBytes(path, ByteArray(4_096) { 1 })
        writeFileBytes(path, byteArrayOf(7, 7, 7))

        assertContentEquals(byteArrayOf(7, 7, 7), readFileBytes(path))
        assertEquals(3L, fileSizeBytes(path))
    }

    @Test
    fun anEmptyFileIsAFileWithNothingInIt() {
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
    fun nothingIsLeftBesideTheFileItWrote() {
        writeFileBytes(pathIn("book.txt"), ByteArray(64))

        assertContentEquals(listOf("book.txt"), directory.list()?.sorted())
    }

    /**
     * A write that was cut short leaves its scratch file behind, and the next
     * write has to go through as though it were not there. Android puts the
     * scratch file into place by hand, so this is the one platform where a
     * leftover could be picked up rather than written over.
     */
    @Test
    fun aScratchFileLeftByAnInterruptedWriteDoesNotStopTheNextOne() {
        val path = pathIn("book.txt")
        File(directory, "book.txt.part").writeBytes(ByteArray(4_096) { 9 })

        writeFileBytes(path, byteArrayOf(7, 7, 7))

        assertContentEquals(byteArrayOf(7, 7, 7), readFileBytes(path))
        assertContentEquals(listOf("book.txt"), directory.list()?.sorted())
    }

    @Test
    fun aFileThatIsNotThereHasNoSize() {
        assertEquals(null, fileSizeBytes(pathIn("missing.txt")))
    }

    @Test
    fun aFileThatIsNotThereCannotBeRead() {
        assertTrue(runCatching { readFileBytes(pathIn("missing.txt")) }.isFailure)
    }

    private fun pathIn(name: String): String = File(directory, name).absolutePath
}
