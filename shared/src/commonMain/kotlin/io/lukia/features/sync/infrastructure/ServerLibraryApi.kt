package io.lukia.features.sync.infrastructure

import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeoutConfig
import io.ktor.client.plugins.timeout
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.contentLength
import io.ktor.http.isSuccess
import io.ktor.utils.io.readAvailable
import io.lukia.api.ApiRoutes
import io.lukia.api.BookResponse
import io.lukia.api.ChangePage
import io.lukia.core.domain.runCatchingApp
import io.lukia.core.infrastructure.Sha256
import io.lukia.core.infrastructure.toHex
import io.lukia.features.account.domain.AccountFailure
import io.lukia.features.account.domain.AccountProblem
import io.lukia.features.account.infrastructure.ServerHttp

/** The shared library's endpoints, as a device catching up and downloading uses them. */
class ServerLibraryApi(private val http: ServerHttp) {

    suspend fun changes(address: String, token: String, since: Long): ChangePage<BookResponse> =
        http.call { get("$address${ApiRoutes.Books}?since=$since") { bearerAuth(token) } }

    /** The book's cover, or null when the server has none to give. */
    suspend fun cover(address: String, token: String, id: String): ByteArray? =
        runCatchingApp {
            http.send { get(address + ApiRoutes.bookCover(id)) { bearerAuth(token) } }.body<ByteArray>()
        }.getOrNull()

    /**
     * Streams the book with [id] to [write] a buffer at a time, reporting how
     * far it has got, and checks on the way that the bytes are the book asked
     * for - its id is their hash. A download that arrives as other bytes, cut
     * short or tampered with on a plain-HTTP home network, is refused rather
     * than shelved.
     *
     * No limit on the whole request, only on silence: a large PDF over a slow
     * link legitimately takes minutes, and a server that has stopped sending
     * does not.
     */
    suspend fun download(
        address: String,
        token: String,
        id: String,
        write: suspend (ByteArray, Int) -> Unit,
        onProgress: (Float) -> Unit,
    ) {
        val statement = http.client.prepareGet(address + ApiRoutes.bookFile(id)) {
            bearerAuth(token)
            timeout {
                requestTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
                socketTimeoutMillis = SilenceMillis
            }
        }

        val digest = Sha256()
        runCatchingApp { statement.execute { response -> stream(response, digest, write, onProgress) } }
            .exceptionOrNull()
            ?.let { throw (it as? AccountFailure) ?: AccountFailure(AccountProblem.ServerUnreachable) }

        if (digest.digest().toHex() != id) throw AccountFailure(AccountProblem.ServerFailed)
    }

    private suspend fun stream(
        response: HttpResponse,
        digest: Sha256,
        write: suspend (ByteArray, Int) -> Unit,
        onProgress: (Float) -> Unit,
    ) {
        if (!response.status.isSuccess()) throw AccountFailure(http.problemIn(response))

        val total = response.contentLength() ?: 0L
        val body = response.bodyAsChannel()
        val buffer = ByteArray(BufferBytes)
        var received = 0L

        while (true) {
            val read = body.readAvailable(buffer, 0, buffer.size)
            if (read == -1) break
            digest.update(buffer, 0, read)
            write(buffer, read)
            received += read
            if (total > 0) onProgress((received.toFloat() / total).coerceAtMost(1f))
        }
    }

    private companion object {
        const val BufferBytes = 64 * 1024
        const val SilenceMillis = 30_000L
    }
}
