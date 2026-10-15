package io.lukia.server.http

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.request.contentLength
import io.ktor.server.request.contentType
import io.lukia.api.ApiErrorCodes

/**
 * Turns away a JSON body before it is read unless it states a length, and a
 * small one. Several JSON endpoints take requests from anyone, and without this
 * a body of any size would be read into memory before a word of it was checked.
 * Book uploads are not JSON and keep their own, larger limit.
 */
val JsonBodyLimit = createApplicationPlugin("JsonBodyLimit") {
    onCall { call ->
        if (!call.request.contentType().match(ContentType.Application.Json)) return@onCall

        val length = call.request.contentLength()
            ?: throw ApiException(HttpStatusCode.LengthRequired, ApiErrorCodes.BadRequest, "State the body's length.")
        if (length > MaxJsonBodyBytes) {
            throw ApiException(HttpStatusCode.PayloadTooLarge, ApiErrorCodes.TooLarge, "The request is too large.")
        }
    }
}

/** Far more than any request the API takes - the longest is a password and a title or two. */
private const val MaxJsonBodyBytes = 64L * 1024
