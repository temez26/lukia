package io.lukia.features.library.infrastructure.importing

import androidx.test.platform.app.InstrumentationRegistry
import io.lukia.core.domain.document.DocumentType
import io.lukia.core.domain.document.TestDocuments
import io.lukia.features.library.infrastructure.importing.AndroidDocumentAnalyzer
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AndroidDocumentAnalyzerTest {

    private val analyzer = AndroidDocumentAnalyzer(
        InstrumentationRegistry.getInstrumentation().targetContext,
    )

    @Test
    fun analyzesContentDocumentMetadata() = runTest {
        val document = TestDocuments.pdf(
            location = "content://io.lukia.shared.test.documents/with-size",
        )

        val result = analyzer.analyze(document)

        assertEquals(document, result.document)
        assertEquals(DocumentType.PDF, result.type)
        assertTrue(result.exists)
        assertEquals(1024L, result.sizeBytes)
    }

    @Test
    fun usesZeroWhenContentProviderDoesNotSupplySize() = runTest {
        val document = TestDocuments.epub(
            location = "content://io.lukia.shared.test.documents/without-size",
        )

        val result = analyzer.analyze(document)

        assertEquals(DocumentType.EPUB, result.type)
        assertTrue(result.exists)
        assertEquals(0L, result.sizeBytes)
    }

    @Test
    fun extensionIsCaseInsensitive() = runTest {
        val result = analyzer.analyze(
            TestDocuments.uppercasePdf(
                location = "content://io.lukia.shared.test.documents/with-size",
            ),
        )

        assertEquals(DocumentType.PDF, result.type)
    }

    @Test
    fun unsupportedExtensionReturnsUnknown() = runTest {
        val result = analyzer.analyze(
            TestDocuments.unsupported(
                location = "content://io.lukia.shared.test.documents/with-size",
            ),
        )

        assertEquals(DocumentType.UNKNOWN, result.type)
    }

    @Test
    fun missingContentDocumentIsRejected() = runTest {
        val document = TestDocuments.missingPdf(
            location = "content://io.lukia.shared.test.documents/missing",
        )

        val exception = assertFailsWith<IllegalArgumentException> {
            analyzer.analyze(document)
        }

        assertTrue(exception.message!!.startsWith("File does not exist:"))
    }
}
