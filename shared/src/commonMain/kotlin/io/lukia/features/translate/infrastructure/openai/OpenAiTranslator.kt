package io.lukia.features.translate.infrastructure.openai

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.lukia.core.domain.AppError
import io.lukia.core.domain.AppErrorException
import io.lukia.features.translate.domain.TranslationProvider
import io.lukia.features.translate.domain.TranslationRequest
import io.lukia.features.translate.domain.TranslationSettings
import io.lukia.features.translate.domain.Translation
import io.lukia.features.translate.domain.Translator
import io.lukia.features.translate.infrastructure.TranslationSettingsRepository
import io.lukia.features.translate.infrastructure.buildTranslationPrompt
import io.lukia.features.translate.infrastructure.readTranslatedPassage
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json

private const val MAX_TOKENS = 1024

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
""".trimIndent().lines().joinToString(" ") { it.trim() }.trim()

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
 */
class OpenAiTranslator(
    private val client: HttpClient,
    private val settings: TranslationSettingsRepository,
    private val json: Json = Json { ignoreUnknownKeys = true }
) : Translator {

    override suspend fun translate(request: TranslationRequest): Translation {
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
                        "Use https, or an address on this machine."
                )
            )
        }

        val enforcesSchema = provider == TranslationProvider.OpenAi
        val prompt = buildTranslationPrompt(request)

        val response = try {
            client.post("${configured.baseUrl.trimEnd('/')}/chat/completions") {
                configured.apiKey?.takeIf { it.isNotBlank() }?.let { key ->
                    headers { append("Authorization", "Bearer $key") }
                }
                contentType(ContentType.Application.Json)
                setBody(
                    ChatRequest(
                        model = configured.model,
                        maxTokens = MAX_TOKENS,
                        responseFormat = TranslationResponseFormat.takeIf { enforcesSchema },
                        messages = listOf(
                            ChatMessage.system(
                                if (enforcesSchema) prompt.system else prompt.system.withShape()
                            ),
                            ChatMessage.user(prompt.user)
                        )
                    )
                )
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Throwable) {
            // A server that is not running is the single most common state of a
            // local one, and "connection refused" is not an answer somebody can
            // act on without being told where the app was looking.
            throw AppErrorException(AppError.Unexpected(configured.unreachable()))
        }

        if (!response.status.isSuccess()) throw response.asAppError(configured)

        val answer = response.body<ChatResponse>()

        // A model cut off mid-answer produces JSON that will not parse, and the
        // reason is worth giving rather than leaving as a parse failure.
        if (answer.finishReason == "length") {
            throw AppErrorException(
                AppError.Unexpected("The translation was cut short. Try a shorter passage.")
            )
        }

        val text = answer.text ?: throw AppErrorException(AppError.TranslationFailed)

        return readTranslatedPassage(text, json)
    }

    /**
     * The shape asked for in words, for a server that cannot be held to a
     * schema. Kept out of the shared prompt builder because it is not something
     * a translator needs to be told - it is something this particular kind of
     * server needs to be asked.
     */
    private fun String.withShape(): String = this + "\n\n" + SHAPE_INSTRUCTION

    private fun TranslationSettings.unreachable(): String = when (provider) {
        TranslationProvider.Local ->
            "Could not reach $baseUrl. Check the server is running and that the " +
                "address is reachable from this device."

        else -> "Could not reach the translation service. Check your connection."
    }

    private suspend fun HttpResponse.asAppError(
        configured: TranslationSettings
    ): AppErrorException {
        val error = when (status) {
            HttpStatusCode.Unauthorized, HttpStatusCode.Forbidden ->
                AppError.Unexpected("That API key was not accepted. Check it in Settings.")

            HttpStatusCode.NotFound -> AppError.Unexpected(
                "Nothing answered at ${configured.baseUrl}. Check the address in Settings - " +
                    "it should end in /v1."
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
                            "your account's billing settings."
                    )

                    reported?.message != null -> AppError.Unexpected(reported.message)

                    else -> AppError.Unexpected(
                        "Too many translations at once. Try again in a moment."
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
