package io.lukia.features.library.infrastructure.importing

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.lukia.core.domain.document.DocumentType
import io.lukia.core.domain.document.TestDocuments
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * What the importer learns about a book it was handed as a `content://` URI,
 * which is how every book picked on Android arrives.
 *
 * On the host rather than on a device. What is being asked here is what the
 * analyser does with a provider's answers - a size column that is there, one
 * that is not, a document that has gone - and a provider answering on the host
 * answers the same way. What genuinely needs a device stayed next door in
 * `androidDeviceTest`.
 *
 * The provider is registered in code rather than declared in a manifest, so
 * there is one place that says which authority it answers on.
 */
@RunWith(RobolectricTestRunner::class)
class AndroidDocumentAnalyzerTest {

    private val analyzer = AndroidDocumentAnalyzer(
        ApplicationProvider.getApplicationContext<Context>(),
    )

    @BeforeTest
    fun registerProvider() {
        Robolectric.setupContentProvider(TestDocumentProvider::class.java, AUTHORITY)
    }

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

    private companion object {
        const val AUTHORITY = "io.lukia.shared.test.documents"
    }
}
