package io.lukia.features.library.presentation.browser

import io.lukia.core.domain.document.DocumentType
import java.io.File

/**
 * One line in the browser: a folder to go into, or a book to pick.
 *
 * Held as its own type rather than as a [File] so that everything above this is
 * a list of names and paths. Sorting, filtering and what a reader is allowed to
 * choose are decided here, once, against the filesystem; the panel that draws
 * them touches no files at all.
 */
internal data class BrowserEntry(
    val name: String,
    val path: String,
    val isFolder: Boolean,
)

/**
 * What is worth showing in [folder], in the order a file panel shows it.
 *
 * Folders first and then books, each run sorted by name the way a reader reads
 * names - case and accents ignored, so `epub` files do not sit in a block after
 * `EPUB` ones. Everything else in the folder is left out: this is a way to find
 * a book, not a file manager, and a list padded with settings files and disk
 * images is a longer list to search for the one thing being looked for.
 *
 * Hidden entries are skipped for the same reason, and an unreadable folder is an
 * empty one - a permission the reader does not have is not something they can do
 * anything about from here.
 */
internal fun listFolder(folder: File, foldersOnly: Boolean): List<BrowserEntry> {
    val children = runCatching { folder.listFiles() }.getOrNull().orEmpty()

    val (folders, files) = children
        .asSequence()
        .filterNot { it.isHidden || it.name.startsWith('.') }
        .partition { it.isDirectory }

    val shown = if (foldersOnly) emptyList() else files.filter { it.holdsABook() }

    return folders.asEntries(isFolder = true) + shown.asEntries(isFolder = false)
}

private fun File.holdsABook(): Boolean =
    DocumentType.fromExtension(extension) != DocumentType.UNKNOWN

private fun List<File>.asEntries(isFolder: Boolean): List<BrowserEntry> =
    map { BrowserEntry(name = it.name, path = it.absolutePath, isFolder = isFolder) }
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })

/**
 * The places a reader is likely to keep books, and every drive under which the
 * rest of them could be.
 *
 * Offered because the alternative is opening at one folder and walking up out of
 * it. The named places come first because that is where books actually are, and
 * the drives after them because on Windows they are the only way to reach
 * anything that is not under the home folder.
 *
 * Only what exists: a machine with no `Downloads` should not offer one, and an
 * empty card reader should not be a place to look.
 */
internal fun browserPlaces(): List<BrowserEntry> {
    val home = File(System.getProperty("user.home").orEmpty())

    val named = listOf(home) + NamedPlaces.map { File(home, it) }

    return (named + File.listRoots().orEmpty())
        .filter { it.isDirectory }
        .distinctBy { it.absolutePath }
        .map { place ->
            // The home folder is named after whoever is logged in, which is not
            // what anybody calls it.
            val name = if (place == home) "Home" else place.placeName()

            BrowserEntry(name = name, path = place.absolutePath, isFolder = true)
        }
}

/**
 * What to call a place in the row of them.
 *
 * A drive has no file name - `C:\` is its own root - so it is named by its path,
 * which is what the reader knows it as anyway. Everything else is named by its
 * folder.
 */
private fun File.placeName(): String = name.ifEmpty { path }

private val NamedPlaces = listOf("Desktop", "Documents", "Downloads")

/**
 * Every folder from the root of the filesystem down to this one, which is what
 * the path bar walks back along.
 *
 * Built by walking up rather than by splitting the path on a separator: which
 * character separates a path and what a root looks like are the platform's
 * business, and `File` already knows both.
 */
internal fun File.trail(): List<BrowserEntry> {
    val folders = generateSequence(absoluteFile) { it.parentFile }.toList().reversed()

    return folders.map { BrowserEntry(name = it.placeName(), path = it.absolutePath, isFolder = true) }
}
