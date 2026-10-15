package io.lukia.server.library

import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
import io.lukia.api.ApiErrorCodes
import io.lukia.core.domain.document.DocumentType
import io.lukia.server.http.ApiException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/** An upload written to disk: its content hash, which is the book's id, and its length. */
class ReceivedUpload(val file: File, val id: String, val sizeBytes: Long)

/**
 * Streams a request body to [target], hashing as it goes, so a book of any
 * size costs one buffer of memory and one pass over its bytes.
 *
 * Gives up as soon as the body passes [maxBytes], rather than after storing it
 * - a client that leaves out its length, or states a false one, is still held
 * to the limit.
 */
suspend fun receiveUpload(body: ByteReadChannel, target: File, maxBytes: Long): ReceivedUpload =
    withContext(Dispatchers.IO) {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(BufferBytes)
        var total = 0L

        target.outputStream().use { output ->
            while (true) {
                val read = body.readAvailable(buffer, 0, buffer.size)
                if (read == -1) break

                total += read
                if (total > maxBytes) throw tooLarge(maxBytes)

                digest.update(buffer, 0, read)
                output.write(buffer, 0, read)
            }
        }

        ReceivedUpload(target, digest.digest().joinToString("") { "%02x".format(it) }, total)
    }

fun tooLarge(maxBytes: Long) = ApiException(
    HttpStatusCode.PayloadTooLarge,
    ApiErrorCodes.TooLarge,
    "A book may be at most ${maxBytes / BytesPerMegabyte} MB.",
)

/** An upload's file name, checked, and the document type it says the file is. */
class UploadName(val fileName: String, val type: DocumentType)

/**
 * Checks the name an upload is given, refusing it unless it names a type the
 * app reads - the same rule the app's own import applies, by extension.
 */
fun uploadName(fileName: String?): UploadName {
    val name = fileName?.trim().orEmpty()
    if (name.isEmpty() || name.length > MaxFileNameLength || !name.all(::isFileNameCharacter)) {
        throw ApiException(HttpStatusCode.BadRequest, ApiErrorCodes.BadRequest, "Name the file with ?fileName=.")
    }

    val type = DocumentType.fromExtension(name.substringAfterLast('.', ""))
    if (type == DocumentType.UNKNOWN) {
        throw ApiException(
            HttpStatusCode.UnsupportedMediaType,
            ApiErrorCodes.UnsupportedFormat,
            "Lukia reads ${DocumentType.supportedExtensions.sorted().joinToString()} files.",
        )
    }
    return UploadName(name, type)
}

/** No path separators, so a name can never be read as a path; no control characters, so a log line stays one line. */
private fun isFileNameCharacter(character: Char) = character != '/' && character != '\\' && !character.isISOControl()

private const val BufferBytes = 64 * 1024
private const val BytesPerMegabyte = 1024 * 1024
private const val MaxFileNameLength = 255
