package io.lukia.features.translate.infrastructure.anthropic

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
import io.lukia.features.translate.infrastructure.StreamedAnswer
import io.lukia.features.translate.infrastructure.TranslationSettingsRepository
import io.lukia.features.translate.infrastructure.buildTranslationPrompt
import io.lukia.features.translate.infrastructure.forEachEventPayload
import io.lukia.features.translate.infrastructure.readTranslatedPassage
import io.lukia.features.translate.infrastructure.tokenAllowanceFor
import io.lukia.features.translate.infrastructure.translating
import io.lukia.features.translate.infrastructure.translationTimeouts
import kotlinx.serialization.json.Json

/** The version of the wire format this speaks, sent on every request. */
private const val API_VERSION = "2023-06-01"

/**
 * A sentence is not a reasoning problem. Low is where the wait is short enough
 * that a reader keeps their place on the page.
 */
private const val EFFORT = "low"

/** The stream saying the model declined, rather than that it had nothing to say. */
private const val REFUSAL = "refusal"

/**
 * Whether a model takes the effort setting at all.
 *
 * Sending it to one that does not is a rejected request rather than a slower
 * answer. Decided from the name because the model is free text now - the reader
 * can name one this build has never heard of, and the honest default for an
 * unknown name is to ask for what the current models accept.
 */
private fun String.takesEffort(): Boolean = !contains("haiku", ignoreCase = true)

/**
 * Translates through Anthropic's Messages API.
 *
 * Raw HTTP rather than a client library: there is no Anthropic SDK for
 * Kotlin/Native, and this has to run on iOS as well as on the JVM and Android.
 * What that costs is this file; what it buys is one implementation for all
 * three.
 *
 * The settings are read on every call rather than captured once, so a key
 * pasted in while a book is open works on the very next tap - and so does
 * changing the model or the language mid-chapter.
 *
 * The answer is streamed. A hosted service is fast enough that this is not
 * about the waiting so much as about what the waiting looks like: a model
 * thinking about an idiom says nothing for a second or two, and a stream turns
 * that into a sentence being written rather than into a pause that might be a
 * failure. It also means one wait can be told from the other - see
 * [translationTimeouts].
 */
class AnthropicTranslator(
    private val client: HttpClient,
    private val settings: TranslationSettingsRepository,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : Translator {

    override suspend fun translate(
        request: TranslationRequest,
        onPartial: (String) -> Unit,
    ): Translation {
        val configured = settings.settings.value
        val key = configured.apiKey?.takeIf { it.isNotBlank() }
            ?: throw AppErrorException(AppError.TranslationUnavailable)

        val prompt = buildTranslationPrompt(request)
        val answer = StreamedAnswer(onPartial)

        // Why the model stopped, and what the service said went wrong partway
        // through - both of which a stream sends as events rather than as a
        // status, so they arrive here rather than from the response.
        var stopReason: String? = null
        var reportedFailure: String? = null

        translating(configured) {
            client.preparePost("${configured.baseUrl.trimEnd('/')}/messages") {
                translationTimeouts(configured)

                headers {
                    append("x-api-key", key)
                    append("anthropic-version", API_VERSION)
                }
                accept(ContentType.Text.EventStream)
                contentType(ContentType.Application.Json)
                setBody(
                    MessagesRequest(
                        model = configured.model,
                        // A ceiling on the thinking and the reply together, and
                        // thinking is on by default on the models this offers -
                        // see tokenAllowanceFor.
                        maxTokens = tokenAllowanceFor(request.text),
                        system = prompt.system,
                        messages = listOf(Message.user(prompt.user)),
                        stream = true,
                        outputConfig = OutputConfig(
                            effort = EFFORT.takeIf { configured.model.takesEffort() },
                            format = TranslationSchema,
                        ),
                    ),
                )
            }.execute { response ->
                if (!response.status.isSuccess()) throw response.asAppError()

                response.forEachEventPayload { payload ->
                    // An event this build cannot read is one carrying something
                    // new. The rest of the answer is still worth having.
                    val event = runCatching { json.decodeFromString<MessagesEvent>(payload) }
                        .getOrNull() ?: return@forEachEventPayload

                    event.error?.message?.let { reportedFailure = it }
                    event.delta?.stopReason?.let { stopReason = it }
                    event.delta?.answerText?.let(answer::append)
                }
            }
        }

        // A declined request is a perfectly successful stream with nothing in
        // it. Read as content it is a blank translation; read as what it is, it
        // is the one failure the reader can do something about, by asking for a
        // different sentence.
        if (stopReason == REFUSAL) throw AppErrorException(AppError.TranslationFailed)

        reportedFailure?.let { throw AppErrorException(AppError.Unexpected(it)) }

        return readTranslatedPassage(answer.text, json)
    }

    /**
     * What went wrong, in words a reader can act on.
     *
     * A rejected key and a busy service are the same HTTP failure to this code
     * and completely different problems to the person holding the phone - one
     * is a trip to Settings and the other is waiting a moment - so they are told
     * apart here rather than all becoming "something went wrong".
     */
    private suspend fun HttpResponse.asAppError(): AppErrorException {
        val error = when (status) {
            HttpStatusCode.Unauthorized, HttpStatusCode.Forbidden ->
                AppError.Unexpected("That API key was not accepted. Check it in Settings.")

            // The service says which of "too often" and "nothing left to
            // spend" this is far better than a guess from the status code.
            HttpStatusCode.TooManyRequests -> AppError.Unexpected(
                readErrorMessage() ?: "Too many translations at once. Try again in a moment.",
            )

            HttpStatusCode.PayloadTooLarge ->
                AppError.Unexpected("That passage is too long to translate.")

            else -> if (status.value >= 500) {
                AppError.Unexpected("The translation service is unavailable. Try again.")
            } else {
                AppError.Unexpected(readErrorMessage())
            }
        }

        return AppErrorException(error)
    }

    /** The service's own explanation, when it sent one worth repeating. */
    private suspend fun HttpResponse.readErrorMessage(): String? = runCatching {
        json.decodeFromString<ErrorResponse>(bodyAsText()).error?.message
    }.getOrNull()
}
