package io.lukia.core.infrastructure

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.test.runTest
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUUID
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * What the document picker copies in is gone within minutes, so a picked book
 * has to be taken out of the picker's folder before the library points at it.
 */
@OptIn(ExperimentalForeignApi::class)
class IOSIncomingDocumentStoreTest {

    private val store = IOSIncomingDocumentStore()
    private val fileManager = NSFileManager.defaultManager

    @Test
    fun aPickedFileIsMovedIntoTheAppsOwnStorage() = runTest {
        val picked = pickerCopy("Der Fuchs.epub", BOOK)

        val document = assertNotNull(store.adopt(NSURL.fileURLWithPath(picked)))

        try {
            val kept = assertNotNull(NSURL.URLWithString(document.location)?.path)

            assertFalse(fileManager.fileExistsAtPath(picked), "the picker's copy was left behind")
            assertNotEquals(picked, kept)
            assertContentEquals(BOOK, readFileBytes(kept))
            assertEquals("Der Fuchs.epub", document.fileName)
            assertEquals("epub", document.extension)
        } finally {
            store.discard(document.location)
        }
    }

    /** Two books picked under the same name are two books, not one replacing the other. */
    @Test
    fun twoPicksOfTheSameNameAreKeptApart() = runTest {
        val first = assertNotNull(store.adopt(NSURL.fileURLWithPath(pickerCopy("book.pdf", BOOK))))
        val second = assertNotNull(store.adopt(NSURL.fileURLWithPath(pickerCopy("book.pdf", OTHER_BOOK))))

        try {
            assertNotEquals(first.location, second.location)
        } finally {
            store.discard(first.location)
            store.discard(second.location)
        }
    }

    /** Removing the book from the shelf removes the copy, which is the only one there is. */
    @Test
    fun discardingAnAdoptedBookDeletesItsCopy() = runTest {
        val document = assertNotNull(store.adopt(NSURL.fileURLWithPath(pickerCopy("gone.txt", BOOK))))
        val kept = assertNotNull(NSURL.URLWithString(document.location)?.path)

        store.discard(document.location)

        assertFalse(fileManager.fileExistsAtPath(kept))
    }

    @Test
    fun aFileThatIsNotThereIsNotAdopted() {
        val missing = NSTemporaryDirectory() + NSUUID().UUIDString() + ".pdf"

        assertNull(store.adopt(NSURL.fileURLWithPath(missing)))
    }

    private fun pickerCopy(name: String, bytes: ByteArray): String {
        val directory = NSTemporaryDirectory() + NSUUID().UUIDString()
        fileManager.createDirectoryAtPath(
            path = directory,
            withIntermediateDirectories = true,
            attributes = null,
            error = null,
        )

        return "$directory/$name".also { writeFileBytes(it, bytes) }
    }

    private companion object {
        val BOOK = "Der kleine Fuchs".encodeToByteArray()
        val OTHER_BOOK = "Ein anderes Buch".encodeToByteArray()
    }
}
