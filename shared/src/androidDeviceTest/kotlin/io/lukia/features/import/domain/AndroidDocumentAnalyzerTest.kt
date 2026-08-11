package io.lukia.features.import.domain

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AndroidDocumentAnalyzerTest {

    private val analyzer = AndroidDocumentAnalyzer(
        InstrumentationRegistry.getInstrumentation().targetContext
    )

    @Test
    fun analyzesContentDocumentMetadata() = runTest {
        val document = PickedDocument(
            fileName = "book.pdf",
            extension = "pdf",
            location = "content://io.lukia.shared.test.documents/with-size"
        )

        val result = analyzer.analyze(document)

        assertEquals(document, result.document)
        assertEquals(DocumentType.PDF, result.type)
        assertTrue(result.exists)
        assertEquals(1024L, result.sizeBytes)
    }

    @Test
    fun usesZeroWhenContentProviderDoesNotSupplySize() = runTest {
        val document = PickedDocument(
            fileName = "book.epub",
            extension = "epub",
            location = "content://io.lukia.shared.test.documents/without-size"
        )

        val result = analyzer.analyze(document)

        assertEquals(DocumentType.EPUB, result.type)
        assertTrue(result.exists)
        assertEquals(0L, result.sizeBytes)
    }

    @Test
    fun missingContentDocumentIsRejected() = runTest {
        val document = PickedDocument(
            fileName = "missing.pdf",
            extension = "pdf",
            location = "content://io.lukia.shared.test.documents/missing"
        )

        val exception = assertFailsWith<IllegalArgumentException> {
            analyzer.analyze(document)
        }

        assertTrue(exception.message!!.startsWith("File does not exist:"))
    }
}
