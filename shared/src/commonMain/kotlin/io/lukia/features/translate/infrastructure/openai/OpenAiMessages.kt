package io.lukia.features.translate.infrastructure.openai

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * OpenAI's chat endpoint, as much of it as translating one sentence needs.
 *
 * The same shapes serve a hosted service and a model running on the reader's
 * own machine: LM Studio, Ollama, llama.cpp's server and vLLM all answer this
 * request, which is why there is one implementation behind two providers.
 */
@Serializable
internal data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    /**
     * What a server that has never heard of anything newer is told, and what
     * every locally hosted one still understands. Null when [maxCompletionTokens]
     * is sent instead - unset fields are not encoded, and sending both is how a
     * request gets refused for contradicting itself.
     */
    @SerialName("max_tokens") val maxTokens: Int? = null,
    /**
     * The same ceiling under the name the hosted service now insists on.
     *
     * `max_tokens` is deprecated there and outright rejected by every model that
     * thinks before it answers - which is now most of what anybody would choose.
     * A reader who types the name of a current model and gets "unsupported
     * parameter" back has been failed by this app rather than by their key, so
     * the hosted service is sent the new name and the servers on people's own
     * machines go on being sent the one they implement.
     */
    @SerialName("max_completion_tokens") val maxCompletionTokens: Int? = null,
    /**
     * Null for a server that does not understand it, which is most of the
     * locally hosted ones. Unset fields are not encoded, so leaving this out is
     * how the request stops asking.
     */
    @SerialName("response_format") val responseFormat: JsonObject? = null,
    /**
     * Always true in practice: a translation is asked for a piece at a time so
     * the reader watches it appear, and so that a model taking its time keeps
     * saying so rather than going silent and being given up on.
     */
    val stream: Boolean = false,
)

@Serializable
internal data class ChatMessage(
    val role: String,
    val content: String,
) {
    companion object {
        fun system(content: String) = ChatMessage(role = "system", content = content)
        fun user(content: String) = ChatMessage(role = "user", content = content)
    }
}

/**
 * One piece of an answer.
 *
 * There is no shape here for an answer that arrives all at once, because none
 * is ever asked for: every request streams, so the text arrives on `delta` a
 * chunk at a time and the reason the model stopped comes on the last of them.
 * Everything else in a chunk - the id, the model, the usage some servers append
 * - says nothing this needs.
 */
@Serializable
internal data class ChatChunk(
    val choices: List<StreamChoice> = emptyList(),
    /**
     * What a server sends when it accepted the request and then could not
     * finish it - a model unloaded mid-answer, a context window overrun that
     * only became apparent once generation started, a proxy losing its upstream.
     *
     * It arrives as an ordinary chunk on a stream that has already answered 200,
     * so a reader of `choices` alone sees a chunk with no text in it and then a
     * stream that simply ends. That is how these used to reach the reader as
     * "that was not a translation", which is true and says nothing about the
     * server having explained itself perfectly clearly.
     */
    val error: ChatErrorBody? = null,
) {
    val text: String? get() = choices.firstOrNull()?.delta?.content

    val finishReason: String? get() = choices.firstOrNull()?.finishReason
}

@Serializable
internal data class StreamChoice(
    val delta: ChatDelta? = null,
    @SerialName("finish_reason") val finishReason: String? = null,
)

@Serializable
internal data class ChatDelta(val content: String? = null)

/** What comes back when the request itself was not accepted. */
@Serializable
internal data class ChatError(val error: ChatErrorBody? = null)

@Serializable
internal data class ChatErrorBody(
    val message: String? = null,
    val type: String? = null,
    val code: String? = null,
) {

    /**
     * Whether this is an account with nothing to spend rather than one asking
     * too often. Both arrive as 429, and they are completely different problems:
     * one is solved by waiting a moment and the other never is.
     */
    val isOutOfCredit: Boolean
        get() = type == OUT_OF_CREDIT || code == OUT_OF_CREDIT

    /**
     * Whether the name in Settings is not a model this server has.
     *
     * The commonest mistake with a local server and with a hosted one alike, and
     * the one that arrives looking most like something else: it is a 404 on the
     * same path a wrong address would be a 404 on. What tells them apart is that
     * the server answered and said which thing it could not find.
     */
    val isUnknownModel: Boolean
        get() = code == UNKNOWN_MODEL ||
            type == UNKNOWN_MODEL ||
            // Servers people run at home have no code for this and say it in
            // prose. Both halves are required, because a rate limit names the
            // model too and is not this.
            message.orEmpty().let { said ->
                said.contains(MODEL, ignoreCase = true) &&
                    MISSING.any { said.contains(it, ignoreCase = true) }
            }

    /**
     * Whether more was sent than the model can hold. Not a passage this app
     * would ever send on its own, but a reader's own server may be running a
     * model with a very short window.
     */
    val isTooLong: Boolean
        get() = code == TOO_LONG || type == TOO_LONG

    private companion object {
        const val OUT_OF_CREDIT = "insufficient_quota"
        const val UNKNOWN_MODEL = "model_not_found"
        const val TOO_LONG = "context_length_exceeded"
        const val MODEL = "model"
        val MISSING = listOf("not found", "does not exist", "unknown", "no such")
    }
}

/**
 * The translation, as a server that can be held to a schema is asked to shape it.
 *
 * Sent only to providers known to enforce it. A local server handed a schema it
 * does not implement answers in prose, and the request is better off not
 * pretending otherwise - which is why the prompt carries its own instructions
 * for those and this is left off.
 */
internal val TranslationResponseFormat: JsonObject = buildJsonObject {
    put("type", "json_schema")
    putJsonObject("json_schema") {
        put("name", "translation")
        put("strict", true)
        putJsonObject("schema") {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("translation") { put("type", "string") }
                putJsonObject("note") { put("type", "string") }
            }
            put(
                "required",
                JsonArray(listOf(JsonPrimitive("translation"), JsonPrimitive("note"))),
            )
            put("additionalProperties", false)
        }
    }
}

/**
 * What a server says it can run, from the catalogue endpoint every
 * OpenAI-compatible server exposes.
 *
 * Worth asking for on a local server in a way it is not on a hosted one: what
 * a hosted service serves is published and stable, while a local one runs
 * whatever the reader happens to have loaded this morning - and its name has to
 * match exactly.
 */
@Serializable
internal data class ModelCatalogueResponse(
    val data: List<ModelEntry> = emptyList(),
)

@Serializable
internal data class ModelEntry(val id: String)
