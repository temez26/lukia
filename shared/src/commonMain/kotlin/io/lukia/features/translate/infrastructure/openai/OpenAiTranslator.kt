package io.lukia.features.translate.infrastructure.openai

import io.ktor.client.HttpClient
import io.ktor.client.request.accept
import io.ktor.client.request.headers
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.lukia.core.domain.AppError
import io.lukia.core.domain.AppErrorException
import io.lukia.features.translate.domain.Translation
import io.lukia.features.translate.domain.TranslationRequest
import io.lukia.features.translate.domain.Translator
import io.lukia.features.translate.domain.provider.TranslationProvider
import io.lukia.features.translate.domain.settings.TranslationSettings
import io.lukia.features.translate.infrastructure.StreamedAnswer
import io.lukia.features.translate.infrastructure.TranslationSettingsRepository
import io.lukia.features.translate.infrastructure.buildTranslationPrompt
import io.lukia.features.translate.infrastructure.forEachEventPayload
import io.lukia.features.translate.infrastructure.readTranslatedPassage
import io.lukia.features.translate.infrastructure.tokenAllowanceFor
import io.lukia.features.translate.infrastructure.translating
import io.lukia.features.translate.infrastructure.translationTimeouts
import kotlinx.serialization.json.Json

/**
 * What a server that cannot be held to a schema is asked for instead. One
 * paragraph, because a local model given a page of formatting rules spends its
 * attention on them rather than on the sentence.
 */
private val SHAPE_INSTRUCTION = """
    Answer with a single JSON object and nothing else - no explanation before or
    after it, and no code fence around it. It has exactly two string fields,
    "translation" and "note", and "note" is an empty string when there is
    nothing worth remarking on.
""".trimIndent().lines().joinToString(" ") {
    it.trim()
}.trim()

/**
 * Translates through anything that speaks OpenAI's chat endpoint.
 *
 * That is a longer list than it sounds: OpenAI itself, LM Studio, Ollama,
 * llama.cpp's server, vLLM and most things that host a model behind an HTTP
 * API. They differ in where they live and whether they want a key, which is
 * configuration - not a second implementation.
 *
 * The one real difference is whether the server can be held to a schema. OpenAI
 * enforces one, so its answer always parses. A model on somebody's laptop may
 * simply ignore the request to, so for those the shape is asked for in the
 * prompt and the answer is read forgivingly. Pretending otherwise would mean
 * every local setup failing on its first sentence.
 *
 * The answer is streamed, which matters most for exactly those local setups: a
 * model on somebody's own machine writes at the speed of their hardware, and
 * asking for the reply piece by piece is what turns that wait into a sentence
 * appearing rather than a blank space that may or may not be broken. It is also
 * what keeps a slow model from being mistaken for a stopped one - see
 * [translationTimeouts].
 */
