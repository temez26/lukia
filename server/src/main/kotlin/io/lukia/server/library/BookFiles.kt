package io.lukia.server.library

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Where book files live: `books/<sha256>` in the data directory, with uploads
 * written to `incoming/` first and moved into place once their hash is known.
 *
 * Incoming files are on the same disk as the books, so the move is a rename and
 * a book is either wholly there or not there at all.
 */
class BookFiles(dataDirectory: File) {

    private val books = File(dataDirectory, "books").apply { mkdirs() }
    private val incoming = File(dataDirectory, "incoming").apply { mkdirs() }

    init {
        // An upload cut off by a restart left its partial file behind, and
        // nothing will ever finish it.
        incoming.listFiles()?.forEach(File::delete)
    }

    /** Null for anything that is not a book id - which keeps a request's id from naming any other path. */
    fun fileOf(id: String): File? = File(books, id).takeIf { isBookId(id) }

    fun newIncoming(): File = File.createTempFile("upload-", ".part", incoming)

    /** Moves an upload into place. The same book uploaded twice at once lands as the same bytes either way. */
    fun store(upload: File, id: String) {
        val target = requireNotNull(fileOf(id)) { "Not a book id" }
        Files.move(
            upload.toPath(),
            target.toPath(),
            StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.ATOMIC_MOVE,
        )
    }

    fun remove(id: String) {
        fileOf(id)?.delete()
    }
}

/** Whether [id] has the shape of a book id: a SHA-256 in lower-case hex. */
fun isBookId(id: String): Boolean = BookIdPattern.matches(id)

private val BookIdPattern = Regex("^[0-9a-f]{64}$")
