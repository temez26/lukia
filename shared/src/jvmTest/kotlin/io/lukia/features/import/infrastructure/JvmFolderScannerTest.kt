package io.lukia.features.import.infrastructure

import io.lukia.core.domain.PickedFolder
import io.lukia.features.import.domain.MaxFolderScanDepth
import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JvmFolderScannerTest {

    private val scanner = JvmFolderScanner()

    @Test
    fun findsSupportedDocumentsAndIgnoresEverythingElse() = runTest {
        val folder = createFolder(
            "book.pdf",
            "novel.epub",
            "report.docx",
            "notes.txt",
            "readme.md",
            "cover.jpg",
            "sequel.mobi",
        )

        val documents = scanner.scan(folder.asPickedFolder())

        // Compared as a set: File.listFiles gives no ordering guarantee.
        assertEquals(
            setOf("book.pdf", "novel.epub", "report.docx", "notes.txt", "readme.md"),
            documents.map { it.fileName }.toSet(),
        )
        assertTrue(documents.all { File(it.location).isFile })
    }

    /** A shelf on disk is organised, not flat. */
    @Test
    fun findsBooksFiledInSubFolders() = runTest {
        val folder = createFolder("loose.pdf")
        val series = File(folder, "Le Guin/Earthsea").apply { mkdirs() }
        File(series, "wizard.epub").writeText("test")

        val documents = scanner.scan(folder.asPickedFolder())

        assertEquals(
            setOf("loose.pdf", "wizard.epub"),
            documents.map { it.fileName }.toSet(),
        )
    }

    /** Dot directories hold a sync client's caches, never a reader's books. */
    @Test
    fun ignoresHiddenSubFolders() = runTest {
        val folder = createFolder("book.pdf")
        val hidden = File(folder, ".cache").apply { mkdir() }
        File(hidden, "stale.pdf").writeText("test")

        val documents = scanner.scan(folder.asPickedFolder())

        assertEquals(listOf("book.pdf"), documents.map { it.fileName })
    }

    /**
     * A folder that links back to one of its own parents would otherwise be
     * walked forever, so the scan stops at a fixed depth.
     */
    @Test
    fun stopsDescendingAtTheDepthCap() = runTest {
        val folder = createFolder()

        var deepest = folder
        repeat(MaxFolderScanDepth + 2) { level ->
            deepest = File(deepest, "level$level").apply { mkdir() }
            File(deepest, "book$level.pdf").writeText("test")
        }

        val found = scanner.scan(folder.asPickedFolder()).map { it.fileName }.toSet()

        assertTrue("book0.pdf" in found)
        assertFalse("book${MaxFolderScanDepth + 1}.pdf" in found)
    }

    @Test
    fun matchesExtensionsRegardlessOfCase() = runTest {
        val folder = createFolder("BOOK.PDF")

        val documents = scanner.scan(folder.asPickedFolder())

        assertEquals(listOf("BOOK.PDF"), documents.map { it.fileName })
    }

    @Test
    fun returnsEmptyListForAPathThatIsNotAFolder() = runTest {
        val missing = File(
            Files.createTempDirectory("lukia-scan").toFile(),
            "does-not-exist",
        )

        assertTrue(scanner.scan(missing.asPickedFolder()).isEmpty())
    }

    private fun createFolder(vararg fileNames: String): File {
        val folder = Files.createTempDirectory("lukia-scan").toFile()
        folder.deleteOnExit()

        fileNames.forEach { fileName ->
            File(folder, fileName).apply {
                writeText("test")
                deleteOnExit()
            }
        }

        return folder
    }

    private fun File.asPickedFolder() = PickedFolder(
        name = name,
        location = absolutePath,
    )
}
