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
 * Only `data:` is looked at. The `event:` line names what the payload already
 * says about itself, blank lines separate records, and a line beginning with a
 * colon is a keep-warm comment. None carries any of the answer - though that
 * last one matters anyway, because arriving at all tells the socket timeout that
 * a model still thinking has not gone quiet.
 *
 * Returns whether the stream said goodbye rather than merely stopping, which is
 * the difference between an answer and half of one: a connection dropped
 * mid-sentence closes exactly like a finished one. Each provider decides what
 * counts as goodbye for its own format - see
 * [io.lukia.features.translate.infrastructure.anthropic.AnthropicTranslator] -
 * and this reports the one signal both can send.
 */
internal suspend fun HttpResponse.forEachEventPayload(action: (String) -> Unit): Boolean {
    val body = bodyAsChannel()

    // Set only by the stream saying goodbye, which is the whole point of the
    // return value: running out of lines instead is a connection that dropped in
    // the middle of an answer. Nothing is read after the goodbye - on a stream
    // the server has not closed yet, that read would wait for a line that is
    // never coming.
    var finished = false

    while (!finished) {
        val line = body.readUTF8Line() ?: break

        when (val payload = line.eventPayload()) {
            null -> Unit
            END_OF_STREAM -> finished = true
            else -> action(payload)
        }
    }

    return finished
}

/** What a line carries, or null for one of the many that carry nothing. */
private fun String.eventPayload(): String? =
    takeIf { it.startsWith(DATA_FIELD) }
        ?.removePrefix(DATA_FIELD)
        ?.trim()
        ?.takeIf { it.isNotEmpty() }

private const val DATA_FIELD = "data:"
