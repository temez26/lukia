package io.lukia.features.import.infrastructure

import io.lukia.core.domain.PickedFolder
import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
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
            "sequel.mobi"
        )
        File(folder, "nested").mkdir()

        val documents = scanner.scan(folder.asPickedFolder())

        // Compared as a set: File.listFiles gives no ordering guarantee.
        assertEquals(
            setOf("book.pdf", "novel.epub", "report.docx", "notes.txt", "readme.md"),
            documents.map { it.fileName }.toSet()
        )
        assertTrue(documents.all { File(it.location).isFile })
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
            "does-not-exist"
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
        location = absolutePath
    )
}
