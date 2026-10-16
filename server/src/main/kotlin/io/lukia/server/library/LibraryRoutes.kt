package io.lukia.server.library

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.header
import io.ktor.server.request.receiveChannel
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondFile
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.util.getOrFail
import io.lukia.api.ApiRoutes
import io.lukia.server.accounts.TokenService
import io.lukia.server.accounts.account
import io.lukia.server.accounts.requireAdmin
import io.lukia.server.accounts.signedIn
import io.lukia.server.http.ChangePageSize
import io.lukia.server.http.blocking
import io.lukia.server.http.since

fun Route.libraryRoutes(library: LibraryService, files: BookFiles, tokens: TokenService, maxUploadBytes: Long) {
    signedIn(tokens) {
        get(ApiRoutes.Books) {
            val since = call.since
            call.respond(blocking { library.changesSince(since, ChangePageSize) })
        }

        post(ApiRoutes.Books) {
            // Both checked before a byte of the body is read.
            val name = uploadName(call.request.queryParameters["fileName"])
            val declaredLength = call.request.header(HttpHeaders.ContentLength)?.toLongOrNull()
            if (declaredLength != null && declaredLength > maxUploadBytes) throw tooLarge(maxUploadBytes)

            val upload = files.newIncoming()
            try {
                val received = receiveUpload(call.receiveChannel(), upload, maxUploadBytes)
                val added = blocking { library.add(received, name, call.account.id, from = call.account.familyId) }
                call.respond(if (added.isNew) HttpStatusCode.Created else HttpStatusCode.OK, added.book)
            } finally {
                // Gone already when it became a book; this is for every way it did not.
                upload.delete()
            }
        }

        get(ApiRoutes.BookFile) {
            val file = blocking { library.fileOf(call.parameters.getOrFail("id")) }
            call.respondFile(file)
        }

        get(ApiRoutes.BookCover) {
            val bytes = blocking { library.coverOf(call.parameters.getOrFail("id")) }
            call.respondBytes(bytes, imageContentType(bytes))
        }

        delete(ApiRoutes.Book) {
            call.requireAdmin()
            blocking { library.delete(call.parameters.getOrFail("id"), from = call.account.familyId) }
            call.respond(HttpStatusCode.NoContent)
        }
    }
}

/**
 * What a stored cover is, from its first bytes. A cover is kept as the file
 * held it - JPEG once shrunk, otherwise whatever the book carried - so the type
 * is read rather than assumed.
 */
private fun imageContentType(bytes: ByteArray): ContentType = when {
    bytes.startsWith(PngSignature) -> ContentType.Image.PNG
    bytes.startsWith(JpegSignature) -> ContentType.Image.JPEG
    bytes.startsWith(GifSignature) -> ContentType.Image.GIF
    else -> ContentType.Application.OctetStream
}

private fun ByteArray.startsWith(prefix: List<Int>) =
    size >= prefix.size && prefix.indices.all { this[it] == prefix[it].toByte() }

private val PngSignature = listOf(0x89, 0x50, 0x4E, 0x47)
private val JpegSignature = listOf(0xFF, 0xD8)
private val GifSignature = listOf(0x47, 0x49, 0x46)
