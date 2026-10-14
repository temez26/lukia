package io.lukia.server.library

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.readRawBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.lukia.api.ApiErrorCodes
import io.lukia.api.ApiRoutes
import io.lukia.api.BookPage
import io.lukia.api.BookResponse
import io.lukia.api.TokenResponse
import io.lukia.core.infrastructure.epub.MinimalEpub
import io.lukia.server.accounts.errorCode
import io.lukia.server.serverTest
import io.lukia.server.setUpAdmin
import io.lukia.server.signUp
import io.lukia.server.testConfig
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LibraryRoutesTest {

    @Test
    fun `an uploaded book is named by its hash and read for its metadata`() = serverTest { client ->
        val admin = client.setUpAdmin()
        val epub = MinimalEpub.book()

        val response = client.upload(admin, "moby-dick.epub", epub)
        val book: BookResponse = response.body()

        assertEquals(HttpStatusCode.Created, response.status)
        assertEquals(sha256(epub), book.id)
        assertEquals("EPUB", book.type)
        assertEquals(epub.size.toLong(), book.sizeBytes)
        assertEquals(MinimalEpub.TITLE, book.title)
        assertEquals(listOf(MinimalEpub.AUTHOR), book.authors)
    }

    @Test
    fun `the same file uploaded twice is one book`() = serverTest { client ->
        val admin = client.setUpAdmin()
        val alice = client.signUp(admin, "alice")

        val first: BookResponse = client.upload(admin, "moby-dick.epub", MinimalEpub.book()).body()
        val second = client.upload(alice, "another name.epub", MinimalEpub.book())

        assertEquals(HttpStatusCode.OK, second.status)
        assertEquals(first, second.body())
        assertEquals(listOf(first), client.changes(alice).books)
    }

    @Test
    fun `every account sees the one library`() = serverTest { client ->
        val admin = client.setUpAdmin()
        val alice = client.signUp(admin, "alice")

        val book: BookResponse = client.upload(alice, "notes.txt", "Call me Ishmael.".toByteArray()).body()

        assertEquals(listOf(book.id), client.changes(admin).books.map { it.id })
    }

    @Test
    fun `a file the app cannot read is refused and nothing is kept`() = serverTest { client ->
        val admin = client.setUpAdmin()

        val unsupported = client.upload(admin, "song.mp3", ByteArray(16))
        val unnamed = client.post(ApiRoutes.Books) {
            bearerAuth(admin.accessToken)
            setBody(ByteArray(16))
        }

        assertEquals(HttpStatusCode.UnsupportedMediaType, unsupported.status)
        assertEquals(ApiErrorCodes.UnsupportedFormat, unsupported.errorCode())
        assertEquals(HttpStatusCode.BadRequest, unnamed.status)
        assertTrue(client.changes(admin).books.isEmpty())
    }

    @Test
    fun `a book over the size limit is refused and nothing is kept`() {
        val config = testConfig(maxUploadBytes = 1024)

        serverTest(config = config) { client ->
            val admin = client.setUpAdmin()

            val response = client.upload(admin, "long.txt", ByteArray(2048) { 'a'.code.toByte() })

            assertEquals(HttpStatusCode.PayloadTooLarge, response.status)
            assertEquals(ApiErrorCodes.TooLarge, response.errorCode())
            assertTrue(client.changes(admin).books.isEmpty())
            assertTrue(config.dataDirectory.resolve("incoming").listFiles().orEmpty().isEmpty())
            assertTrue(config.dataDirectory.resolve("books").listFiles().orEmpty().isEmpty())
        }
    }

    @Test
    fun `a book downloads whole or from where a download broke off`() = serverTest { client ->
        val admin = client.setUpAdmin()
        val text = "Call me Ishmael. Some years ago - never mind how long precisely.".toByteArray()
        val book: BookResponse = client.upload(admin, "moby.txt", text).body()

        val whole = client.get(ApiRoutes.bookFile(book.id)) { bearerAuth(admin.accessToken) }
        val rest = client.get(ApiRoutes.bookFile(book.id)) {
            bearerAuth(admin.accessToken)
            header(HttpHeaders.Range, "bytes=5-")
        }

        assertContentEquals(text, whole.readRawBytes())
        assertEquals(HttpStatusCode.PartialContent, rest.status)
        assertContentEquals(text.copyOfRange(5, text.size), rest.readRawBytes())
    }

    @Test
    fun `a cover is served when the book has one`() = serverTest { client ->
        val admin = client.setUpAdmin()
        val withCover: BookResponse = client.upload(admin, "moby.epub", MinimalEpub.book()).body()
        val plain: BookResponse = client.upload(admin, "moby.txt", "Call me Ishmael.".toByteArray()).body()

        val cover = client.get(ApiRoutes.bookCover(withCover.id)) { bearerAuth(admin.accessToken) }
        val none = client.get(ApiRoutes.bookCover(plain.id)) { bearerAuth(admin.accessToken) }

        assertTrue(withCover.hasCover)
        assertEquals(HttpStatusCode.OK, cover.status)
        assertEquals(ContentType.Image.PNG, cover.contentType()?.withoutParameters())
        assertEquals(HttpStatusCode.NotFound, none.status)
    }

    @Test
    fun `a PDF's cover is drawn from its first page`() = serverTest { client ->
        val admin = client.setUpAdmin()

        val book: BookResponse = client.upload(admin, "paper.pdf", onePagePdf()).body()
        val cover = client.get(ApiRoutes.bookCover(book.id)) { bearerAuth(admin.accessToken) }

        assertEquals("PDF", book.type)
        assertTrue(book.hasCover)
        assertEquals(HttpStatusCode.OK, cover.status)
        assertTrue(cover.contentType()?.contentType == "image")
    }

    @Test
    fun `only an admin removes a book, and devices learn that it went`() = serverTest { client ->
        val admin = client.setUpAdmin()
        val alice = client.signUp(admin, "alice")
        val book: BookResponse = client.upload(admin, "moby.txt", "Call me Ishmael.".toByteArray()).body()
        val seen = client.changes(alice).cursor

        val refused = client.delete(ApiRoutes.book(book.id)) { bearerAuth(alice.accessToken) }
        val removed = client.delete(ApiRoutes.book(book.id)) { bearerAuth(admin.accessToken) }
        val since = client.changes(alice, since = seen)

        assertEquals(HttpStatusCode.Forbidden, refused.status)
        assertEquals(HttpStatusCode.NoContent, removed.status)
        assertEquals(listOf(book.copy(deleted = true)), since.books)
        assertEquals(
            HttpStatusCode.NotFound,
            client.get(ApiRoutes.bookFile(book.id)) { bearerAuth(alice.accessToken) }.status,
        )
    }

    @Test
    fun `a removed book uploaded again returns as a new change`() = serverTest { client ->
        val admin = client.setUpAdmin()
        val text = "Call me Ishmael.".toByteArray()
        val book: BookResponse = client.upload(admin, "moby.txt", text).body()
        client.delete(ApiRoutes.book(book.id)) { bearerAuth(admin.accessToken) }
        val afterRemoval = client.changes(admin).cursor

        val again = client.upload(admin, "moby.txt", text)
        val since = client.changes(admin, since = afterRemoval)

        assertEquals(HttpStatusCode.Created, again.status)
        assertEquals(listOf(book.id), since.books.map { it.id })
        assertEquals(false, since.books.single().deleted)
        assertEquals(
            HttpStatusCode.OK,
            client.get(ApiRoutes.bookFile(book.id)) { bearerAuth(admin.accessToken) }.status,
        )
    }

    @Test
    fun `an id that is not a book's names no file`() = serverTest { client ->
        val admin = client.setUpAdmin()

        val response = client.get(ApiRoutes.bookFile("..%2F..%2Flukia-server.db")) { bearerAuth(admin.accessToken) }

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `the library needs a signed-in account`() = serverTest { client ->
        assertEquals(HttpStatusCode.Unauthorized, client.get(ApiRoutes.Books).status)
        assertEquals(HttpStatusCode.Unauthorized, client.post("${ApiRoutes.Books}?fileName=a.txt").status)
    }

    private suspend fun HttpClient.upload(by: TokenResponse, fileName: String, bytes: ByteArray): HttpResponse =
        post(ApiRoutes.Books) {
            parameter("fileName", fileName)
            bearerAuth(by.accessToken)
            contentType(ContentType.Application.OctetStream)
            setBody(bytes)
        }

    private suspend fun HttpClient.changes(by: TokenResponse, since: Long = 0): BookPage =
        get("${ApiRoutes.Books}?since=$since") { bearerAuth(by.accessToken) }.body()

    private fun onePagePdf(): ByteArray = PDDocument().use { document ->
        val page = PDPage().also(document::addPage)
        PDPageContentStream(document, page).use { content ->
            content.beginText()
            content.setFont(PDType1Font(Standard14Fonts.FontName.HELVETICA), 24f)
            content.newLineAtOffset(72f, 700f)
            content.showText("Call me Ishmael.")
            content.endText()
        }
        ByteArrayOutputStream().also(document::save).toByteArray()
    }

    private fun sha256(bytes: ByteArray) =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
