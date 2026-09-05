package io.lukia.features.translate.infrastructure.anthropic

import io.ktor.client.HttpClient
import io.ktor.client.request.accept
import io.ktor.client.request.headers
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.lukia.core.domain.AppError
import io.lukia.core.domain.AppErrorException
import io.lukia.core.domain.diagnostics.Diagnostics
import io.lukia.features.translate.domain.Translation
import io.lukia.features.translate.domain.TranslationRequest
import io.lukia.features.translate.domain.Translator
import io.lukia.features.translate.infrastructure.StreamedAnswer
import io.lukia.features.translate.infrastructure.TranslationFailureException
import io.lukia.features.translate.infrastructure.TranslationFailureKind
import io.lukia.features.translate.infrastructure.TranslationSettingsRepository
import io.lukia.features.translate.infrastructure.buildTranslationPrompt
import io.lukia.features.translate.infrastructure.forEachEventPayload
import io.lukia.features.translate.infrastructure.readStreamedPassage
import io.lukia.features.translate.infrastructure.refuseCleartextToThePublicInternet
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

/** The stream saying the model was still writing when its allowance ran out. */
private const val OUT_OF_ROOM = "max_tokens"

/** The event that closes a finished answer, and only a finished one. */
private const val MESSAGE_STOP = "message_stop"

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
    private val diagnostics: Diagnostics = Diagnostics.None,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : Translator {

    override suspend fun translate(
        request: TranslationRequest,
        onPartial: (String) -> Unit,
    ): Translation {
        val configured = settings.settings.value
        val key = configured.apiKey?.takeIf { it.isNotBlank() }
            ?: throw AppErrorException(AppError.TranslationUnavailable)

        // Anthropic's address is not one the settings screen offers to change,
        // so this is a lock on a door nobody has tried - and the check belongs
        // to whoever is about to send the passage rather than to the screen
        // that usually decides where it goes.
        configured.refuseCleartextToThePublicInternet()

        val prompt = buildTranslationPrompt(request)
        val answer = StreamedAnswer(onPartial)

        // Why the model stopped, what the service said went wrong partway
        // through, and whether it ever said goodbye - all three of which a
        // stream sends as events rather than as a status, so they arrive here
        // rather than from the response.
        var stopReason: String? = null
        var reportedFailure: ErrorBody? = null
        var closed = false

        translating(configured, diagnostics) {
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
                if (!response.status.isSuccess()) {
                    throw response.asTranslationFailure(json, configured.model)
                }

                response.forEachEventPayload { payload ->
                    // An event this build cannot read is one carrying something
                    // new. The rest of the answer is still worth having.
                    val event = runCatching { json.decodeFromString<MessagesEvent>(payload) }
                        .getOrNull() ?: return@forEachEventPayload

                    if (event.type == MESSAGE_STOP) closed = true

                    event.error?.let { reportedFailure = it }
                    event.delta?.stopReason?.let { stopReason = it }
                    event.delta?.answerText?.let(answer::append)
                }
            }
        }

        // Anything the stream said about itself before the answer is read out of
        // it: the service giving up partway, and the two stop reasons that end a
        // perfectly good stream with no translation in it.
        val declared = reportedFailure?.asTranslationFailure()
            ?: stopReason.asFailureOrNull(configured.model)

        if (declared != null) throw declared

        // A message that said why it stopped is a finished message, whether or
        // not the event that closes one arrived behind it.
        return readStreamedPassage(
            answer = answer.text,
            json = json,
            endedProperly = closed || stopReason != null,
        )
    }

    /**
     * The two ways a stream ends successfully with nothing worth reading in it.
     *
     * Both are perfectly good HTTP and neither is a translation, and the reader
     * can act on each of them differently - which is the whole reason they are
     * not both left to arrive as an answer that would not parse.
     */
    private fun String?.asFailureOrNull(model: String): TranslationFailureException? =
        when (this) {
            REFUSAL -> TranslationFailureException(
                kind = TranslationFailureKind.Refused,
                explanation = "$model would not translate that passage. Try a different one.",
            )

            // Named as the model's own thinking first, because that is usually
            // what ran out: thinking is charged to the same allowance as the
            // answer and is not shown, so from outside a model that spent it all
            // reasoning looks exactly like one still writing - see
            // tokenAllowanceFor.
            OUT_OF_ROOM -> TranslationFailureException(
                kind = TranslationFailureKind.RanOutOfRoom,
                explanation = "$model stopped before it finished translating. It is charged " +
                    "for thinking as well as for the answer - try a model that thinks less " +
                    "before it answers, or a shorter passage.",
            )

            else -> null
        }
}
