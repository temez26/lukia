package io.lukia.features.reader.infrastructure

import io.lukia.features.reader.domain.page.PageSize
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** That a PDF's page sizes are measured once per file and read back after that. */
class PageSizeStoreTest {

    private val directory = createTempDirectory("page-sizes").toFile()
    private val book = File(directory, "book.pdf").apply { writeBytes(ByteArray(100)) }
    private val sizes = listOf(PageSize(612f, 792f), PageSize(595.5f, 842f))

    @AfterTest
    fun cleanUp() {
        directory.deleteRecursively()
    }

    @Test
    fun storedSizesAreReadBack() {
        storePageSizes(book.path, sizes)

        assertEquals(sizes, storedPageSizes(book.path, pageCount = 2))
    }

    @Test
    fun aBookNeverMeasuredHasNoStoredSizes() {
        assertNull(storedPageSizes(book.path, pageCount = 2))
    }

    /** The working copy was replaced by a different file under the same name. */
    @Test
    fun sizesFromAFileOfAnotherLengthAreIgnored() {
        storePageSizes(book.path, sizes)
        book.writeBytes(ByteArray(200))

        assertNull(storedPageSizes(book.path, pageCount = 2))
    }

    @Test
    fun sizesForAnotherPageCountAreIgnored() {
        storePageSizes(book.path, sizes)

        assertNull(storedPageSizes(book.path, pageCount = 3))
    }
}