class OpenAiTranslator(
    private val client: HttpClient,
    private val settings: TranslationSettingsRepository,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : Translator {

    override suspend fun translate(
        request: TranslationRequest,
        onPartial: (String) -> Unit,
    ): Translation {
        val configured = settings.settings.value
        val provider = configured.provider

        if (provider.requiresKey && configured.apiKey.isNullOrBlank()) {
            throw AppErrorException(AppError.TranslationUnavailable)
        }

        // Refused here rather than by the platform, so the reason given is the
        // real one instead of a connection that mysteriously will not open.
        if (!configured.isEndpointAllowed) {
            throw AppErrorException(
                AppError.Unexpected(
                    "Translating over plain HTTP is only allowed to your own network. " +
                        "Use https, or an address on this machine.",
                ),
            )
        }

        val enforcesSchema = provider == TranslationProvider.OpenAi
        val prompt = buildTranslationPrompt(request)
        val answer = StreamedAnswer(onPartial)

        // Why the model stopped, from whichever chunk carried it.
        var finishReason: String? = null

        translating(configured) {
            client.preparePost("${configured.baseUrl.trimEnd('/')}/chat/completions") {
                translationTimeouts(configured)

                configured.apiKey?.takeIf { it.isNotBlank() }?.let { key ->
                    headers { append("Authorization", "Bearer $key") }
                }
                accept(ContentType.Text.EventStream)
                contentType(ContentType.Application.Json)
                setBody(
                    ChatRequest(
                        model = configured.model,
                        // Sized to the passage, and covering the model's own
                        // thinking as well as the answer - see tokenAllowanceFor.
                        maxTokens = tokenAllowanceFor(request.text),
                        responseFormat = TranslationResponseFormat.takeIf { enforcesSchema },
                        stream = true,
                        messages = listOf(
                            ChatMessage.system(
                                if (enforcesSchema) prompt.system else prompt.system.withShape(),
                            ),
                            ChatMessage.user(prompt.user),
                        ),
                    ),
                )
            }.execute { response ->
                if (!response.status.isSuccess()) throw response.asAppError(configured)

                response.forEachEventPayload { payload ->
                    // A chunk this build cannot read is a chunk carrying
                    // something new, not a failed translation: servers append
                    // fields, and one unreadable piece of the answer is not
                    // worth throwing the rest of it away for.
                    val chunk = runCatching { json.decodeFromString<ChatChunk>(payload) }
                        .getOrNull() ?: return@forEachEventPayload

                    chunk.finishReason?.let { finishReason = it }
                    chunk.text?.let(answer::append)
                }
            }
        }

        // A model cut off mid-answer produces JSON that will not parse, and the
        // reason is worth giving rather than leaving as a parse failure.
        //
        // The passage is named last rather than first, because it is usually not
        // what ran out of room: a model that thinks before it answers is charged
        // for the thinking too, and it is perfectly capable of spending the whole
        // allowance without writing a word - see tokenAllowanceFor.
        if (finishReason == "length") {
            throw AppErrorException(
                AppError.Unexpected(
                    "${configured.model} stopped before it finished translating. It is " +
                        "charged for thinking as well as for the answer - try a model that " +
                        "thinks less before it answers, or a shorter passage.",
                ),
            )
        }

        return readTranslatedPassage(answer.text, json)
    }

    /**
     * The shape asked for in words, for a server that cannot be held to a
     * schema. Kept out of the shared prompt builder because it is not something
     * a translator needs to be told - it is something this particular kind of
     * server needs to be asked.
     */
    private fun String.withShape(): String = this + "\n\n" + SHAPE_INSTRUCTION

    private suspend fun HttpResponse.asAppError(
        configured: TranslationSettings,
    ): AppErrorException {
        val error = when (status) {
            HttpStatusCode.Unauthorized, HttpStatusCode.Forbidden ->
                AppError.Unexpected("That API key was not accepted. Check it in Settings.")

            HttpStatusCode.NotFound -> AppError.Unexpected(
                "Nothing answered at ${configured.baseUrl}. Check the address in Settings - " +
                    "it should end in /v1.",
            )

            // Being out of credit and asking too often arrive as the same
            // status and are completely different problems: one is solved by
            // waiting a moment and the other never is. On a first attempt it is
            // nearly always the account, so telling them apart here is what
            // stops somebody waiting for a rate limit that was never the
            // reason.
            HttpStatusCode.TooManyRequests -> readError().let { reported ->
                when {
                    reported?.isOutOfCredit == true -> AppError.Unexpected(
                        "Your ${configured.provider.label} account is out of API credit. " +
                            "API billing is separate from any subscription - add credit in " +
                            "your account's billing settings.",
                    )

                    reported?.message != null -> AppError.Unexpected(reported.message)

                    else -> AppError.Unexpected(
                        "Too many translations at once. Try again in a moment.",
                    )
                }
            }

            else -> if (status.value >= 500) {
                AppError.Unexpected("The translation service is unavailable. Try again.")
            } else {
                // A server that does not know the model is the usual 400 here,
                // and it says so far better than this code could guess.
                AppError.Unexpected(readError()?.message)
            }
        }

        return AppErrorException(error)
    }

    /** The service's own account of what went wrong, when it sent one. */
    private suspend fun HttpResponse.readError(): ChatErrorBody? = runCatching {
        json.decodeFromString<ChatError>(bodyAsText()).error
    }.getOrNull()
}
