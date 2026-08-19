package io.lukia.features.translate.infrastructure.anthropic

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * The Messages API, as much of it as translating one sentence needs.
 *
 * Hand-written rather than taken from a client library because there is no
 * Anthropic SDK for Kotlin/Native, and the app has to talk to this from iOS as
 * well as from the JVM. What is here is deliberately the whole of what is sent
 * and read: a request with a system prompt and one message, and a response with
 * a reason for stopping and some blocks of content.
 */
@Serializable
internal data class MessagesRequest(
    val model: String,
    @SerialName("max_tokens") val maxTokens: Int,
    val system: String,
    val messages: List<Message>,
    @SerialName("output_config") val outputConfig: OutputConfig,
    /**
     * Always true in practice, for the same reason the answer is worth watching
     * arrive: a model that thinks before it writes says nothing for a while, and
     * a stream makes that silence visible rather than indistinguishable from a
     * request that has stalled.
     */
    val stream: Boolean = false
)

@Serializable
internal data class Message(
    val role: String,
    val content: String
) {
    companion object {
        fun user(content: String) = Message(role = "user", content = content)
    }
}

/**
 * How hard to think, and what shape the answer has to be.
 *
 * The schema is the reason nothing here has to ask for the answer politely: the
 * response is guaranteed to parse, so there is no preamble to strip, no code
 * fence to find and no retry loop for the times a model felt chatty.
 */
@Serializable
internal data class OutputConfig(
    /**
     * Null for a model that does not take one. Translating a sentence is not a
     * reasoning problem, so where this can be set it is set low - the reader is
     * waiting with a finger still on the page.
     *
     * Null is also how it is left out of the request: unset fields are not
     * encoded, which is exactly what a model that rejects this one needs.
     */
    val effort: String? = null,
    /**
     * Passed in rather than defaulted, because a field left at its default is
     * not encoded either - and a request that quietly dropped the schema would
     * come back as prose that fails to parse rather than as an error.
     */
    val format: JsonObject
)

/**
 * One event out of a streamed answer.
 *
 * The stream carries more kinds of event than this reads - a message opening
 * and closing, a content block opening and closing, a ping to keep the
 * connection warm - and all of them arrive as this shape with their unread
 * fields absent. What is read is the two that carry anything: a delta with a
 * piece of the answer on it, and the delta at the end that says why the model
 * stopped.
 */
@Serializable
internal data class MessagesEvent(
    val type: String? = null,
    val delta: EventDelta? = null,
    val error: ErrorBody? = null
)

@Serializable
internal data class EventDelta(
    val type: String? = null,
    val text: String? = null,
    @SerialName("stop_reason") val stopReason: String? = null
) {

    /**
     * The answer, as opposed to the model thinking about it.
     *
     * Thinking is on by default on the models this offers, and it streams too -
     * as deltas of its own type. Reading by type rather than by "whatever has
     * text on it" is what keeps the model's reasoning out of the book if a
     * future event ever carries both under one name.
     */
    val answerText: String? get() = text?.takeIf { type == TEXT_DELTA }

    private companion object {
        const val TEXT_DELTA = "text_delta"
    }
}

/** What comes back when the request itself was not accepted. */
@Serializable
internal data class ErrorResponse(val error: ErrorBody? = null)

@Serializable
internal data class ErrorBody(
    val type: String? = null,
    val message: String? = null
)

/**
 * Both fields are required, and the note is emptied rather than omitted when
 * there is nothing to say. A schema whose fields are all required is the one
 * least likely to be rejected as too loose, and an empty string is as easy to
 * read as an absent key.
 */
internal val TranslationSchema: JsonObject = buildJsonObject {
    put("type", "json_schema")
    putJsonObject("schema") {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("translation") { put("type", "string") }
            putJsonObject("note") { put("type", "string") }
        }
        put(
            "required",
            kotlinx.serialization.json.JsonArray(
                listOf(JsonPrimitive("translation"), JsonPrimitive("note"))
            )
        )
        put("additionalProperties", false)
    }
}
