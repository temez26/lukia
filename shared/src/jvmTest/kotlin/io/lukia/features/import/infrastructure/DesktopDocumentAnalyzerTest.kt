package io.lukia.features.import.infrastructure

import io.lukia.core.domain.DocumentType
import io.lukia.core.domain.TestDocuments
import kotlinx.coroutines.test.runTest
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createTempDirectory
import kotlin.io.path.createTempFile
import kotlin.io.path.deleteIfExists
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DesktopDocumentAnalyzerTest {

    private val analyzer = DesktopDocumentAnalyzer()

    @Test
    fun analyzesPdfDocument() = runTest {
        val file = createTempFile(
            prefix = "lukia-test-",
            suffix = ".pdf",
        )

        try {
            val content = "test pdf content".encodeToByteArray()
            file.toFile().writeBytes(content)

            val document = TestDocuments.pdf(
                location = file.toString(),
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
            suffix = ".epub",
        )

        try {
            val content = "test epub content".encodeToByteArray()
            file.toFile().writeBytes(content)

            val document = TestDocuments.epub(
                location = file.toString(),
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
            suffix = ".PDF",
        )

        try {
            val document = TestDocuments.uppercasePdf(
                location = file.toString(),
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
            suffix = ".mobi",
        )

        try {
            val document = TestDocuments.unsupported(
                location = file.toString(),
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
            suffix = ".pdf",
        )

        try {
            val document = TestDocuments.pdf(
                location = file.toString(),
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
                location = directory.toString(),
            )

            val exception = assertFailsWith<IllegalArgumentException> {
                analyzer.analyze(document)
            }

            assertTrue(
                exception.message!!.startsWith("Path is not a file:"),
            )
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun nonexistentFileThrowsException() = runTest {
        val document = TestDocuments.missingPdf(
            location = "/this/file/does/not/exist/book.pdf",
        )

        val exception = assertFailsWith<IllegalArgumentException> {
            analyzer.analyze(document)
        }

        assertTrue(
            exception.message!!.startsWith("File does not exist:"),
        )
    }

    @Test
    fun analyzesPdfDocumentPopulatesCoverImage() = runTest {
        val file = createTempFile(
            prefix = "lukia-test-",
            suffix = ".pdf",
        )

        try {
            file.toFile().writeBytes(buildPdf())

            val document = TestDocuments.pdf(
                location = file.toString(),
            )

            val result = analyzer.analyze(document)

            val coverBytes = assertNotNull(result.coverImage).bytes
            assertTrue(coverBytes.isNotEmpty())
            // JPEG rather than the PNG the renderer produced: a page rendered at
            // 96 DPI is larger than a cover is stored at, so what reaches the
            // library is the shrunk re-encoding of it.
            assertEquals(0xFF.toByte(), coverBytes[0])
            assertEquals(0xD8.toByte(), coverBytes[1])
            assertEquals(0xFF.toByte(), coverBytes[2])
        } finally {
            file.deleteIfExists()
        }
    }

    @Test
    fun analyzesEpubDocumentPopulatesCoverImage() = runTest {
        val file = createTempFile(
            prefix = "lukia-test-",
            suffix = ".epub",
        )

        try {
            val coverBytes = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
            file.toFile().writeBytes(buildEpub(coverBytes))

            val document = TestDocuments.epub(
                location = file.toString(),
            )

            val result = analyzer.analyze(document)

            assertEquals(coverBytes.toList(), assertNotNull(result.coverImage).bytes.toList())
        } finally {
            file.deleteIfExists()
        }
    }

    private fun buildPdf(): ByteArray {
        val output = ByteArrayOutputStream()
        PDDocument().use { document ->
            document.addPage(PDPage())
            document.save(output)
        }
        return output.toByteArray()
    }

    private fun buildEpub(coverBytes: ByteArray): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            fun addEntry(name: String, content: ByteArray) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content)
                zip.closeEntry()
            }

            addEntry(
                "META-INF/container.xml",
                """
                    <?xml version="1.0"?>
                    <container>
                      <rootfiles>
                        <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
                      </rootfiles>
                    </container>
                """.trimIndent().encodeToByteArray(),
            )

            addEntry(
                "OEBPS/content.opf",
                """
                    <?xml version="1.0"?>
                    <package xmlns="http://www.idpf.org/2007/opf">
                      <metadata>
                        <meta name="cover" content="cover-image"/>
                      </metadata>
                      <manifest>
                        <item id="cover-image" href="images/cover.jpg" media-type="image/jpeg"/>
                      </manifest>
                    </package>
                """.trimIndent().encodeToByteArray(),
            )

            addEntry("OEBPS/images/cover.jpg", coverBytes)
        }
        return output.toByteArray()
    }
}
