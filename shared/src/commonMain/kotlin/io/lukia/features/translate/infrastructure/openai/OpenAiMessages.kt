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
    @SerialName("max_tokens") val maxTokens: Int,
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

    private companion object {
        const val OUT_OF_CREDIT = "insufficient_quota"
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
