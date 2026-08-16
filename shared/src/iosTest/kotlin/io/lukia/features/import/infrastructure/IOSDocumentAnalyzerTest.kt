package io.lukia.features.import.infrastructure

import io.lukia.core.domain.TestDocuments
import io.lukia.core.domain.DocumentType
import io.lukia.features.import.infrastructure.IOSDocumentAnalyzer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.test.runTest
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUUID
import platform.Foundation.NSTemporaryDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@OptIn(ExperimentalForeignApi::class)
class IOSDocumentAnalyzerTest {

    private val analyzer = IOSDocumentAnalyzer()
    private val fileManager = NSFileManager.defaultManager

    @Test
    fun analyzesEmptyPdfDocument() = runTest {
        withTemporaryFile("pdf") { path ->
            val document = TestDocuments.pdf(
                location = location(path)
            )

            val result = analyzer.analyze(document)

            assertEquals(document, result.document)
            assertEquals(DocumentType.PDF, result.type)
            assertTrue(result.exists)
            assertEquals(0L, result.sizeBytes)
        }
    }

    @Test
    fun analyzesEpubDocument() = runTest {
        withTemporaryFile("epub") { path ->
            val document = TestDocuments.epub(
                location = location(path)
            )

            val result = analyzer.analyze(document)

            assertEquals(DocumentType.EPUB, result.type)
            assertTrue(result.exists)
        }
    }

    @Test
    fun extensionIsCaseInsensitive() = runTest {
        withTemporaryFile("PDF") { path ->
            val result = analyzer.analyze(
                TestDocuments.uppercasePdf(
                    location = location(path)
                )
            )

            assertEquals(DocumentType.PDF, result.type)
        }
    }

    @Test
    fun unsupportedExtensionReturnsUnknown() = runTest {
        withTemporaryFile("txt") { path ->
            val result = analyzer.analyze(
                TestDocuments.unsupported(
                    location = location(path)
                )
            )

            assertEquals(DocumentType.UNKNOWN, result.type)
        }
    }

    @Test
    fun nonexistentFileThrowsException() = runTest {
        val document = TestDocuments.missingPdf(
            location = "file:///lukia-test-missing.pdf"
        )

        val exception = assertFailsWith<IllegalArgumentException> {
            analyzer.analyze(document)
        }

        assertTrue(exception.message!!.startsWith("File does not exist:"))
    }

    @Test
    fun directoryIsRejected() = runTest {
        val path = temporaryPath("directory")
        assertTrue(fileManager.createDirectoryAtPath(path, true, null, null))

        try {
            val exception = assertFailsWith<IllegalArgumentException> {
                analyzer.analyze(
                    TestDocuments.pdf(
                        location = location(path)
                    )
                )
            }

            assertTrue(exception.message!!.startsWith("Path is not a file:"))
        } finally {
            fileManager.removeItemAtPath(path, null)
        }
    }

    private suspend fun withTemporaryFile(
        extension: String,
        block: suspend (String) -> Unit
    ) {
        val path = temporaryPath(extension)
        assertTrue(fileManager.createFileAtPath(path, null, null))

        try {
            block(path)
        } finally {
            fileManager.removeItemAtPath(path, null)
        }
    }

    private fun location(path: String): String =
        NSURL.fileURLWithPath(path).absoluteString!!

    private fun temporaryPath(extension: String): String =
        "${NSTemporaryDirectory()}lukia-test-${NSUUID().UUIDString}.$extension"
}
