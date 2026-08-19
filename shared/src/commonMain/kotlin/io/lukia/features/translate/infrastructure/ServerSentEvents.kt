package io.lukia.features.translate.infrastructure

import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.readUTF8Line

/** What a stream sends in place of a payload when it has nothing left to say. */
private const val END_OF_STREAM = "[DONE]"

/**
 * Every payload a streamed response carries, in the order it sends them.
 *
 * Both providers stream the same way - a text/event-stream of `data:` lines with
 * a JSON object on each - so the reading of it is here rather than twice, and
 * what each of them makes of those objects stays in its own package.
 *
 * Only `data:` is looked at. The `event:` line beside it names the same thing
 * the payload already says about itself, the blank lines separate records, and
 * a line beginning with a colon is a comment a server sends to keep the
 * connection warm. None of those carry any of the answer - though the last of
 * them matters anyway, because arriving at all is what tells the socket timeout
 * that a model still thinking has not gone quiet.
 */
internal suspend fun HttpResponse.forEachEventPayload(action: (String) -> Unit) {
    val body = bodyAsChannel()

    while (true) {
        val line = body.readUTF8Line() ?: return

        if (!line.startsWith("data:")) continue

        val payload = line.removePrefix("data:").trim()

        if (payload.isEmpty()) continue
        if (payload == END_OF_STREAM) return

        action(payload)
    }
}
