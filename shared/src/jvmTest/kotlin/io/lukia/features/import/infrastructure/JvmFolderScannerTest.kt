package io.lukia.features.import.infrastructure

import io.lukia.core.domain.PickedFolder
import io.lukia.features.import.domain.MaxFolderScanDepth
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JvmFolderScannerTest {

    private val scanner = JvmFolderScanner()

    /**
     * One directory per test, taken away afterwards.
     *
     * Deleted here rather than left to `deleteOnExit`, which cannot do it: that
     * only removes a directory that is empty when the JVM stops, and half the
     * tests below build folders inside their folder. What it left behind was a
     * temporary directory per test, per run, for ever.
     */
    private val root = File(
        System.getProperty("java.io.tmpdir"),
        "lukia-scan-test-${kotlin.random.Random.nextInt(until = Int.MAX_VALUE)}",
    ).apply { mkdirs() }

    @AfterTest
    fun tearDown() {
        root.deleteRecursively()
    }

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

    /**
     * Dot directories hold a sync client's caches, never a reader's books.
     *
     * Named rather than flagged, because that is how such a directory usually
     * arrives on Windows: nothing sets the DOS hidden attribute on a `.cache`
     * copied off a Mac or written by a sync client, so a scanner that only asked
     * `isHidden` walked straight into it there.
     */
    @Test
    fun ignoresHiddenSubFolders() = runTest {
        val folder = createFolder("book.pdf")
        val hidden = File(folder, ".cache").apply { mkdir() }
        File(hidden, "stale.pdf").writeText("test")

        val documents = scanner.scan(folder.asPickedFolder())

        assertEquals(listOf("book.pdf"), documents.map { it.fileName })
    }

    /**
     * ...but a reader who points the picker at a dot directory has said what
     * they meant, and a scan that came back empty would look like a refusal
     * nothing on screen explained.
     */
    @Test
    fun scansTheChosenFolderEvenWhenItIsItselfHidden() = runTest {
        val folder = createFolder()
        val chosen = File(folder, ".books").apply { mkdir() }
        File(chosen, "wizard.epub").writeText("test")

        val documents = scanner.scan(chosen.asPickedFolder())

        assertEquals(listOf("wizard.epub"), documents.map { it.fileName })
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
        val missing = File(root, "does-not-exist")

        assertTrue(scanner.scan(missing.asPickedFolder()).isEmpty())
    }

    private fun createFolder(vararg fileNames: String): File {
        val folder = File(root, "folder-${kotlin.random.Random.nextInt()}").apply { mkdirs() }

        fileNames.forEach { fileName -> File(folder, fileName).writeText("test") }

        return folder
    }

    private fun File.asPickedFolder() = PickedFolder(
        name = name,
        location = absolutePath,
    )
}
