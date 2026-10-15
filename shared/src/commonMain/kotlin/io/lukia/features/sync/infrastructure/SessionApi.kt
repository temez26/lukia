package io.lukia.features.sync.infrastructure

import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.lukia.api.ApiRoutes
import io.lukia.api.ChangePage
import io.lukia.api.SessionRequest
import io.lukia.api.SessionResponse
import io.lukia.features.account.infrastructure.ServerHttp

/** The signed-in account's reading sessions: `/me/sessions`. */
class SessionApi(private val http: ServerHttp) {

    suspend fun send(address: String, token: String, id: String, session: SessionRequest): SessionResponse =
        http.call {
            put(address + ApiRoutes.mySession(id)) {
                bearerAuth(token)
                contentType(ContentType.Application.Json)
                setBody(session)
            }
        }

    suspend fun changes(address: String, token: String, since: Long): ChangePage<SessionResponse> =
        http.call { get("$address${ApiRoutes.MySessions}?since=$since") { bearerAuth(token) } }
}
