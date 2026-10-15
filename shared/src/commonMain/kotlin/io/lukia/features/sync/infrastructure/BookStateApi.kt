package io.lukia.features.sync.infrastructure

import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.lukia.api.ApiRoutes
import io.lukia.api.BookStateRequest
import io.lukia.api.BookStateResponse
import io.lukia.api.ChangePage
import io.lukia.features.account.infrastructure.ServerHttp

/** The signed-in account's own state per book: `/me/books`. */
class BookStateApi(private val http: ServerHttp) {

    suspend fun send(address: String, token: String, bookId: String, change: BookStateRequest): BookStateResponse =
        http.call {
            put(address + ApiRoutes.myBook(bookId)) {
                bearerAuth(token)
                contentType(ContentType.Application.Json)
                setBody(change)
            }
        }

    suspend fun changes(address: String, token: String, since: Long): ChangePage<BookStateResponse> =
        http.call { get("$address${ApiRoutes.MyBooks}?since=$since") { bearerAuth(token) } }
}
