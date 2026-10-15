package io.lukia.server.library

import io.lukia.core.domain.document.DocumentType
import io.lukia.server.TestClock
import io.lukia.server.inMemoryDatabase
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class LibraryServiceTest {

    private val dataDirectory = Files.createTempDirectory("lukia-library-test").toFile()
    private val database = inMemoryDatabase().apply { accountQueries.insert("admin", "hash", true, 0) }
    private val files = BookFiles(dataDirectory)
    private val library = LibraryService(database, files, TestClock())

    @AfterTest
    fun removeFiles() {
        dataDirectory.deleteRecursively()
    }

    @Test
    fun `changes come a page at a time, each cursor picking up where the last stopped`() {
        val ids = (1..5).map { add("Chapter $it") }

        val first = library.changesSince(0, limit = 2)
        val second = library.changesSince(first.cursor, limit = 2)
        val third = library.changesSince(second.cursor, limit = 2)
        val after = library.changesSince(third.cursor, limit = 2)

        assertEquals(ids, (first.items + second.items + third.items).map { it.id })
        assertEquals(listOf(true, true, false), listOf(first.hasMore, second.hasMore, third.hasMore))
        assertEquals(emptyList(), after.items)
        assertEquals(third.cursor, after.cursor)
    }

    @Test
    fun `stale partial uploads are cleared when the server starts`() {
        val leftOver = files.newIncoming()

        BookFiles(dataDirectory)

        assertEquals(false, leftOver.exists())
    }

    private fun add(text: String): String {
        val upload = files.newIncoming().apply { writeText(text) }
        val id = text.toByteArray().sha256()
        library.add(ReceivedUpload(upload, id, upload.length()), UploadName("$text.txt", DocumentType.TEXT), 1)
        return id
    }

    private fun ByteArray.sha256() =
        MessageDigest.getInstance("SHA-256").digest(this).joinToString("") { "%02x".format(it) }
}
