package io.lukia.core.infrastructure

import io.lukia.core.domain.document.DocumentInfo
import io.lukia.core.domain.document.DocumentType
import io.lukia.core.domain.document.PickedDocument
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class JvmDocumentWorkspaceTest {

    private val root = File(System.getProperty("java.io.tmpdir"), "lukia-workspace-test-${currentId()}")
    private val workspaceDirectory = File(root, "workspace")
    private val sourceDirectory = File(root, "source").also { it.mkdirs() }

    private val workspace = JvmDocumentWorkspace(directory = workspaceDirectory)

    @AfterTest
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun copiesTheDocumentIntoTheWorkspace() = runTest {
        val source = sourceFile("book.pdf", "a pdf, near enough")

        val copy = workspace.workingCopy(documentAt(source))

        val copied = File(copy.path)
        assertTrue(copied.isFile)
        assertNotEquals(source.absolutePath, copied.absolutePath)
        assertContentEquals(source.readBytes(), copied.readBytes())
    }

    @Test
    fun theCopyReportsWhereItCameFrom() = runTest {
        val source = sourceFile("book.pdf", "content")

        val copy = workspace.workingCopy(documentAt(source))

        assertEquals(source.absolutePath, copy.sourceLocation)
    }

    /**
     * The whole point of the copy: nothing downstream of it can reach the file
     * the user imported.
     */
    @Test
    fun writingToTheCopyLeavesTheOriginalAlone() = runTest {
        val source = sourceFile("book.pdf", "original")

        val copy = workspace.workingCopy(documentAt(source))
        File(copy.path).writeText("translated")

        assertEquals("original", source.readText())
    }

    @Test
    fun openingTheSameDocumentTwiceReusesTheCopy() = runTest {
        val source = sourceFile("book.pdf", "content")
        val document = documentAt(source)

        val first = workspace.workingCopy(document)
        val second = workspace.workingCopy(document)

        assertEquals(first.path, second.path)
        assertEquals(1, workspaceDirectory.listFiles()?.size)
    }

    /**
     * A copy is reused rather than rewritten, so an edit made to it - which is
     * what translation will be - has to survive the book being reopened.
     */
    @Test
    fun reopeningDoesNotOverwriteChangesMadeToTheCopy() = runTest {
        val source = sourceFile("book.pdf", "original")
        val document = documentAt(source)

        val first = workspace.workingCopy(document)
        File(first.path).writeText("translated")

        val second = workspace.workingCopy(document)

        assertEquals("translated", File(second.path).readText())
    }

    @Test
    fun twoDocumentsGetTwoCopies() = runTest {
        workspace.workingCopy(documentAt(sourceFile("one.pdf", "one")))
        workspace.workingCopy(documentAt(sourceFile("two.pdf", "two")))

        assertEquals(2, workspaceDirectory.listFiles()?.size)
    }

    @Test
    fun aMissingOriginalFails() = runTest {
        val missing = File(sourceDirectory, "gone.pdf")

        assertFailsWith<IllegalArgumentException> {
            workspace.workingCopy(documentAt(missing))
        }
    }

    /** A copy cut short leaves an empty file, which must not pass as the copy. */
    @Test
    fun anEmptyCopyIsRewrittenRatherThanReused() = runTest {
        val source = sourceFile("book.pdf", "content")
        val document = documentAt(source)

        val first = workspace.workingCopy(document)
        File(first.path).writeText("")

        val second = workspace.workingCopy(document)

        assertEquals("content", File(second.path).readText())
    }

    @Test
    fun discardDeletesTheCopy() = runTest {
        val source = sourceFile("book.pdf", "content")

        val copy = workspace.workingCopy(documentAt(source))
        workspace.discard(source.absolutePath)

        assertFalse(File(copy.path).exists())
    }

    @Test
    fun discardLeavesOtherDocumentsAlone() = runTest {
        val kept = workspace.workingCopy(documentAt(sourceFile("one.pdf", "one")))
        val removed = sourceFile("two.pdf", "two")
        workspace.workingCopy(documentAt(removed))

        workspace.discard(removed.absolutePath)

        assertTrue(File(kept.path).exists())
        assertEquals(1, workspaceDirectory.listFiles()?.size)
    }

    @Test
    fun discardingADocumentThatWasNeverOpenedDoesNothing() = runTest {
        workspace.discard("/books/never-opened.pdf")
    }

    @Test
    fun discardLeavesTheOriginalOnDisk() = runTest {
        val source = sourceFile("book.pdf", "content")

        workspace.workingCopy(documentAt(source))
        workspace.discard(source.absolutePath)

        assertTrue(source.isFile)
    }

    private fun sourceFile(name: String, content: String): File =
        File(sourceDirectory, name).apply { writeText(content) }

    private fun documentAt(file: File) = DocumentInfo(
        document = PickedDocument(
            fileName = file.name,
            extension = file.extension,
            location = file.absolutePath,
        ),
        type = DocumentType.PDF,
        exists = file.exists(),
        sizeBytes = file.length(),
    )

    private companion object {
        fun currentId(): String = kotlin.random.Random.nextInt(until = Int.MAX_VALUE).toString()
    }
}
