package io.lukia.features.library.infrastructure.importing

import io.lukia.core.domain.document.PickedDocument
import kotlinx.coroutines.test.runTest
import java.io.File
import java.security.MessageDigest
import kotlin.io.path.createTempFile
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** A desktop book's hash has to be the server's name for the same bytes, or the two never meet. */
class DesktopContentHashTest {

    private val analyzer = DesktopDocumentAnalyzer()

    @Test
    fun aFileIsNamedAsTheServerWouldNameIt() = runTest {
        // Larger than one read window, so the hash spans several.
        val bytes = Random(7).nextBytes(3 * 1024 * 1024 + 17)
        val file = createTempFile(suffix = ".pdf").toFile().apply { writeBytes(bytes) }

        try {
            val expected = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

            assertEquals(expected, analyzer.contentHash(picked(file)))
        } finally {
            file.delete()
        }
    }

    @Test
    fun aFileThatIsGoneHasNoHash() = runTest {
        assertNull(analyzer.contentHash(picked(File("does-not-exist.pdf"))))
    }

    private fun picked(file: File) = PickedDocument(file.name, "pdf", file.path)
}
