package io.lukia.features.library.presentation.browser

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FileBrowsingTest {

    private fun folderWith(vararg names: String): File {
        val root = createTempDirectory("browsing").toFile()

        names.forEach { name ->
            if (name.endsWith('/')) {
                File(root, name.trimEnd('/')).mkdirs()
            } else {
                File(root, name).writeText("")
            }
        }

        return root
    }

    @Test
    fun `lists folders before books`() {
        val folder = folderWith("Reading.epub", "Notes/", "Manual.pdf", "Archive/")

        val listed = listFolder(folder, foldersOnly = false).map { it.name }

        assertEquals(listOf("Archive", "Notes", "Manual.pdf", "Reading.epub"), listed)
    }

    @Test
    fun `sorts names the way a reader reads them`() {
        val folder = folderWith("zeta.epub", "Alpha.epub", "beta.epub")

        val listed = listFolder(folder, foldersOnly = false).map { it.name }

        assertEquals(listOf("Alpha.epub", "beta.epub", "zeta.epub"), listed)
    }

    @Test
    fun `leaves out files the library cannot read`() {
        val folder = folderWith("Reading.epub", "notes.txt", "photo.jpeg", "archive.zip")

        val listed = listFolder(folder, foldersOnly = false).map { it.name }

        // `.txt` is a format the library reads; the image and the archive are not.
        assertEquals(listOf("notes.txt", "Reading.epub"), listed)
    }

    @Test
    fun `leaves out hidden entries`() {
        val folder = folderWith(".hidden/", ".secret.epub", "Reading.epub")

        val listed = listFolder(folder, foldersOnly = false).map { it.name }

        assertEquals(listOf("Reading.epub"), listed)
    }

    @Test
    fun `shows only folders when only a folder may be chosen`() {
        val folder = folderWith("Reading.epub", "Notes/")

        val listed = listFolder(folder, foldersOnly = true)

        assertEquals(listOf("Notes"), listed.map { it.name })
        assertTrue(listed.all { it.isFolder })
    }

    @Test
    fun `an unreadable folder is an empty one`() {
        val missing = File(createTempDirectory("browsing").toFile(), "gone")

        assertEquals(emptyList(), listFolder(missing, foldersOnly = false))
    }

    @Test
    fun `the trail runs from the root down to the folder`() {
        val folder = folderWith("Books/Fiction/")
        val deep = File(folder, "Books/Fiction")

        val trail = deep.trail()

        assertEquals(deep.absolutePath, trail.last().path)
        assertEquals("Fiction", trail.last().name)
        assertEquals("Books", trail[trail.size - 2].name)
        assertTrue(trail.all { it.isFolder })
    }
}
