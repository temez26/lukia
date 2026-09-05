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
 *
 * Returns whether the stream said goodbye rather than merely stopping. That is
 * the difference between an answer and half of one: a connection dropped in the
 * middle of a sentence closes exactly like a finished one, and read without this
 * the half that arrived is indistinguishable from all a model had to say. Each
 * provider decides what counts as goodbye for its own format - see
 * [io.lukia.features.translate.infrastructure.anthropic.AnthropicTranslator] -
 * and this reports the one signal both of them can send.
 */
internal suspend fun HttpResponse.forEachEventPayload(action: (String) -> Unit): Boolean {
    val body = bodyAsChannel()

    while (true) {
        val line = body.readUTF8Line() ?: return false

        if (!line.startsWith(DATA_FIELD)) continue

        val payload = line.removePrefix(DATA_FIELD).trim()

        if (payload.isEmpty()) continue
        if (payload == END_OF_STREAM) return true

        action(payload)
    }
}

private const val DATA_FIELD = "data:"
