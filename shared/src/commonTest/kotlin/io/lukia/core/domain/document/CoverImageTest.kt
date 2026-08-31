package io.lukia.core.domain.document

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class CoverImageTest {

    @Test
    fun coverImagesWithEqualBytesAreEqual() {
        val first = CoverImage(byteArrayOf(1, 2, 3))
        val second = CoverImage(byteArrayOf(1, 2, 3))

        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
    }

    @Test
    fun coverImagesWithDifferentBytesAreNotEqual() {
        val first = CoverImage(byteArrayOf(1, 2, 3))
        val second = CoverImage(byteArrayOf(4, 5, 6))

        assertNotEquals(first, second)
    }

    @Test
    fun documentInfoEqualityAccountsForCoverImageContent() {
        val document = TestDocuments.pdf(location = "/test/book.pdf")

        val first = DocumentInfo(
            document = document,
            type = DocumentType.PDF,
            exists = true,
            sizeBytes = 1024L,
            coverImage = CoverImage(byteArrayOf(1, 2, 3)),
        )
        val second = DocumentInfo(
            document = document,
            type = DocumentType.PDF,
            exists = true,
            sizeBytes = 1024L,
            coverImage = CoverImage(byteArrayOf(1, 2, 3)),
        )

        assertEquals(first, second)
    }
}
