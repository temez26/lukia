package io.lukia.features.import.infrastructure

import io.lukia.features.import.TestDocuments
import io.lukia.features.import.domain.DocumentType
import kotlinx.coroutines.test.runTest
import kotlin.io.path.createTempDirectory
import kotlin.io.path.createTempFile
import kotlin.io.path.deleteIfExists
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DesktopDocumentAnalyzerTest {

    private val analyzer = DesktopDocumentAnalyzer()

    @Test
    fun analyzesPdfDocument() = runTest {
        val file = createTempFile(
            prefix = "lukia-test-",
            suffix = ".pdf"
        )

        try {
            val content = "test pdf content".encodeToByteArray()
            file.toFile().writeBytes(content)

            val document = TestDocuments.pdf(
                location = file.toString()
            )

            val result = analyzer.analyze(document)

            assertEquals(document, result.document)
            assertEquals(DocumentType.PDF, result.type)
            assertTrue(result.exists)
            assertEquals(content.size.toLong(), result.sizeBytes)
        } finally {
            file.deleteIfExists()
        }
    }

    @Test
    fun analyzesEpubDocument() = runTest {
        val file = createTempFile(
            prefix = "lukia-test-",
            suffix = ".epub"
        )

        try {
            val content = "test epub content".encodeToByteArray()
            file.toFile().writeBytes(content)

            val document = TestDocuments.epub(
                location = file.toString()
            )

            val result = analyzer.analyze(document)

            assertEquals(document, result.document)
            assertEquals(DocumentType.EPUB, result.type)
            assertTrue(result.exists)
            assertEquals(content.size.toLong(), result.sizeBytes)
        } finally {
            file.deleteIfExists()
        }
    }

    @Test
    fun extensionIsCaseInsensitive() = runTest {
        val file = createTempFile(
            prefix = "lukia-test-",
            suffix = ".PDF"
        )

        try {
            val document = TestDocuments.uppercasePdf(
                location = file.toString()
            )

            val result = analyzer.analyze(document)

            assertEquals(DocumentType.PDF, result.type)
        } finally {
            file.deleteIfExists()
        }
    }

    @Test
    fun unsupportedExtensionReturnsUnknown() = runTest {
        val file = createTempFile(
            prefix = "lukia-test-",
            suffix = ".txt"
        )

        try {
            val document = TestDocuments.txt(
                location = file.toString()
            )

            val result = analyzer.analyze(document)

            assertEquals(DocumentType.UNKNOWN, result.type)
        } finally {
            file.deleteIfExists()
        }
    }

    @Test
    fun analyzesEmptyDocumentWithZeroSize() = runTest {
        val file = createTempFile(
            prefix = "lukia-test-",
            suffix = ".pdf"
        )

        try {
            val document = TestDocuments.pdf(
                location = file.toString()
            )

            val result = analyzer.analyze(document)

            assertEquals(0L, result.sizeBytes)
        } finally {
            file.deleteIfExists()
        }
    }

    @Test
    fun directoryIsRejected() = runTest {
        val directory = createTempDirectory(prefix = "lukia-test-")

        try {
            val document = TestDocuments.pdf(
                location = directory.toString()
            )

            val exception = assertFailsWith<IllegalArgumentException> {
                analyzer.analyze(document)
            }

            assertTrue(
                exception.message!!.startsWith("Path is not a file:")
            )
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun nonexistentFileThrowsException() = runTest {
        val document = TestDocuments.missingPdf(
            location = "/this/file/does/not/exist/book.pdf"
        )

        val exception = assertFailsWith<IllegalArgumentException> {
            analyzer.analyze(document)
        }

        assertTrue(
            exception.message!!.startsWith("File does not exist:")
        )
    }
}
